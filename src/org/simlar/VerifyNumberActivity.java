/**
 * Copyright (C) 2013 The Simlar Authors.
 *
 * This file is part of Simlar. (http://www.simlar.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.simlar;

import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

public final class VerifyNumberActivity extends Activity
{
	static final String LOGTAG = VerifyNumberActivity.class.getSimpleName();
	private static final int RESULT_CREATE_ACCOUNT_ACTIVITY = 0;

	ProgressDialog mProgressDialog = null;
	private Spinner mSpinner;
	private EditText mEditNumber;

	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCall();

	private final class SimlarServiceCommunicatorCall extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorCall()
		{
			super(LOGTAG);
		}

		@Override
		void onServiceFinishes()
		{
			Log.i(LOGTAG, "onServiceFinishes");

			mProgressDialog.dismiss();

			// prevent switch to MainActivity but finish
			VerifyNumberActivity.this.moveTaskToBack(true);
			VerifyNumberActivity.this.finish();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		Log.i(LOGTAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_verify_number);

		final Integer regionCode = Integer.valueOf(SimlarNumber.readRegionCodeFromSimCardOrConfiguration(this));
		final String number = SimlarNumber.readLocalPhoneNumberFromSimCard(this);

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setMessage(getString(R.string.progress_finishing));
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);

		//Country Code Selector
		final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			adapter.addAll(SimlarNumber.getSupportedCountryCodes());
		} else {
			for (final Integer countryCode : SimlarNumber.getSupportedCountryCodes()) {
				adapter.add(countryCode);
			}
		}
		adapter.sort(new Comparator<Integer>() {
			@Override
			public int compare(final Integer lhs, final Integer rhs)
			{
				return lhs.compareTo(rhs);
			}
		});

		mSpinner = (Spinner) findViewById(R.id.spinnerCountryCodes);
		mSpinner.setAdapter(adapter);

		Log.i(LOGTAG, "proposing country code: " + regionCode);
		if (regionCode.intValue() > 0) {
			mSpinner.setSelection(adapter.getPosition(regionCode));
		}

		// telephone number
		mEditNumber = (EditText) findViewById(R.id.editTextPhoneNumber);
		if (!Util.isNullOrEmpty(number)) {
			mEditNumber.setText(number);
		} else {
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run()
				{
					showSoftInputForEditNumber();
				}
			}, 100);
		}
	}

	void showSoftInputForEditNumber()
	{
		Log.e(LOGTAG, "no number");
		mEditNumber.requestFocus();
		if (((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
				.showSoftInput(mEditNumber, InputMethodManager.SHOW_IMPLICIT))
		{
			Log.w(LOGTAG, "showSoftInput success");
		} else {
			Log.w(LOGTAG, "showSoftInput failed");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();

		mCommunicator.register(this, VerifyNumberActivity.class);
		if (PreferencesHelper.getCreateAccountStatus() == CreateAccountStatus.WAITING_FOR_SMS) {
			Log.i(LOGTAG, "CreateAccountStatus = WAITING FOR SMS");
			startActivityForResult(new Intent(this, CreateAccountActivity.class), RESULT_CREATE_ACCOUNT_ACTIVITY);
		}
	}

	@Override
	protected void onPause()
	{
		Log.i(LOGTAG, "onPause");
		mCommunicator.unregister(this);
		super.onPause();
	}

	@SuppressWarnings("unused")
	public void createAccount(final View view)
	{
		final Integer countryCallingCode = (Integer) mSpinner.getSelectedItem();
		if (countryCallingCode == null) {
			Log.e(LOGTAG, "createAccount no country code => aborting");
			return;
		}
		SimlarNumber.setDefaultRegion(countryCallingCode.intValue());

		final String number = mEditNumber.getText().toString();
		if (Util.isNullOrEmpty(number)) {
			(new AlertDialog.Builder(this))
					.setTitle(R.string.verify_number_activity_alert_no_telephone_number_title)
					.setMessage(R.string.verify_number_activity_alert_no_telephone_number_message)
					.create().show();
			return;
		}

		final String telephoneNumber = "+" + countryCallingCode + number;
		final Intent intent = new Intent(this, CreateAccountActivity.class);
		intent.putExtra(CreateAccountActivity.INTENT_EXTRA_NUMBER, telephoneNumber);
		startActivityForResult(intent, RESULT_CREATE_ACCOUNT_ACTIVITY);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		Log.i(LOGTAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);
		if (requestCode == RESULT_CREATE_ACCOUNT_ACTIVITY) {
			if (resultCode == RESULT_OK) {
				Log.i(LOGTAG, "finishing on CreateAccount request");
				finish();
			}
		}
	}

	@SuppressWarnings("unused")
	public void cancelAccountCreation(final View view)
	{
		mProgressDialog.setMessage(getString(R.string.progress_finishing));
		mProgressDialog.show();
		mCommunicator.getService().terminate();
	}

	@Override
	public void onBackPressed()
	{
		// prevent switch to MainActivity
		moveTaskToBack(true);
	}
}

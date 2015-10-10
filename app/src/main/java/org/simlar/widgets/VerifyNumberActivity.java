/**
 * Copyright (C) 2013 The Simlar Authors.
 *
 * This file is part of Simlar. (https://www.simlar.org)
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

package org.simlar.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.simlar.R;
import org.simlar.helper.CreateAccountStatus;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.SimlarNumber;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.util.Comparator;

public final class VerifyNumberActivity extends AppCompatActivity
{
	private static final int RESULT_CREATE_ACCOUNT_ACTIVITY = 0;

	private Spinner mSpinner;
	private EditText mEditNumber;
	private Button mButtonAccept;

	private final class EditNumberTextWatcher implements TextWatcher
	{
		public EditNumberTextWatcher()
		{
			super();
		}

		@Override
		public void onTextChanged(final CharSequence sequence, final int start, final int before, final int count)
		{
			VerifyNumberActivity.this.updateButtonAccept();
		}

		@Override
		public void beforeTextChanged(final CharSequence sequence, final int start, final int count, final int after)
		{
		}

		@Override
		public void afterTextChanged(final Editable editable)
		{
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Lg.i("onCreate");
		setContentView(R.layout.activity_verify_number);

		final int regionCode = SimlarNumber.readRegionCodeFromSimCardOrConfiguration(this);
		final String number = SimlarNumber.readLocalPhoneNumberFromSimCard(this);

		final ArrayAdapter<Integer> adapter = createCountryCodeSelector();
		mSpinner = (Spinner) findViewById(R.id.spinnerCountryCodes);
		mSpinner.setAdapter(adapter);

		Lg.i("proposing country code: ", regionCode);
		if (regionCode > 0) {
			mSpinner.setSelection(adapter.getPosition(regionCode));
		}

		// telephone number
		mEditNumber = (EditText) findViewById(R.id.editTextPhoneNumber);
		if (!Util.isNullOrEmpty(number)) {
			mEditNumber.setText(number);

			final TextView text = (TextView) findViewById(R.id.textViewCheckOrVerifyYourNumber);
			text.setText(getString(R.string.verify_number_activity_verify_your_number));
		} else {
			new Handler().postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					showSoftInputForEditNumber();
				}
			}, 100);
		}
		mEditNumber.addTextChangedListener(new EditNumberTextWatcher());

		mButtonAccept = (Button) findViewById(R.id.buttonRegister);
		updateButtonAccept();
	}

	private ArrayAdapter<Integer> createCountryCodeSelector()
	{
		final ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			adapter.addAll(SimlarNumber.getSupportedCountryCodes());
		} else {
			for (final Integer countryCode : SimlarNumber.getSupportedCountryCodes()) {
				adapter.add(countryCode);
			}
		}
		adapter.sort(new Comparator<Integer>()
		{
			@Override
			public int compare(final Integer lhs, final Integer rhs)
			{
				return lhs.compareTo(rhs);
			}
		});

		return adapter;
	}

	private void showSoftInputForEditNumber()
	{
		Lg.e("no number");
		mEditNumber.requestFocus();
		if (((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
				.showSoftInput(mEditNumber, InputMethodManager.SHOW_IMPLICIT))
		{
			Lg.w("showSoftInput success");
		} else {
			Lg.w("showSoftInput failed");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Lg.i("onStart");

		if (PreferencesHelper.getCreateAccountStatus() == CreateAccountStatus.WAITING_FOR_SMS) {
			Lg.i("CreateAccountStatus = WAITING FOR SMS");
			startActivityForResult(new Intent(this, CreateAccountActivity.class), RESULT_CREATE_ACCOUNT_ACTIVITY);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Lg.i("onResume");
	}

	@Override
	protected void onPause()
	{
		Lg.i("onPause");
		super.onPause();
	}

	private void updateButtonAccept()
	{
		final boolean enabled = !Util.isNullOrEmpty(mEditNumber.getText().toString());
		Lg.i("updateButtonAccept enabled=", enabled);
		mButtonAccept.setEnabled(enabled);
	}

	@SuppressWarnings("unused")
	public void createAccount(final View view)
	{
		final Integer countryCallingCode = (Integer) mSpinner.getSelectedItem();
		if (countryCallingCode == null) {
			Lg.e("createAccount no country code => aborting");
			return;
		}
		SimlarNumber.setDefaultRegion(countryCallingCode.intValue());

		final String number = mEditNumber.getText().toString();
		if (Util.isNullOrEmpty(number)) {
			Lg.e("createAccount no number => aborting");
			return;
		}

		// check telephoneNumbers plausibility
		final SimlarNumber simlarNumber = new SimlarNumber(number);
		if (!simlarNumber.isValid()) {
			(new AlertDialog.Builder(this))
					.setMessage(R.string.verify_number_activity_alert_wrong_number_text)
					.create().show();
			return;
		}

		final Intent intent = new Intent(this, CreateAccountActivity.class);
		intent.putExtra(CreateAccountActivity.INTENT_EXTRA_NUMBER, simlarNumber.getTelephoneNumber());
		startActivityForResult(intent, RESULT_CREATE_ACCOUNT_ACTIVITY);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		Lg.i("onActivityResult requestCode=", requestCode, " resultCode=", resultCode);
		if (requestCode == RESULT_CREATE_ACCOUNT_ACTIVITY) {
			if (resultCode == RESULT_OK) {
				Lg.i("finishing on CreateAccount request");
				finish();
				startActivity(new Intent(this, MainActivity.class));
			}
		}
	}
}

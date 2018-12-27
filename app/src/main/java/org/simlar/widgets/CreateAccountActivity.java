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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.simlar.R;
import org.simlar.helper.CreateAccountStatus;
import org.simlar.helper.FlavourHelper;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.SimlarNumber;
import org.simlar.https.CreateAccount;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarServiceCommunicator;
import org.simlar.service.SimlarStatus;
import org.simlar.utils.Util;

import java.util.Calendar;
import java.util.Date;

public final class CreateAccountActivity extends Activity
{
	public static final String INTENT_EXTRA_NUMBER = "CreateAccountActivityTelephoneNumber";

	private View mLayoutProgress = null;
	private ProgressBar mProgressRequest = null;
	private ProgressBar mProgressWaitingForSMS = null;
	private ProgressBar mProgressConfirm = null;
	private ProgressBar mProgressFirstLogIn = null;
	private TextView mWaitingForSmsText = null;
	private View mLayoutMessage = null;
	private EditText mEditRegistrationCode = null;
	private TextView mDetails = null;
	private Button mButtonConfirm = null;
	private Button mButtonCall = null;

	private final Handler mHandler = new Handler();

	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCreateAccount();
	private String mTelephoneNumber = "";

	private final class SimlarServiceCommunicatorCreateAccount extends SimlarServiceCommunicator
	{
		private boolean mTestRegistrationSuccess = false;

		@Override
		public void onSimlarStatusChanged()
		{
			final SimlarStatus status = getService().getSimlarStatus();
			Lg.i("onSimlarStatusChanged: ", status);

			if (status.isConnectedToSipServer() || status.isRegistrationAtSipServerFailed()) {
				mTestRegistrationSuccess = status.isConnectedToSipServer();
				if (FlavourHelper.isGcmEnabled()) {
					getService().terminate();
				} else {
					unregister();
					handleRegistrationResult();
				}
			}
		}

		@Override
		public void onServiceFinishes()
		{
			Lg.i("onServiceFinishes");
			handleRegistrationResult();
		}

		private void handleRegistrationResult()
		{
			mProgressFirstLogIn.setVisibility(View.INVISIBLE);

			if (mTestRegistrationSuccess) {
				setResult(RESULT_OK);
				finish();
			} else {
				showMessage(R.string.create_account_activity_error_sip_not_possible);
			}
		}
	}

	private final class EditRegistrationCodeListener implements TextWatcher
	{
		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count)
		{
			if (s.length() == 6) {
				mButtonConfirm.setEnabled(true);
			} else {
				mButtonConfirm.setEnabled(false);
			}
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after)
		{
		}

		@Override
		public void afterTextChanged(final Editable s)
		{
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Lg.i("onCreate");

		setContentView(R.layout.activity_create_account);

		setFinishOnTouchOutside(false);

		mLayoutProgress = findViewById(R.id.linearLayoutProgress);
		mProgressRequest = findViewById(R.id.progressBarRequest);
		mProgressWaitingForSMS = findViewById(R.id.progressBarWaitingForSMS);
		mProgressConfirm = findViewById(R.id.progressBarConfirm);
		mProgressFirstLogIn = findViewById(R.id.progressBarFirstLogIn);

		mProgressRequest.setVisibility(View.INVISIBLE);
		mProgressWaitingForSMS.setVisibility(View.INVISIBLE);
		mProgressConfirm.setVisibility(View.INVISIBLE);
		mProgressFirstLogIn.setVisibility(View.INVISIBLE);
		mWaitingForSmsText = findViewById(R.id.textViewWaitingForSMS);

		mLayoutMessage = findViewById(R.id.layoutMessage);
		mLayoutMessage.setVisibility(View.GONE);
		mEditRegistrationCode = findViewById(R.id.editTextRegistrationCode);
		mDetails = findViewById(R.id.textViewDetails);
		mButtonConfirm = findViewById(R.id.buttonConfirm);
		mButtonCall = findViewById(R.id.buttonCall);

		mEditRegistrationCode.addTextChangedListener(new EditRegistrationCodeListener());

		if (PreferencesHelper.getCreateAccountStatus() == CreateAccountStatus.WAITING_FOR_SMS) {
			mTelephoneNumber = PreferencesHelper.getVerifiedTelephoneNumber();
			showEnterRegistrationCode();
		} else {
			mTelephoneNumber = getIntent().getStringExtra(INTENT_EXTRA_NUMBER);
			getIntent().removeExtra(INTENT_EXTRA_NUMBER);
			createAccountRequest();
		}
	}

	/// Workaround to close soft keyboard on older android versions
	@Override
	public boolean onKeyUp(final int keyCode, @NonNull final KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_ENTER) {
			((InputMethodManager) Util.getSystemService(this, Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mEditRegistrationCode.getWindowToken(), 0);
			return true;
		}
		return false;
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

		if (mProgressFirstLogIn.getVisibility() == View.VISIBLE) {
			mCommunicator.register(this, VerifyNumberActivity.class);
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

	@Override
	protected void onStop()
	{
		Lg.i("onStop");

		if (mProgressFirstLogIn.getVisibility() == View.VISIBLE) {
			mCommunicator.unregister();
		}

		super.onStop();
	}

	@SuppressLint("StaticFieldLeak")
	private void createAccountRequest()
	{
		if (Util.isNullOrEmpty(mTelephoneNumber)) {
			Lg.e("createAccountRequest without telephone number");
			return;
		}

		mProgressRequest.setVisibility(View.VISIBLE);
		Lg.i("createAccountRequest: ", new Lg.Anonymizer(mTelephoneNumber));
		final String smsText = getString(R.string.create_account_activity_sms_text);
		final String expectedSimlarId = SimlarNumber.createSimlarId(mTelephoneNumber);
		final String telephoneNumber = mTelephoneNumber;

		new AsyncTask<String, Void, CreateAccount.RequestResult>()
		{

			@Override
			protected CreateAccount.RequestResult doInBackground(final String... params)
			{
				return CreateAccount.httpPostRequest(params[0], params[1]);
			}

			@Override
			protected void onPostExecute(final CreateAccount.RequestResult result)
			{
				mProgressRequest.setVisibility(View.INVISIBLE);

				if (result.isError()) {
					showMessage(result.getErrorMessage());
					return;
				}

				if (!result.getSimlarId().equals(expectedSimlarId)) {
					Lg.e("received simlarId not equal to expected: telephoneNumber=", new Lg.Anonymizer(telephoneNumber),
							" expected=", new Lg.Anonymizer(expectedSimlarId),
							" actual=", new Lg.Anonymizer(result.getSimlarId()));
				}

				PreferencesHelper.init(result.getSimlarId(), result.getPassword(), Calendar.getInstance().getTime().getTime());
				PreferencesHelper.saveToFilePreferences(CreateAccountActivity.this);
				PreferencesHelper.saveToFileCreateAccountStatus(CreateAccountActivity.this, CreateAccountStatus.WAITING_FOR_SMS, telephoneNumber);

				showEnterRegistrationCode();
			}

		}.execute(mTelephoneNumber, smsText);
	}

	private void showEnterRegistrationCode()
	{
		Lg.i("showEnterRegistrationCode");

		mProgressWaitingForSMS.setVisibility(View.INVISIBLE);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms);

		showMessage(R.string.create_account_activity_error_sms_not_granted_or_timeout);
	}

	@SuppressLint("StaticFieldLeak")
	private void confirmRegistrationCode(final String registrationCode)
	{
		Lg.i("confirmRegistrationCode: ", registrationCode);

		mProgressConfirm.setVisibility(View.VISIBLE);

		final String simlarId = PreferencesHelper.getMySimlarIdOrEmptyString();
		if (Util.isNullOrEmpty(registrationCode) || Util.isNullOrEmpty(simlarId)) {
			Lg.e("Error: registrationCode or simlarId empty");
			showMessage(R.string.create_account_activity_error_not_possible);
			return;
		}

		new AsyncTask<String, Void, CreateAccount.ConfirmResult>()
		{

			@Override
			protected CreateAccount.ConfirmResult doInBackground(final String... params)
			{
				return CreateAccount.httpPostConfirm(params[0], params[1]);
			}

			@Override
			protected void onPostExecute(final CreateAccount.ConfirmResult result)
			{
				mProgressConfirm.setVisibility(View.INVISIBLE);

				if (result.isError()) {
					Lg.e("failed to parse confirm result");
					showMessage(result.getErrorMessage());
					return;
				}

				if (!Util.equalString(result.getSimlarId(), simlarId)) {
					Lg.e("confirm response received simlarId=", new Lg.Anonymizer(result.getSimlarId()),
							" not equal to requested simlarId=", new Lg.Anonymizer(simlarId));
					showMessage(R.string.create_account_activity_error_not_possible);
					return;
				}

				PreferencesHelper.saveToFileCreateAccountStatus(CreateAccountActivity.this, CreateAccountStatus.SUCCESS);
				connectToServer();
			}

		}.execute(simlarId, registrationCode);
	}

	private void connectToServer()
	{
		mProgressFirstLogIn.setVisibility(View.VISIBLE);
		mCommunicator.startServiceAndRegister(this, VerifyNumberActivity.class, null);
	}

	private void showMessage(final int resId)
	{
		mLayoutProgress.setVisibility(View.GONE);
		mLayoutMessage.setVisibility(View.VISIBLE);

		switch (resId) {
		case R.string.create_account_activity_error_wrong_telephone_number:
			mDetails.setText(String.format(getString(resId), mTelephoneNumber));
			setRegistrationCodeInputVisible(false);
			break;
		case R.string.create_account_activity_error_registration_code:
		case R.string.create_account_activity_error_too_many_calls:
			mDetails.setText(resId);
			setRegistrationCodeInputVisible(true);
			break;
		case R.string.create_account_activity_error_sms:
		case R.string.create_account_activity_error_sms_not_granted_or_timeout:
		case R.string.create_account_activity_error_sms_call_success:
			mDetails.setText(String.format(getString(resId), mTelephoneNumber));
			setRegistrationCodeInputVisible(true);
			break;
		default:
			mDetails.setText(resId);
			setRegistrationCodeInputVisible(false);
		}
	}

	private void setRegistrationCodeInputVisible(final boolean visible)
	{
		final int visibility = visible ? View.VISIBLE : View.GONE;

		mButtonCall.setVisibility(visibility);
		mButtonConfirm.setVisibility(visibility);
		mEditRegistrationCode.setVisibility(visibility);
		if (visible) {
			mButtonCall.setEnabled(false);
			mButtonConfirm.setEnabled(false);
			mEditRegistrationCode.requestFocus();
			((InputMethodManager) Util.getSystemService(this, Context.INPUT_METHOD_SERVICE)).showSoftInput(mEditRegistrationCode,
					InputMethodManager.SHOW_IMPLICIT);

			updateCallButton();
		} else {
			((InputMethodManager) Util.getSystemService(this, Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mEditRegistrationCode.getWindowToken(), 0);
		}

	}

	@SuppressWarnings("UseOfObsoleteDateTimeApi") /// java8 one's requires android sdk version 26
	private void updateCallButton()
	{
		if (mButtonCall.getVisibility() == View.GONE) {
			return;
		}

		final Date now = Calendar.getInstance().getTime();
		final Date begin = new Date(PreferencesHelper.getCreateAccountRequestTimestamp() + 90 * 1000);
		final Date end = new Date(PreferencesHelper.getCreateAccountRequestTimestamp() + 10 * 60 * 1000);

		if (now.after(end)) {
			mButtonCall.setText(R.string.create_account_activity_button_call_not_available);
			mButtonCall.setEnabled(false);
			return;
		}

		if (now.before(begin)) {
			mButtonCall.setText(String.format(getString(R.string.create_account_activity_button_call_available_in), (begin.getTime() - now.getTime()) / 1000));
			mButtonCall.setEnabled(false);
		} else {
			mButtonCall.setText(R.string.create_account_activity_button_call);
			mButtonCall.setEnabled(true);
		}

		mHandler.postDelayed(this::updateCallButton, 1000);
	}

	@SuppressWarnings("unused")
	public void onCancelClicked(final View view)
	{
		Lg.i("onCancelClicked");
		PreferencesHelper.saveToFileCreateAccountStatus(this, CreateAccountStatus.NONE);
		finish();
	}

	@SuppressLint("StaticFieldLeak")
	@SuppressWarnings("unused")
	public void onCallClicked(final View view)
	{
		Lg.i("onCallClicked");

		final String telephoneNumber = mTelephoneNumber;

		mLayoutProgress.setVisibility(View.VISIBLE);
		mLayoutMessage.setVisibility(View.GONE);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms_call);
		mProgressWaitingForSMS.setVisibility(View.VISIBLE);

		new AsyncTask<String, Void, CreateAccount.RequestResult>()
		{
			@Override
			protected CreateAccount.RequestResult doInBackground(final String... params)
			{
				return CreateAccount.httpPostCall(params[0], params[1]);
			}

			@Override
			protected void onPostExecute(final CreateAccount.RequestResult result)
			{
				mProgressWaitingForSMS.setVisibility(View.INVISIBLE);

				if (result.isError()) {
					Lg.e("failed to parse call result");
					showMessage(result.getErrorMessage());
					return;
				}

				Lg.i("successfully requested call");
				showMessage(R.string.create_account_activity_error_sms_call_success);
			}
		}.execute(telephoneNumber, PreferencesHelper.getPassword());
	}

	@SuppressWarnings("unused")
	public void onConfirmClicked(final View view)
	{
		Lg.i("onConfirmClicked");
		((InputMethodManager) Util.getSystemService(this, Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mEditRegistrationCode.getWindowToken(), 0);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms_manual);
		mLayoutProgress.setVisibility(View.VISIBLE);
		mLayoutMessage.setVisibility(View.GONE);

		confirmRegistrationCode(mEditRegistrationCode.getText().toString());
	}

	@Override
	public void onBackPressed()
	{
		// prevent back key from doing anything
	}
}

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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

public class CreateAccountActivity extends Activity
{
	static final String LOGTAG = CreateAccountActivity.class.getSimpleName();
	public static final String INTENT_EXTRA_NUMBER = "telefonNumber";
	private static final int SECONDS_TO_WAIT_FOR_SMS = 30;
	private static final String SIMLAR_SMS_SOURCE = "+4922199999930";

	private View mLayoutProgress = null;
	ProgressBar mProgressRequest = null;
	ProgressBar mProgressWaitingForSMS = null;
	ProgressBar mProgressConfirm = null;
	ProgressBar mProgressFirstLogIn = null;
	private TextView mWaitingForSmsText = null;
	private EditText mEditRegistrationCode = null;
	private TextView mDetails = null;
	Button mButtonConfirm = null;
	private Button mButtonCancel = null;

	private int mSecondsToStillWaitForSms = 0;
	private Handler mHandler = null;

	private BroadcastReceiver mSmsReceiver = null;
	private SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCreateAccount();

	private class SimlarServiceCommunicatorCreateAccount extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorCreateAccount()
		{
			super(LOGTAG);
		}

		@Override
		void onTestRegistrationFailed()
		{
			Log.i(LOGTAG, "onTestRegistrationFailed");
			mProgressFirstLogIn.setVisibility(View.INVISIBLE);
			onError(R.string.create_account_activity_error_sip_not_possible);
		}

		@Override
		void onTestRegistrationSuccess()
		{
			Log.i(LOGTAG, "onTestRegistrationSuccess");
			mProgressFirstLogIn.setVisibility(View.INVISIBLE);
			setResult(RESULT_OK);
			finish();
		}

		@Override
		void onServiceFinishes()
		{
			Log.i(LOGTAG, "onServiceFinishes");
			setResult(RESULT_OK);
			finish();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.i(LOGTAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_create_account);

		// versions before HONEYCOMB do not support FinishOnTouchOutsides
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setFinishOnTouchOutside(false);
		}

		mHandler = new Handler();

		mLayoutProgress = findViewById(R.id.linearLayoutProgress);
		mProgressRequest = (ProgressBar) findViewById(R.id.progressBarRequest);
		mProgressWaitingForSMS = (ProgressBar) findViewById(R.id.progressBarWaitingForSMS);
		mProgressConfirm = (ProgressBar) findViewById(R.id.progressBarConfirm);
		mProgressFirstLogIn = (ProgressBar) findViewById(R.id.progressBarFirstLogIn);

		mProgressRequest.setVisibility(View.VISIBLE);
		mProgressWaitingForSMS.setVisibility(View.INVISIBLE);
		mProgressConfirm.setVisibility(View.INVISIBLE);
		mProgressFirstLogIn.setVisibility(View.INVISIBLE);

		mWaitingForSmsText = (TextView) findViewById(R.id.textViewWaitingForSMS);
		mEditRegistrationCode = (EditText) findViewById(R.id.editTextRegistrationCode);
		mEditRegistrationCode.setVisibility(View.GONE);
		mDetails = (TextView) findViewById(R.id.textViewDetails);
		mDetails.setVisibility(View.GONE);

		mButtonConfirm = (Button) findViewById(R.id.buttonConfirm);
		mButtonConfirm.setVisibility(View.GONE);
		mButtonCancel = (Button) findViewById(R.id.buttonCancel);
		mButtonCancel.setVisibility(View.GONE);

		mEditRegistrationCode.addTextChangedListener(new TextWatcher()
		{
			@Override
			public void onTextChanged(final CharSequence s, int start, int before, int count)
			{
				if (s.length() == 6) {
					mButtonConfirm.setEnabled(true);
				} else {
					mButtonConfirm.setEnabled(false);
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after)
			{
			}

			@Override
			public void afterTextChanged(Editable s)
			{
			}
		});

		mSmsReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent)
			{
				if (intent == null) {
					return;
				}

				final Bundle extras = intent.getExtras();
				if (extras == null) {
					return;
				}

				final Object[] pdus = (Object[]) extras.get("pdus");
				for (int i = 0; i < pdus.length; i++)
				{
					final SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
					onSmsReceived(sms.getOriginatingAddress(), sms.getMessageBody().toString());
				}
			}
		};

		if (PreferencesHelper.getCreateAccountStatus() == CreateAccountStatus.WAITING_FOR_SMS) {
			onWaitingForSmsTimedOut();
		} else {
			createAccountRequest(getIntent().getStringExtra(INTENT_EXTRA_NUMBER));
		}
	}

	/// Workaround to close soft keyboard on older android versions
	@Override
	public boolean onKeyUp(final int keyCode, final KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_ENTER) {
			((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mEditRegistrationCode.getWindowToken(), 0);
			return true;
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		return true;
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();

		mCommunicator.register(this, VerifyNumberActivity.class);
	}

	@Override
	protected void onPause()
	{
		Log.i(LOGTAG, "onPause");
		mCommunicator.unregister(this);
		super.onPause();
	}

	private void createAccountRequest(final String telephoneNumber)
	{
		if (Util.isNullOrEmpty(telephoneNumber)) {
			Log.e(LOGTAG, "createAccountRequest without telephoneNumber");
			return;
		}

		Log.i(LOGTAG, "createAccountRequest: " + telephoneNumber);
		final String smsText = getString(R.string.create_account_activity_sms_text) + " ";
		final String expextedSimlarId = SimlarNumber.createSimlarNumber(telephoneNumber);

		IntentFilter filter = new IntentFilter();
		filter.addAction("android.provider.Telephony.SMS_RECEIVED");
		registerReceiver(mSmsReceiver, filter);

		new AsyncTask<String, Void, CreateAccount.RequestResult>() {

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
					onError(result.getErrorMessage());
					return;
				}

				if (!result.getSimlarId().equals(expextedSimlarId)) {
					Log.e(LOGTAG, "received simlarId not equal to expected: telephonenumber=" + telephoneNumber + " expected=" + expextedSimlarId
							+ " actual=" + result.getSimlarId());
				}

				PreferencesHelper.init(result.getSimlarId(), result.getPassword());
				PreferencesHelper.saveToFilePreferences(CreateAccountActivity.this);
				PreferencesHelper.saveToFileCreateAccountStatus(CreateAccountActivity.this, CreateAccountStatus.WAITING_FOR_SMS);
				waitForSms();
			}

		}.execute(telephoneNumber, smsText);
	}

	void waitForSms()
	{
		Log.i(LOGTAG, "waiting for sms");
		mProgressWaitingForSMS.setVisibility(View.VISIBLE);

		mSecondsToStillWaitForSms = SECONDS_TO_WAIT_FOR_SMS;
		waitingForSmsIteration();
	}

	void waitingForSmsIteration()
	{
		mWaitingForSmsText.setText(getString(R.string.create_account_activity_waiting_for_sms) + " (" + mSecondsToStillWaitForSms + "s)");
		--mSecondsToStillWaitForSms;
		if (mSecondsToStillWaitForSms >= 0) {
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run()
				{
					waitingForSmsIteration();
				}
			}, 1000);
		} else {
			onWaitingForSmsTimedOut();
		}
	}

	private void onWaitingForSmsTimedOut()
	{
		Log.w(LOGTAG, "waiting for sms timedout");
		unregisterReceiver(mSmsReceiver);

		mProgressWaitingForSMS.setVisibility(View.INVISIBLE);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms);

		onError(R.string.create_account_activity_error_sms_timeout);
	}

	void onSmsReceived(final String sender, final String message)
	{
		if (Util.isNullOrEmpty(sender) || Util.isNullOrEmpty(message)) {
			return;
		}

		if (!sender.equals(SIMLAR_SMS_SOURCE)) {
			Log.i(LOGTAG, "ignoring SMS from: " + sender);
			return;
		}

		Log.i(LOGTAG, "received sms: sender=" + sender + " message=" + message);

		final String simlarTag = getString(R.string.create_account_activity_sms_text) + " ";
		if (!message.startsWith(simlarTag)) {
			Log.e(LOGTAG, "unable to parse sms message: " + message);
			return;
		}
		final String registrationCode = message.split(simlarTag)[1];

		unregisterReceiver(mSmsReceiver);
		mHandler.removeCallbacksAndMessages(null);
		mProgressWaitingForSMS.setVisibility(View.INVISIBLE);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms);

		confirmRegistrationCode(registrationCode);
	}

	void confirmRegistrationCode(final String registrationCode)
	{
		Log.i(LOGTAG, "confirmRegistrationCode: " + registrationCode);

		mProgressConfirm.setVisibility(View.VISIBLE);

		final String simlarId = PreferencesHelper.getMySimlarIdOrEmptyString();
		if (Util.isNullOrEmpty(registrationCode) || Util.isNullOrEmpty(simlarId)) {
			Log.e(LOGTAG, "failed to parse confirm result");
			onError(R.string.create_account_activity_error_not_possible);
			return;
		}

		new AsyncTask<String, Void, CreateAccount.ConfirmResult>() {

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
					Log.e(LOGTAG, "failed to parse confirm result");
					onError(result.getErrorMessage());
					return;
				}

				if (!result.getSimlarId().equals(simlarId)) {
					Log.e(LOGTAG, "confirm response received simlarId=" + result.getSimlarId() + " not equal to requested simlarId=" + simlarId);
					onError(R.string.create_account_activity_error_not_possible);
					return;
				}

				PreferencesHelper.saveToFileCreateAccountStatus(CreateAccountActivity.this, CreateAccountStatus.SUCCESS);
				connectToServer();
			}

		}.execute(simlarId, registrationCode);

	}

	void connectToServer()
	{
		mProgressFirstLogIn.setVisibility(View.VISIBLE);
		mCommunicator.getService().connect();
	}

	void onError(int resId)
	{
		mLayoutProgress.setVisibility(View.GONE);
		mDetails.setText(resId);
		mDetails.setVisibility(View.VISIBLE);
		mButtonCancel.setVisibility(View.VISIBLE);

		if (resId == R.string.create_account_activity_error_sms_timeout
				|| resId == R.string.create_account_activity_error_registration_code)
		{
			mButtonConfirm.setVisibility(View.VISIBLE);
			mButtonConfirm.setEnabled(false);

			mEditRegistrationCode.setVisibility(View.VISIBLE);
			mEditRegistrationCode.requestFocus();
			((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(mEditRegistrationCode,
					InputMethodManager.SHOW_IMPLICIT);
		} else {
			mButtonConfirm.setVisibility(View.GONE);
		}
	}

	@SuppressWarnings("unused")
	public void onCancelClicked(View view)
	{
		PreferencesHelper.saveToFileCreateAccountStatus(CreateAccountActivity.this, CreateAccountStatus.NONE);
		finish();
	}

	@SuppressWarnings("unused")
	public void onConfirmClicked(View view)
	{
		((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mEditRegistrationCode.getWindowToken(), 0);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms_manual);
		mDetails.setVisibility(View.GONE);
		mEditRegistrationCode.setVisibility(View.GONE);
		mButtonConfirm.setVisibility(View.GONE);
		mButtonCancel.setVisibility(View.GONE);
		mLayoutProgress.setVisibility(View.VISIBLE);

		confirmRegistrationCode(mEditRegistrationCode.getText().toString());
	}

	@Override
	public void onBackPressed()
	{
		// prevent switch to VerifyNumberActivity
		//moveTaskToBack(true);
	}
}

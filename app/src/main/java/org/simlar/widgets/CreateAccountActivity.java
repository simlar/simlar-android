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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.telephony.SmsMessage;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CreateAccountActivity extends Activity
{
	public static final String INTENT_EXTRA_NUMBER = "CreateAccountActivityTelephoneNumber";
	private static final int SECONDS_TO_WAIT_FOR_SMS = 90;
	private static final String SIMLAR_SMS_SOURCE = "+4922199999930";

	private View mLayoutProgress = null;
	private ProgressBar mProgressRequest = null;
	private ProgressBar mProgressWaitingForSMS = null;
	private ProgressBar mProgressConfirm = null;
	private ProgressBar mProgressFirstLogIn = null;
	private TextView mWaitingForSmsText = null;
	private EditText mEditRegistrationCode = null;
	private TextView mDetails = null;
	private Button mButtonConfirm = null;
	private Button mButtonCancel = null;

	private int mSecondsToStillWaitForSms = 0;
	private final Handler mHandler = new Handler();

	private final BroadcastReceiver mSmsReceiver = new SmsReceiver();
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
				CreateAccountActivity.this.finish();
			} else {
				onError(R.string.create_account_activity_error_sip_not_possible);
			}
		}
	}

	private final class EditRegistrationCodeListener implements TextWatcher
	{
		public EditRegistrationCodeListener()
		{
			super();
		}

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

	private final class SmsReceiver extends BroadcastReceiver
	{
		public SmsReceiver()
		{
			super();
		}

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
			if (pdus == null) {
				return;
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				final String smsFormat = extras.getString("format");
				if (smsFormat == null) {
					Lg.e("received sms with no format");
					return;
				}

				for (final Object pdu : pdus) {
					final SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, smsFormat);
					onSmsReceived(sms.getOriginatingAddress(), sms.getMessageBody());
				}
			} else {
				for (final Object pdu : pdus) {
					//noinspection deprecation
					final SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
					onSmsReceived(sms.getOriginatingAddress(), sms.getMessageBody());
				}
			}
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Lg.i("onCreate");

		setContentView(R.layout.activity_create_account);

		Util.setFinishOnTouchOutsideCompatible(this, false);

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

		mEditRegistrationCode.addTextChangedListener(new EditRegistrationCodeListener());

		registerSmsReceiver();

		if (PreferencesHelper.getCreateAccountStatus() == CreateAccountStatus.WAITING_FOR_SMS) {
			mTelephoneNumber = PreferencesHelper.getVerifiedTelephoneNumber();
			onWaitingForSmsTimedOut();
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
			((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mEditRegistrationCode.getWindowToken(), 0);
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

	@Override
	protected void onDestroy()
	{
		Lg.i("onPause");
		unregisterReceiver(mSmsReceiver);
		super.onDestroy();
	}

	private void createAccountRequest()
	{
		if (Util.isNullOrEmpty(mTelephoneNumber)) {
			Lg.e("createAccountRequest without telephone number");
			return;
		}

		Lg.i("createAccountRequest: ", new Lg.Anonymizer(mTelephoneNumber));
		final String smsText = getString(R.string.create_account_activity_sms_text) + " ";
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
					onError(result.getErrorMessage());
					return;
				}

				if (!result.getSimlarId().equals(expectedSimlarId)) {
					Lg.e("received simlarId not equal to expected: telephoneNumber=", new Lg.Anonymizer(telephoneNumber),
							" expected=", new Lg.Anonymizer(expectedSimlarId),
							" actual=", new Lg.Anonymizer(result.getSimlarId()));
				}

				PreferencesHelper.init(result.getSimlarId(), result.getPassword());
				PreferencesHelper.saveToFilePreferences(CreateAccountActivity.this);
				PreferencesHelper.saveToFileCreateAccountStatus(CreateAccountActivity.this, CreateAccountStatus.WAITING_FOR_SMS, telephoneNumber);
				waitForSms();
			}

		}.execute(mTelephoneNumber, smsText);
	}

	private void registerSmsReceiver()
	{
		final IntentFilter filter = new IntentFilter();
		filter.addAction("android.provider.Telephony.SMS_RECEIVED");
		filter.setPriority(1002); // TextSecure uses 1001
		registerReceiver(mSmsReceiver, filter);
	}

	private void waitForSms()
	{
		Lg.i("waiting for sms");
		mProgressWaitingForSMS.setVisibility(View.VISIBLE);

		mSecondsToStillWaitForSms = SECONDS_TO_WAIT_FOR_SMS;
		waitingForSmsIteration();
	}

	@SuppressLint("SetTextI18n")
	private void waitingForSmsIteration()
	{
		mWaitingForSmsText.setText(getString(R.string.create_account_activity_waiting_for_sms) + " (" + mSecondsToStillWaitForSms + "s)");
		--mSecondsToStillWaitForSms;
		if (mSecondsToStillWaitForSms >= 0) {
			mHandler.postDelayed(new Runnable()
			{
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
		Lg.w("waiting for sms timed out");

		mProgressWaitingForSMS.setVisibility(View.INVISIBLE);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms);

		onError(R.string.create_account_activity_error_sms_timeout);
	}

	private void onSmsReceived(final String sender, final String message)
	{
		if (Util.isNullOrEmpty(sender) || Util.isNullOrEmpty(message)) {
			return;
		}

		if (!sender.equals(SIMLAR_SMS_SOURCE)) {
			Lg.i("ignoring sms from: ", new Lg.Anonymizer(sender));
			return;
		}

		Lg.i("received sms: sender=", sender, " message=", message);

		final String regex = getString(R.string.create_account_activity_sms_text).replace("*CODE*", "(\\d{6})");
		final Matcher matcher = Pattern.compile(regex).matcher(message);
		if (!matcher.find()) {
			Lg.e("unable to parse sms message: ", message);
			return;
		}
		final String registrationCode = matcher.group(1);

		mHandler.removeCallbacksAndMessages(null);
		mProgressWaitingForSMS.setVisibility(View.INVISIBLE);
		mWaitingForSmsText.setText(R.string.create_account_activity_waiting_for_sms);

		confirmRegistrationCode(registrationCode);
	}

	private void confirmRegistrationCode(final String registrationCode)
	{
		Lg.i("confirmRegistrationCode: ", registrationCode);

		mProgressConfirm.setVisibility(View.VISIBLE);

		final String simlarId = PreferencesHelper.getMySimlarIdOrEmptyString();
		if (Util.isNullOrEmpty(registrationCode) || Util.isNullOrEmpty(simlarId)) {
			Lg.e("Error: registrationCode or simlarId empty");
			onError(R.string.create_account_activity_error_not_possible);
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
					onError(result.getErrorMessage());
					return;
				}

				if (!result.getSimlarId().equals(simlarId)) {
					Lg.e("confirm response received simlarId=", new Lg.Anonymizer(result.getSimlarId()),
							" not equal to requested simlarId=", new Lg.Anonymizer(simlarId));
					onError(R.string.create_account_activity_error_not_possible);
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

	private void onError(final int resId)
	{
		mLayoutProgress.setVisibility(View.GONE);
		if (resId == R.string.create_account_activity_error_wrong_telephone_number ||
				resId == R.string.create_account_activity_error_sms ||
				resId == R.string.create_account_activity_error_sms_timeout) {
			mDetails.setText(String.format(getString(resId), mTelephoneNumber));
		} else {
			mDetails.setText(resId);
		}
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
	public void onCancelClicked(final View view)
	{
		PreferencesHelper.saveToFileCreateAccountStatus(CreateAccountActivity.this, CreateAccountStatus.NONE);
		finish();
	}

	@SuppressWarnings("unused")
	public void onConfirmClicked(final View view)
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
		// prevent back key from doing anything
	}
}

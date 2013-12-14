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
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CallActivity extends Activity implements SensorEventListener
{
	static final String LOGTAG = CallActivity.class.getSimpleName();

	private SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCall();
	private SensorManager mSensorManager;
	private Sensor mSensor;

	private class SimlarServiceCommunicatorCall extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorCall()
		{
			super(LOGTAG);
		}

		@Override
		void onBoundToSimlarService()
		{
			CallActivity.this.onSimlarCallStateChanged();
			CallActivity.this.setButtonMicophoneMute();
			CallActivity.this.setButtonSpeakerMute();
		}

		@Override
		void onSimlarCallStateChanged()
		{
			CallActivity.this.onSimlarCallStateChanged();
		}

		@Override
		void onServiceFinishes()
		{
			CallActivity.this.finish();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.i(LOGTAG, "onCreate ");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_call);

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
				WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

		final TextView textViewLabelToken = (TextView) findViewById(R.id.textViewLabelAuthenticationToken);
		final TextView textViewToken = (TextView) findViewById(R.id.textViewAuthenticationToken);
		final Button buttonVerify = (Button) findViewById(R.id.buttonAuthenticationTokenVerify);
		final Button buttonWrong = (Button) findViewById(R.id.buttonAuthenticationTokenWrong);
		final ImageButton buttonInfo = (ImageButton) findViewById(R.id.buttonConnectionDetails);
		textViewLabelToken.setVisibility(View.INVISIBLE);
		textViewToken.setVisibility(View.INVISIBLE);
		buttonVerify.setVisibility(View.INVISIBLE);
		buttonWrong.setVisibility(View.INVISIBLE);
		buttonInfo.setVisibility(View.INVISIBLE);

		final LinearLayout callStatus = (LinearLayout) findViewById(R.id.linearLayoutCallStatus);
		final LinearLayout connectionQuality = (LinearLayout) findViewById(R.id.linearLayoutConnectionQuality);
		callStatus.setVisibility(View.INVISIBLE);
		connectionQuality.setVisibility(View.INVISIBLE);

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();
		mCommunicator.register(this, CallActivity.class);
		mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	protected void onPause()
	{
		Log.i(LOGTAG, "onPause");
		mCommunicator.unregister(this);
		mSensorManager.unregisterListener(this);
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		return true;
	}

	private void setCallStatus(final String status)
	{
		final TextView tv = (TextView) findViewById(R.id.textViewCallStatus);
		tv.setText(status);
	}

	void setQuality(String quality)
	{
		final TextView tvQuality = (TextView) findViewById(R.id.textViewQuality);
		tvQuality.setText(quality);
	}

	void setCallEncryption(final boolean encrypted, final String authenticationToken, final boolean authenticationTokenVerified)
	{
		final TextView label = (TextView) findViewById(R.id.textViewLabelAuthenticationToken);
		final TextView token = (TextView) findViewById(R.id.textViewAuthenticationToken);
		final Button verify = (Button) findViewById(R.id.buttonAuthenticationTokenVerify);
		final Button wrong = (Button) findViewById(R.id.buttonAuthenticationTokenWrong);
		final LinearLayout linearLayoutAuthenticationToken = (LinearLayout) findViewById(R.id.linearLayoutAuthenticationToken);

		if (!encrypted) {
			linearLayoutAuthenticationToken.setVisibility(View.GONE);
			label.setVisibility(View.VISIBLE);
			label.setText(R.string.error_not_encrypted);
			token.setVisibility(View.INVISIBLE);
			verify.setVisibility(View.INVISIBLE);
			wrong.setVisibility(View.INVISIBLE);
			linearLayoutAuthenticationToken.setVisibility(View.VISIBLE);
			return;
		}

		if (Util.isNullOrEmpty(authenticationToken)) {
			linearLayoutAuthenticationToken.setVisibility(View.GONE);
			label.setVisibility(View.INVISIBLE);
			token.setVisibility(View.INVISIBLE);
			verify.setVisibility(View.INVISIBLE);
			wrong.setVisibility(View.INVISIBLE);
			return;
		}

		token.setText(authenticationToken);
		token.setVisibility(View.VISIBLE);
		label.setVisibility(View.VISIBLE);
		linearLayoutAuthenticationToken.setVisibility(View.VISIBLE);
		if (authenticationTokenVerified) {
			label.setText(R.string.verified_authentication_token);
			linearLayoutAuthenticationToken.setVisibility(View.GONE);
			verify.setVisibility(View.INVISIBLE);
			wrong.setVisibility(View.INVISIBLE);
		} else {
			label.setText(R.string.please_verify_authentication_token);
			verify.setVisibility(View.VISIBLE);
			wrong.setVisibility(View.VISIBLE);
			linearLayoutAuthenticationToken.setVisibility(View.VISIBLE);
		}
	}

	void onSimlarCallStateChanged()
	{
		if (mCommunicator.getService() == null) {
			Log.e(LOGTAG, "ERROR: onSimlarCallStateChanged but not bound to service");
			return;
		}

		final SimlarCallState simlarCallState = mCommunicator.getService().getSimlarCallState();
		if (simlarCallState == null || simlarCallState.isEmpty()) {
			Log.e(LOGTAG, "ERROR: onSimlarCallStateChanged simlarCallState null or empty");
			return;
		}

		Log.i(LOGTAG, "onSimlarCallStateChanged " + simlarCallState);

		setTitle(getString(R.string.title_activity_call) + " " + simlarCallState.getDisplayName());
		setCallEncryption(simlarCallState.isEncrypted(), simlarCallState.getAuthenticationToken(), simlarCallState.isAuthenticationTokenVerified());

		final LinearLayout callStatus = (LinearLayout) findViewById(R.id.linearLayoutCallStatus);
		final LinearLayout connectionQuality = (LinearLayout) findViewById(R.id.linearLayoutConnectionQuality);
		final ImageButton buttonInfo = (ImageButton) findViewById(R.id.buttonConnectionDetails);

		if (simlarCallState.hasConnectionInfo()) {
			setQuality(getString(simlarCallState.getQualityDescription()));
			connectionQuality.setVisibility(View.VISIBLE);
			buttonInfo.setVisibility(View.VISIBLE);
			getString(simlarCallState.getQualityDescription());
		} else {
			connectionQuality.setVisibility(View.INVISIBLE);

			if (simlarCallState.hasCallStatusMessage()) {
				callStatus.setVisibility(View.VISIBLE);
				setCallStatus(simlarCallState.getCallStatusDisplayMessage(this));
			} else if (simlarCallState.hasErrorMessage()) {
				callStatus.setVisibility(View.VISIBLE);
				setCallStatus(simlarCallState.getErrorDisplayMessage(this, simlarCallState.getDisplayName()));
			} else {
				callStatus.setVisibility(View.INVISIBLE);
			}
		}

		if (simlarCallState.isTalking()) {
			setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
		}

		if (simlarCallState.isEndedCall()) {
			if (simlarCallState.hasErrorMessage()) {
				finishDelayed(5000);
			} else {
				finish();
			}
		}
	}

	private void finishDelayed(final int milliSeconds)
	{
		Log.i(LOGTAG, "finishing activity in " + milliSeconds + " ms");

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run()
			{
				finish();
			}
		}, milliSeconds);
	}

	@SuppressWarnings("unused")
	public void verifyAuthenticationToken(View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(true);
	}

	@SuppressWarnings("unused")
	public void wrongAuthenticationToken(View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(false);
		//terminateCall(view);
	}

	@SuppressWarnings("unused")
	public void showConnectionDetails(View view)
	{
		startActivity(new Intent(this, ConnectionDetailsActivity.class));
	}

	@SuppressWarnings("unused")
	public void showSoundSettingsDialog(View view)
	{
		startActivity(new Intent(this, VolumesControlActivity.class));
	}

	@SuppressWarnings("unused")
	public void toggleMicrophoneMuted(View view)
	{
		mCommunicator.getService().setVolumes(mCommunicator.getService().getVolumes().toggleMicrophoneMuted());
		setButtonMicophoneMute();
	}

	@SuppressWarnings("unused")
	public void toggleSpeakerMuted(View view)
	{
		mCommunicator.getService().setVolumes(mCommunicator.mService.getVolumes().toggleExternalSpeaker());
		setButtonSpeakerMute();
	}

	void setButtonMicophoneMute()
	{
		final ImageButton button = (ImageButton) findViewById(R.id.buttonMicro);

		if (mCommunicator.getService().getVolumes().getMicrophoneMuted()) {
			button.setImageResource(R.drawable.micro_off);
			button.setContentDescription(getString(R.string.call_activity_microphone_mute));
		} else {
			button.setImageResource(R.drawable.micro_on);
			button.setContentDescription(getString(R.string.call_activity_microphone_on));
		}
	}

	void setButtonSpeakerMute()
	{
		final ImageButton button = (ImageButton) findViewById(R.id.buttonSpeaker);

		if (mCommunicator.getService().getVolumes().getExternalSpeaker()) {
			button.setImageResource(R.drawable.speaker_on);
			button.setContentDescription(getString(R.string.call_activity_loudspeaker_on));
		} else {
			button.setImageResource(R.drawable.speaker_off);
			button.setContentDescription(getString(R.string.call_activity_loudspeaker_off));
		}
	}

	@SuppressWarnings("unused")
	public void terminateCall(View view)
	{
		mCommunicator.getService().terminateCall();
		finish();
	}

	@Override
	public void onBackPressed()
	{
		// prevent switch to MainActivity
		moveTaskToBack(true);
	}

	//
	// SensorEventListener overloaded member functions
	//
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		WindowManager.LayoutParams params = getWindow().getAttributes();
		if (event.values[0] == 0) {
			params.screenBrightness = 0.1f;
		} else {
			params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
		}
		getWindow().setAttributes(params);
	}
}

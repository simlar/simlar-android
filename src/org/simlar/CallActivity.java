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

	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCall();
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
	protected void onCreate(final Bundle savedInstanceState)
	{
		Log.i(LOGTAG, "onCreate ");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_call);

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
				WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

		final LinearLayout callStatus = (LinearLayout) findViewById(R.id.linearLayoutCallStatus);
		final LinearLayout connectionQuality = (LinearLayout) findViewById(R.id.linearLayoutConnectionQuality);
		final LinearLayout linearLayoutAuthenticationToken = (LinearLayout) findViewById(R.id.linearLayoutAuthenticationToken);

		callStatus.setVisibility(View.INVISIBLE);
		connectionQuality.setVisibility(View.INVISIBLE);
		linearLayoutAuthenticationToken.setVisibility(View.INVISIBLE);

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
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	private void setCallStatus(final String status)
	{
		final TextView tv = (TextView) findViewById(R.id.textViewCallStatus);
		tv.setText(status);
	}

	void setQuality(final String quality)
	{
		final TextView tvQuality = (TextView) findViewById(R.id.textViewQuality);
		tvQuality.setText(quality);
	}

	void setCallEncryption(final boolean encrypted, final String authenticationToken, final boolean authenticationTokenVerified)
	{
		final LinearLayout linearLayoutAuthenticationToken = (LinearLayout) findViewById(R.id.linearLayoutAuthenticationToken);
		final LinearLayout linearLayoutUnencryptedCall = (LinearLayout) findViewById(R.id.linearLayoutUnencryptedCall);

		if (!encrypted) {
			linearLayoutAuthenticationToken.setVisibility(View.GONE);
			linearLayoutUnencryptedCall.setVisibility(View.VISIBLE);
			return;
		}
		linearLayoutUnencryptedCall.setVisibility(View.GONE);

		if (authenticationTokenVerified || Util.isNullOrEmpty(authenticationToken)) {
			linearLayoutAuthenticationToken.setVisibility(View.GONE);
			return;
		}

		linearLayoutAuthenticationToken.setVisibility(View.VISIBLE);
		final TextView token = (TextView) findViewById(R.id.textViewAuthenticationToken);
		token.setText(authenticationToken);
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

		setTitle(getString(R.string.call_activity_title) + " " + simlarCallState.getDisplayName());
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
	public void verifyAuthenticationToken(final View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(true);
	}

	@SuppressWarnings("unused")
	public void wrongAuthenticationToken(final View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(false);
		//terminateCall(view);
	}

	@SuppressWarnings("unused")
	public void showConnectionDetails(final View view)
	{
		startActivity(new Intent(this, ConnectionDetailsActivity.class));
	}

	@SuppressWarnings("unused")
	public void acceptUnencryptedCall(final View view)
	{
		mCommunicator.getService().acceptUnencryptedCall();

		final Button button = (Button) findViewById(R.id.buttonAcceptUnencryptedCall);
		button.setVisibility(View.INVISIBLE);
	}

	@SuppressWarnings("unused")
	public void showSoundSettingsDialog(final View view)
	{
		startActivity(new Intent(this, VolumesControlActivity.class));
	}

	@SuppressWarnings("unused")
	public void toggleMicrophoneMuted(final View view)
	{
		mCommunicator.getService().setVolumes(mCommunicator.getService().getVolumes().toggleMicrophoneMuted());
		setButtonMicophoneMute();
	}

	@SuppressWarnings("unused")
	public void toggleSpeakerMuted(final View view)
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
	public void terminateCall(final View view)
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
	public void onAccuracyChanged(final Sensor sensor, final int accuracy)
	{
	}

	@Override
	public void onSensorChanged(final SensorEvent event)
	{
		final WindowManager.LayoutParams params = getWindow().getAttributes();
		if (event.values[0] == 0) {
			params.screenBrightness = 0.1f;
		} else {
			params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
		}
		getWindow().setAttributes(params);
	}
}

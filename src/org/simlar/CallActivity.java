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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class CallActivity extends Activity implements SensorEventListener
{
	static final String LOGTAG = CallActivity.class.getSimpleName();

	private static final float PROXIMITY_DISTANCE_THRESHOLD = 4.0f;

	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCall();
	private SensorManager mSensorManager;
	private long mCallStartTime = -1;
	private final Handler mHandler = new Handler();

	// gui elements
	private ImageView mImageViewContactImage;
	private TextView mTextViewContactName;
	private TextView mTextViewCallTimer;

	private LinearLayout mLayoutCallStatus;
	private TextView mTextViewCallStatus;

	private LinearLayout mLayoutConnectionQuality;
	private TextView mTextViewQuality;
	private ImageButton mButtonConnectionDetails;

	private LinearLayout mLayoutAuthenticationToken;
	private TextView mTextViewAuthenticationToken;

	private LinearLayout mLayoutUnencryptedCall;
	private Button mButtonAcceptUnencryptedCall;

	private ImageButton mButtonMicro;
	private ImageButton mButtonSpeaker;

	private final class SimlarServiceCommunicatorCall extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorCall()
		{
			super(LOGTAG);
		}

		@Override
		void onBoundToSimlarService()
		{
			CallActivity.this.onSimlarCallStateChanged();
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

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_call);

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

		mImageViewContactImage = (ImageView) findViewById(R.id.contactImage);
		mTextViewContactName = (TextView) findViewById(R.id.contactName);
		mTextViewCallTimer = (TextView) findViewById(R.id.callTimer);

		mLayoutCallStatus = (LinearLayout) findViewById(R.id.linearLayoutCallStatus);
		mTextViewCallStatus = (TextView) findViewById(R.id.textViewCallStatus);

		mLayoutConnectionQuality = (LinearLayout) findViewById(R.id.linearLayoutConnectionQuality);
		mTextViewQuality = (TextView) findViewById(R.id.textViewQuality);
		mButtonConnectionDetails = (ImageButton) findViewById(R.id.buttonConnectionDetails);

		mLayoutAuthenticationToken = (LinearLayout) findViewById(R.id.linearLayoutAuthenticationToken);
		mTextViewAuthenticationToken = (TextView) findViewById(R.id.textViewAuthenticationToken);

		mLayoutUnencryptedCall = (LinearLayout) findViewById(R.id.linearLayoutUnencryptedCall);
		mButtonAcceptUnencryptedCall = (Button) findViewById(R.id.buttonAcceptUnencryptedCall);

		mButtonMicro = (ImageButton) findViewById(R.id.buttonMicro);
		mButtonSpeaker = (ImageButton) findViewById(R.id.buttonSpeaker);

		//
		// Presets
		mTextViewCallTimer.setVisibility(View.INVISIBLE);

		mLayoutCallStatus.setVisibility(View.INVISIBLE);
		mLayoutConnectionQuality.setVisibility(View.INVISIBLE);
		mLayoutAuthenticationToken.setVisibility(View.INVISIBLE);
		mLayoutUnencryptedCall.setVisibility(View.GONE);
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();
		mCommunicator.register(this, CallActivity.class);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);
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
	protected void onStop()
	{
		Log.i(LOGTAG, "onStop");
		mHandler.removeCallbacksAndMessages(null);
		mTextViewCallTimer.setVisibility(View.INVISIBLE);
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	void setCallEncryption(final boolean encrypted, final String authenticationToken, final boolean authenticationTokenVerified)
	{
		if (!encrypted) {
			mLayoutAuthenticationToken.setVisibility(View.GONE);
			mLayoutUnencryptedCall.setVisibility(View.VISIBLE);
			return;
		}
		mLayoutUnencryptedCall.setVisibility(View.GONE);

		if (authenticationTokenVerified || Util.isNullOrEmpty(authenticationToken)) {
			mLayoutAuthenticationToken.setVisibility(View.GONE);
			return;
		}

		mLayoutAuthenticationToken.setVisibility(View.VISIBLE);
		mTextViewAuthenticationToken.setText(authenticationToken);
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

		if (!Util.isNullOrEmpty(simlarCallState.getDisplayPhotoId())) {
			mImageViewContactImage.setImageURI(Uri.parse(simlarCallState.getDisplayPhotoId()));
		} else {
			mImageViewContactImage.setImageResource(R.drawable.contact_picture);
		}
		mTextViewContactName.setText(simlarCallState.getDisplayName());
		setCallEncryption(simlarCallState.isEncrypted(), simlarCallState.getAuthenticationToken(), simlarCallState.isAuthenticationTokenVerified());

		mCallStartTime = simlarCallState.getStartTime();
		if (mCallStartTime > 0) {
			startCallTimer();
		}

		if (simlarCallState.hasQuality()) {
			mTextViewQuality.setText(getString(simlarCallState.getQualityDescription()));
			mLayoutConnectionQuality.setVisibility(View.VISIBLE);
			mButtonConnectionDetails.setVisibility(View.VISIBLE);
			getString(simlarCallState.getQualityDescription());
		} else {
			mLayoutConnectionQuality.setVisibility(View.INVISIBLE);

			if (simlarCallState.hasCallStatusMessage()) {
				mLayoutCallStatus.setVisibility(View.VISIBLE);
				mTextViewCallStatus.setText(simlarCallState.getCallStatusDisplayMessage(this));
			} else if (simlarCallState.hasErrorMessage()) {
				mLayoutCallStatus.setVisibility(View.VISIBLE);
				mTextViewCallStatus.setText(simlarCallState.getErrorDisplayMessage(this, simlarCallState.getDisplayName()));
			} else {
				mLayoutCallStatus.setVisibility(View.INVISIBLE);
			}
		}

		if (simlarCallState.isTalking()) {
			setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
		}

		setButtonMicrophoneMute();
		setButtonSpeakerMute();

		if (simlarCallState.isEndedCall()) {
			if (simlarCallState.hasErrorMessage()) {
				finishDelayed(5000);
			} else {
				finish();
			}
		}
	}

	private void startCallTimer()
	{
		if (mTextViewCallTimer.getVisibility() == View.VISIBLE) {
			return;
		}
		mTextViewCallTimer.setVisibility(View.VISIBLE);

		iterateTimer();
	}

	void iterateTimer()
	{
		final String text = Util.formatMilliSeconds(SystemClock.elapsedRealtime() - mCallStartTime);
		Log.i(LOGTAG, "iterateTimer: " + text);

		mTextViewCallTimer.setText(text);

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run()
			{
				iterateTimer();
			}
		}, 1000);

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
		mButtonAcceptUnencryptedCall.setVisibility(View.INVISIBLE);
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
		setButtonMicrophoneMute();
	}

	@SuppressWarnings("unused")
	public void toggleSpeakerMuted(final View view)
	{
		mCommunicator.getService().setVolumes(mCommunicator.mService.getVolumes().toggleExternalSpeaker());
		setButtonSpeakerMute();
	}

	private void setButtonMicrophoneMute()
	{
		switch (mCommunicator.getService().getVolumes().getMicrophoneStatus()) {
		case DISABLED:
			mButtonMicro.setImageResource(R.drawable.micro_out_grey);
			mButtonMicro.setContentDescription(getString(R.string.call_activity_microphone_disabled));
			break;
		case MUTED:
			mButtonMicro.setImageResource(R.drawable.micro_off);
			mButtonMicro.setContentDescription(getString(R.string.call_activity_microphone_mute));
			break;
		case ON:
		default:
			mButtonMicro.setImageResource(R.drawable.micro_on);
			mButtonMicro.setContentDescription(getString(R.string.call_activity_microphone_on));
			break;
		}
	}

	private void setButtonSpeakerMute()
	{
		if (mCommunicator.getService().getVolumes().getExternalSpeaker()) {
			mButtonSpeaker.setImageResource(R.drawable.speaker_on);
			mButtonSpeaker.setContentDescription(getString(R.string.call_activity_loudspeaker_on));
		} else {
			mButtonSpeaker.setImageResource(R.drawable.speaker_off);
			mButtonSpeaker.setContentDescription(getString(R.string.call_activity_loudspeaker_off));
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
		final float distance = event.values[0];

		if (distance > event.sensor.getMaximumRange()) {
			Log.w(LOGTAG, "proximity sensors distance=" + distance + " out of range=" + event.sensor.getMaximumRange());
			return;
		}

		final WindowManager.LayoutParams params = getWindow().getAttributes();
		if (distance <= PROXIMITY_DISTANCE_THRESHOLD) {
			Log.i(LOGTAG, "proximity sensors distance=" + distance + " below threshold=" + PROXIMITY_DISTANCE_THRESHOLD
					+ " => dimming screen in order to disable touch events");
			params.screenBrightness = 0.1f;
		} else {
			Log.i(LOGTAG, "proximity sensors distance=" + distance + " above threshold=" + PROXIMITY_DISTANCE_THRESHOLD
					+ " => enabling touch events (no screen dimming)");
			params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
		}
		getWindow().setAttributes(params);
	}
}

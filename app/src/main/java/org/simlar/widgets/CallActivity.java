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

import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.proximityscreenlocker.ProximityScreenLocker;
import org.simlar.proximityscreenlocker.ProximityScreenLockerHelper;
import org.simlar.service.SimlarCallState;
import org.simlar.service.SimlarService;
import org.simlar.service.SimlarServiceCommunicator;
import org.simlar.utils.Util;

public final class CallActivity extends AppCompatActivity implements VolumesControlDialogFragment.Listener
{
	public static final String INTENT_EXTRA_SIMLAR_ID = "simlarId";

	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCall();
	private ProximityScreenLocker mProximityScreenLocker;
	private long mCallStartTime = -1;
	private final Handler mHandler = new Handler();
	private Runnable mCallTimer = null;
	private boolean mFinishDelayedCalled = false;
	private boolean mHideAuthenticationToken = false;

	// gui elements
	private ImageView mImageViewContactImage;
	private TextView mTextViewContactName;
	private TextView mTextViewCallStatus;
	private TextView mTextViewCallTimer;

	private LinearLayout mLayoutConnectionQuality;
	private TextView mTextViewQuality;
	private ImageButton mButtonConnectionDetails;

	private LinearLayout mLayoutVerifiedAuthenticationToken;
	private TextView mTextViewVerifiedAuthenticationToken;

	private LinearLayout mLayoutAuthenticationToken;
	private TextView mTextViewAuthenticationToken;

	private LinearLayout mLayoutCallEndReason;
	private TextView mTextViewCallEndReason;

	private ImageButton mButtonMicro;
	private ImageButton mButtonSpeaker;

	private final class SimlarServiceCommunicatorCall extends SimlarServiceCommunicator
	{
		@Override
		public void onBoundToSimlarService()
		{
			CallActivity.this.onSimlarCallStateChanged();
		}

		@Override
		public void onSimlarCallStateChanged()
		{
			CallActivity.this.onSimlarCallStateChanged();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Lg.i("onCreate");

		setContentView(R.layout.activity_call);

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mProximityScreenLocker = ProximityScreenLockerHelper.createProximityScreenLocker(this);

		mImageViewContactImage = (ImageView) findViewById(R.id.contactImage);
		mTextViewContactName = (TextView) findViewById(R.id.contactName);
		mTextViewCallStatus = (TextView) findViewById(R.id.textViewCallStatus);
		mTextViewCallTimer = (TextView) findViewById(R.id.textViewCallTimer);

		mLayoutConnectionQuality = (LinearLayout) findViewById(R.id.linearLayoutConnectionQuality);
		mTextViewQuality = (TextView) findViewById(R.id.textViewQuality);
		mButtonConnectionDetails = (ImageButton) findViewById(R.id.buttonConnectionDetails);

		mLayoutVerifiedAuthenticationToken = (LinearLayout) findViewById(R.id.linearLayoutVerifiedAuthenticationToken);
		mTextViewVerifiedAuthenticationToken = (TextView) findViewById(R.id.textViewVerifiedAuthenticationToken);

		mLayoutAuthenticationToken = (LinearLayout) findViewById(R.id.linearLayoutAuthenticationToken);
		mTextViewAuthenticationToken = (TextView) findViewById(R.id.textViewAuthenticationToken);

		mLayoutCallEndReason = (LinearLayout) findViewById(R.id.linearLayoutCallEndReason);
		mTextViewCallEndReason = (TextView) findViewById(R.id.textViewCallEndReason);

		mButtonMicro = (ImageButton) findViewById(R.id.buttonMicro);
		mButtonSpeaker = (ImageButton) findViewById(R.id.buttonSpeaker);

		//
		// Presets
		mTextViewCallTimer.setVisibility(View.INVISIBLE);

		mLayoutConnectionQuality.setVisibility(View.INVISIBLE);
		mLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
		mLayoutAuthenticationToken.setVisibility(View.GONE);
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Lg.i("onStart");

		final String simlarIdToCall = getIntent().getStringExtra(INTENT_EXTRA_SIMLAR_ID);
		getIntent().removeExtra(INTENT_EXTRA_SIMLAR_ID);
		if (!Util.isNullOrEmpty(simlarIdToCall)) {
			mCommunicator.startServiceAndRegister(this, CallActivity.class, simlarIdToCall);
		} else if (!mCommunicator.register(this, CallActivity.class)) {
			Lg.w("SimlarService is not running, starting MainActivity");
			startActivity(new Intent(this, MainActivity.class));
			finish();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Lg.i("onResume");

		mProximityScreenLocker.acquire();
	}

	@Override
	protected void onPause()
	{
		Lg.i("onPause");
		mProximityScreenLocker.release(false);
		super.onPause();
	}

	@Override
	protected void onStop()
	{
		Lg.i("onStop");
		stopCallTimer();
		mCommunicator.unregister();
		super.onStop();
	}

	@Override
	public void onDestroy()
	{
		Lg.i("onDestroy");
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	private void setCallEncryption(final String authenticationToken, final boolean authenticationTokenVerified)
	{
		if (Util.isNullOrEmpty(authenticationToken)) {
			return;
		}

		if (authenticationTokenVerified) {
			mLayoutVerifiedAuthenticationToken.setVisibility(View.VISIBLE);
			mTextViewVerifiedAuthenticationToken.setText(authenticationToken);
			mLayoutAuthenticationToken.setVisibility(View.GONE);
		} else {
			mLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
			mLayoutAuthenticationToken.setVisibility(mHideAuthenticationToken ? View.GONE : View.VISIBLE);
			mTextViewAuthenticationToken.setText(authenticationToken);
		}
	}

	private void onSimlarCallStateChanged()
	{
		if (mCommunicator.getService() == null) {
			Lg.e("ERROR: onSimlarCallStateChanged but not bound to service");
			return;
		}

		final SimlarCallState simlarCallState = mCommunicator.getService().getSimlarCallState();
		if (simlarCallState == null || simlarCallState.isEmpty()) {
			Lg.e("ERROR: onSimlarCallStateChanged simlarCallState null or empty");
			return;
		}

		Lg.d("onSimlarCallStateChanged ", simlarCallState);

		mImageViewContactImage.setImageBitmap(simlarCallState.getContactPhotoBitmap(this, R.drawable.contact_picture));
		mTextViewContactName.setText(simlarCallState.getContactName());

		mTextViewCallStatus.setText(simlarCallState.getCallStatusDisplayMessage(this));

		mCallStartTime = simlarCallState.getStartTime();
		if (mCallStartTime > 0) {
			startCallTimer();
		}

		if (simlarCallState.hasQuality()) {
			mTextViewQuality.setText(getString(simlarCallState.getQualityDescription()));
			mLayoutConnectionQuality.setVisibility(View.VISIBLE);
			mButtonConnectionDetails.setVisibility(View.VISIBLE);
			getString(simlarCallState.getQualityDescription());
		}

		setCallEncryption(simlarCallState.getAuthenticationToken(), simlarCallState.isAuthenticationTokenVerified());

		if (simlarCallState.isTalking()) {
			setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
		}

		setButtonMicrophoneMute();
		setButtonSpeakerMute();

		if (simlarCallState.isEndedCall()) {
			mLayoutConnectionQuality.setVisibility(View.INVISIBLE);
			mLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
			mLayoutAuthenticationToken.setVisibility(View.GONE);
			mLayoutCallEndReason.setVisibility(View.VISIBLE);
			mTextViewCallEndReason.setText(simlarCallState.getCallStatusDisplayMessage(this));
			stopCallTimer();
			finishDelayed(20000);
		}
	}

	private void startCallTimer()
	{
		if (mCallTimer != null) {
			return;
		}

		mCallTimer = new Runnable()
		{
			@Override
			public void run()
			{
				iterateTimer();
			}
		};

		mTextViewCallTimer.setVisibility(View.VISIBLE);

		iterateTimer();
	}

	private void iterateTimer()
	{
		final String text = Util.formatMilliSeconds(SystemClock.elapsedRealtime() - mCallStartTime);
		Lg.d("iterateTimer: ", text);

		mTextViewCallTimer.setText(text);

		if (mCallTimer != null) {
			mHandler.postDelayed(mCallTimer, 1000);
		}
	}

	private void stopCallTimer()
	{
		mHandler.removeCallbacks(mCallTimer);
		mCallTimer = null;
	}

	@SuppressWarnings("SameParameterValue")
	private void finishDelayed(final int milliSeconds)
	{
		if (mFinishDelayedCalled) {
			return;
		}

		mFinishDelayedCalled = true;

		Lg.i("finishing activity in ", milliSeconds, " ms");

		new Handler().postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				finish();
				overridePendingTransition(R.anim.fadein, R.anim.fadeout);
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
		mHideAuthenticationToken = true;
		mLayoutAuthenticationToken.setVisibility(View.GONE);
	}

	@SuppressWarnings("unused")
	public void showConnectionDetails(final View view)
	{
		startActivity(new Intent(this, ConnectionDetailsActivity.class));
	}

	@SuppressWarnings("unused")
	public void showSoundSettingsDialog(final View view)
	{
		(new VolumesControlDialogFragment()).show(getSupportFragmentManager(), VolumesControlDialogFragment.class.getCanonicalName());
	}

	@SuppressWarnings("unused")
	public void toggleMicrophoneMuted(final View view)
	{
		mCommunicator.getService().toggleMicrophoneMuted();
		setButtonMicrophoneMute();
	}

	@SuppressWarnings("unused")
	public void toggleSpeakerMuted(final View view)
	{
		mCommunicator.getService().toggleExternalSpeaker();
		setButtonSpeakerMute();
	}

	private void setButtonMicrophoneMute()
	{
		switch (mCommunicator.getService().getMicrophoneStatus()) {
		case DISABLED:
			mButtonMicro.setImageResource(R.drawable.micro_off_disabled);
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
		if (mCommunicator.getService().getExternalSpeaker()) {
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
		final SimlarService service = mCommunicator.getService();
		if (service != null) {
			service.terminateCall();
		}

		finish();
	}

	@Override
	public void onBackPressed()
	{
		// prevent switch to MainActivity
		moveTaskToBack(true);
	}

	@Override
	public int getMicrophoneVolume()
	{
		return mCommunicator.getService().getMicrophoneVolume();
	}

	@Override
	public int getSpeakerVolume()
	{
		return mCommunicator.getService().getSpeakerVolume();
	}

	@Override
	public boolean getEchoLimiter()
	{
		return mCommunicator.getService().getEchoLimiter();
	}

	@Override
	public void onMicrophoneVolumeChanged(final int progress)
	{
		mCommunicator.getService().setMicrophoneVolume(progress);
	}

	@Override
	public void onSpeakerVolumeChanged(final int progress)
	{
		mCommunicator.getService().setSpeakerVolume(progress);
	}

	@Override
	public void onEchoLimiterChanged(final boolean enabled)
	{
		mCommunicator.getService().setEchoLimiter(enabled);
	}
}

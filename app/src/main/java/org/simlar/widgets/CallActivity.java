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

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Menu;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import java.util.Set;

import org.simlar.R;
import org.simlar.databinding.ActivityCallBinding;
import org.simlar.helper.PermissionsHelper;
import org.simlar.helper.VideoState;
import org.simlar.logging.Lg;
import org.simlar.proximityscreenlocker.ProximityScreenLocker;
import org.simlar.proximityscreenlocker.ProximityScreenLockerHelper;
import org.simlar.service.AudioOutputType;
import org.simlar.service.SimlarCallState;
import org.simlar.service.SimlarService;
import org.simlar.service.SimlarServiceCommunicator;
import org.simlar.utils.Util;

public final class CallActivity extends AppCompatActivity implements VolumesControlDialogFragment.Listener, VideoFragment.Listener
{
	private static final String INTENT_EXTRA_SIMLAR_ID = "simlarId";

	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorCall();
	private ProximityScreenLocker mProximityScreenLocker = null;
	private long mCallStartTime = -1;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private Runnable mCallTimer = null;
	private boolean mFinishDelayedCalled = false;
	private boolean mHideAuthenticationToken = false;
	private AlertDialog mAlertDialogRemoteRequestedVideo = null;
	private AlertDialog mAlertDialogRemoteDeniedVideo = null;
	private AudioOutputType mCurrentAudioOutputType = AudioOutputType.PHONE;

	private ActivityCallBinding mBinding = null;

	private ConnectionDetailsDialogFragment mConnectionDetailsDialogFragment = null;
	private VideoFragment mVideoFragment = null;

	private final ActivityResultLauncher<String> mRequestPermissionLauncherRequestVideo = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(), isGranted -> {
				if (isGranted) {
					mCommunicator.getService().requestVideoUpdate(true);
				}
			});

	private final ActivityResultLauncher<String> mRequestPermissionLauncherAcceptVideo = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(), isGranted -> {
				if (isGranted) {
					startVideo();
					mCommunicator.getService().acceptVideoUpdate(true);
				} else {
					mCommunicator.getService().acceptVideoUpdate(false);
				}
			});

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

		@Override
		public void onCallConnectionDetailsChanged()
		{
			CallActivity.this.onCallConnectionDetailsChanged();
		}

		@Override
		public void onVideoStateChanged(final VideoState videoState)
		{
			CallActivity.this.onVideoStateChanged(videoState);
		}

		@Override
		public void onAudioOutputChanged(final AudioOutputType currentAudioOutputType, final Set<AudioOutputType> availableAudioOutputTypes)
		{
			CallActivity.this.onAudioOutputChanged(currentAudioOutputType, availableAudioOutputTypes);
		}
	}

	public static void createCallView(final Context context, final String simlarId)
	{
		Lg.i("starting CallActivity with simlarId=", new Lg.Anonymizer(simlarId));
		context.startActivity(new Intent(context, CallActivity.class)
				.putExtra(INTENT_EXTRA_SIMLAR_ID, simlarId)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Lg.i("onCreate");

		mBinding = ActivityCallBinding.inflate(getLayoutInflater());
		setContentView(mBinding.getRoot());

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mProximityScreenLocker = ProximityScreenLockerHelper.createProximityScreenLocker(this);

		//
		// Presets
		mBinding.textViewCallTimer.setVisibility(View.INVISIBLE);

		mBinding.linearLayoutConnectionQuality.setVisibility(View.INVISIBLE);
		mBinding.linearLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
		mBinding.linearLayoutAuthenticationToken.setVisibility(View.GONE);
		mBinding.progressBarRequestingVideo.setVisibility(View.GONE);
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

		if (mCurrentAudioOutputType == AudioOutputType.PHONE) {
			mProximityScreenLocker.acquire();
		}
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
			mBinding.linearLayoutVerifiedAuthenticationToken.setVisibility(View.VISIBLE);
			mBinding.textViewVerifiedAuthenticationToken.setText(authenticationToken);
			mBinding.linearLayoutAuthenticationToken.setVisibility(View.GONE);
		} else {
			mBinding.linearLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
			mBinding.linearLayoutAuthenticationToken.setVisibility(mHideAuthenticationToken ? View.GONE : View.VISIBLE);
			mBinding.textViewAuthenticationToken.setText(authenticationToken);
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

		mBinding.contactImage.setImageBitmap(simlarCallState.getContactPhotoBitmap(this, R.drawable.contact_picture));
		mBinding.contactName.setText(simlarCallState.getContactName());

		mBinding.textViewCallStatus.setText(simlarCallState.getCallStatusDisplayMessage(this));

		mCallStartTime = simlarCallState.getStartTime();
		if (mCallStartTime > 0) {
			startCallTimer();
		}

		if (simlarCallState.hasQuality()) {
			mBinding.textViewQuality.setText(getString(simlarCallState.getQualityDescription()));
			mBinding.linearLayoutConnectionQuality.setVisibility(View.VISIBLE);
			mBinding.buttonConnectionDetails.setVisibility(View.VISIBLE);
			getString(simlarCallState.getQualityDescription());
		}

		setCallEncryption(simlarCallState.getAuthenticationToken(), simlarCallState.isAuthenticationTokenVerified());

		if (simlarCallState.isTalking()) {
			setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
		}

		mBinding.buttonToggleVideo.setEnabled(simlarCallState.isVideoRequestPossible());

		setButtonMicrophoneMute();

		if (simlarCallState.isEndedCall()) {
			if (mAlertDialogRemoteRequestedVideo != null) {
				mAlertDialogRemoteRequestedVideo.hide();
			}
			if (mAlertDialogRemoteDeniedVideo != null) {
				mAlertDialogRemoteDeniedVideo.hide();
			}
			mBinding.linearLayoutConnectionQuality.setVisibility(View.INVISIBLE);
			mBinding.linearLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
			mBinding.linearLayoutAuthenticationToken.setVisibility(View.GONE);
			mBinding.linearLayoutCallEndReason.setVisibility(View.VISIBLE);
			mBinding.textViewCallEndReason.setText(simlarCallState.getCallStatusDisplayMessage(this));
			stopCallTimer();
			finishDelayed(20000);
		}
	}

	private void onCallConnectionDetailsChanged()
	{
		if (mConnectionDetailsDialogFragment == null) {
			return;
		}

		mConnectionDetailsDialogFragment.setCallConnectionDetails(mCommunicator.getService().getCallConnectionDetails());
	}

	private void onVideoStateChanged(final VideoState videoState)
	{
		Lg.i("onVideoStateChanged: ", videoState);

		switch (videoState) {
			case OFF, REQUESTING, REMOTE_REQUESTED, DENIED -> stopVideo();
			case ACCEPTED, INITIALIZING, PLAYING -> startVideo();
		}

		final boolean requestingVideo = VideoState.REQUESTING == videoState;
		mBinding.progressBarRequestingVideo.setVisibility(requestingVideo ? View.VISIBLE : View.GONE);
		mBinding.buttonToggleVideo.setVisibility(requestingVideo ? View.GONE : View.VISIBLE);

		switch (videoState) {
			case OFF:
			case REQUESTING:
			case ACCEPTED:
			case INITIALIZING:
				break;
			case REMOTE_REQUESTED:
				showRemoteRequestedVideoAlert();
				break;
			case DENIED:
				showRemoteDeniedVideoAlert();
				break;
			case PLAYING:
				mVideoFragment.setNowPlaying();
				break;
		}
	}

	private void showRemoteRequestedVideoAlert()
	{
		if (mAlertDialogRemoteRequestedVideo == null) {
			mAlertDialogRemoteRequestedVideo = new AlertDialog.Builder(this)
					.setTitle(R.string.call_activity_alert_accept_video_request_title)
					.setMessage(R.string.call_activity_alert_accept_video_request_text)
					.setNegativeButton(R.string.button_cancel, (dialog, id) -> acceptVideoUpdate(false))
					.setPositiveButton(R.string.button_continue, (dialog, id) -> acceptVideoUpdate(true))
					.setOnCancelListener(dialog -> acceptVideoUpdate(false))
					.create();
		}

		if (!mAlertDialogRemoteRequestedVideo.isShowing()) {
			Lg.i("remote requested video => showing alert");
			mAlertDialogRemoteRequestedVideo.show();
		}
	}

	private void showRemoteDeniedVideoAlert()
	{
		if (mAlertDialogRemoteDeniedVideo == null) {
			mAlertDialogRemoteDeniedVideo = new AlertDialog.Builder(this)
					.setMessage(R.string.call_activity_video_denied)
					.create();
		}

		if (!mAlertDialogRemoteDeniedVideo.isShowing()) {
			Lg.i("remote denied video => showing alert");
			mAlertDialogRemoteDeniedVideo.show();
		}
	}

	private void acceptVideoUpdate(final boolean accept)
	{
		if (accept) {
			if (PermissionsHelper.hasPermission(this, PermissionsHelper.Type.CAMERA)) {
				startVideo();
				mCommunicator.getService().acceptVideoUpdate(true);
			} else {
				PermissionsHelper.showRationalIfNeeded(this, PermissionsHelper.Type.CAMERA,
						mRequestPermissionLauncherAcceptVideo::launch);
			}
		} else {
			mCommunicator.getService().acceptVideoUpdate(false);
		}
	}

	@Override
	public void setVideoWindows(final TextureView videoView, final TextureView captureView)
	{
		mCommunicator.getService().setVideoWindows(videoView, captureView);
	}

	@Override
	public void destroyVideoWindows()
	{
		mCommunicator.getService().destroyVideoWindows();
	}

	@Override
	public void onVideoViewClick()
	{
		Lg.i("onVideoViewClick");

		mBinding.linearLayoutCallControlButtons.setVisibility(mBinding.linearLayoutCallControlButtons.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
	}

	@Override
	public void onCaptureViewClick()
	{
		Lg.i("onCaptureViewClick");
		mCommunicator.getService().toggleCamera();
	}

	private void startVideo()
	{
		if (mVideoFragment != null) {
			return;
		}

		Lg.i("adding video fragment");

		mVideoFragment = new VideoFragment();

		final FragmentManager fm = getSupportFragmentManager();
		fm.beginTransaction().add(R.id.layoutVideoFragmentContainer, mVideoFragment).commit();

		mProximityScreenLocker.release(false);
		if (mCurrentAudioOutputType == AudioOutputType.PHONE) {
			mCommunicator.getService().setCurrentAudioOutputType(AudioOutputType.SPEAKER);
		}
	}

	private void stopVideo()
	{
		if (mVideoFragment == null) {
			return;
		}

		Lg.i("removing video fragment");

		final FragmentManager fm = getSupportFragmentManager();
		fm.beginTransaction().remove(mVideoFragment).commit();

		mVideoFragment = null;

		if (mCurrentAudioOutputType == AudioOutputType.SPEAKER) {
			mCommunicator.getService().setCurrentAudioOutputType(AudioOutputType.PHONE);
		}

		mBinding.linearLayoutCallControlButtons.setVisibility(View.VISIBLE);
	}

	private void startCallTimer()
	{
		if (mCallTimer != null) {
			return;
		}

		mCallTimer = this::iterateTimer;

		mBinding.textViewCallTimer.setVisibility(View.VISIBLE);

		iterateTimer();
	}

	private void iterateTimer()
	{
		final String text = Util.formatMilliSeconds(SystemClock.elapsedRealtime() - mCallStartTime);
		Lg.d("iterateTimer: ", text);

		mBinding.textViewCallTimer.setText(text);

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

		mHandler.postDelayed(() -> {
			finish();
			overridePendingTransition(R.anim.fadein, R.anim.fadeout);
		}, milliSeconds);
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void verifyAuthenticationToken(final View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(true);
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void wrongAuthenticationToken(final View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(false);
		mHideAuthenticationToken = true;
		mBinding.linearLayoutAuthenticationToken.setVisibility(View.GONE);
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void showConnectionDetails(final View view)
	{
		if (mConnectionDetailsDialogFragment == null) {
			mConnectionDetailsDialogFragment = new ConnectionDetailsDialogFragment();
		}

		if (!mConnectionDetailsDialogFragment.isResumed()) {
			mConnectionDetailsDialogFragment.show(getSupportFragmentManager(), ConnectionDetailsDialogFragment.class.getCanonicalName());
		}
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void toggleVideoClicked(final View view)
	{
		if (mVideoFragment == null) {
			if (PermissionsHelper.hasPermission(this, PermissionsHelper.Type.CAMERA)) {
				mCommunicator.getService().requestVideoUpdate(true);
			} else {
				PermissionsHelper.showRationalIfNeeded(this, PermissionsHelper.Type.CAMERA,
						mRequestPermissionLauncherRequestVideo::launch);
			}
		} else {
			mCommunicator.getService().requestVideoUpdate(false);
		}
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void showSoundSettingsDialog(final View view)
	{
		new VolumesControlDialogFragment().show(getSupportFragmentManager(), VolumesControlDialogFragment.class.getCanonicalName());
	}

	private void onAudioOutputChanged(final AudioOutputType currentAudioOutput, final Set<AudioOutputType> availableAudioOutputTypes)
	{
		Lg.i("onAudioOutputChanged");
		mCurrentAudioOutputType = currentAudioOutput;

		if (currentAudioOutput == AudioOutputType.PHONE) {
			mProximityScreenLocker.acquire();
		} else {
			mProximityScreenLocker.release(false);
		}

		if (availableAudioOutputTypes.size() <= 2 && availableAudioOutputTypes.contains(AudioOutputType.SPEAKER)) {
			mBinding.buttonSpeaker.setVisibility(View.VISIBLE);
			mBinding.buttonSpeakerChoices.setVisibility(View.GONE);

			if (currentAudioOutput == AudioOutputType.SPEAKER) {
				mBinding.buttonSpeaker.setImageResource(R.drawable.speaker_on);
				mBinding.buttonSpeaker.setContentDescription(getString(R.string.call_activity_loudspeaker_on));
			} else {
				mBinding.buttonSpeaker.setImageResource(R.drawable.speaker_off);
				mBinding.buttonSpeaker.setContentDescription(getString(R.string.call_activity_loudspeaker_off));
			}
		} else {
			mBinding.buttonSpeaker.setVisibility(View.GONE);
			mBinding.buttonSpeakerChoices.setVisibility(View.VISIBLE);

			switch (currentAudioOutput) {
				case PHONE -> mBinding.buttonSpeakerChoices.setImageResource(R.drawable.audio_output_phone);
				case WIRED_HEADSET -> mBinding.buttonSpeakerChoices.setImageResource(R.drawable.audio_output_wired_headset);
				case SPEAKER -> mBinding.buttonSpeakerChoices.setImageResource(R.drawable.audio_output_speaker);
				case BLUETOOTH -> mBinding.buttonSpeakerChoices.setImageResource(R.drawable.audio_output_bluetooth);
			}

			mBinding.buttonSpeakerChoices.setOnClickListener(view -> {
				Lg.i("button showSpeakerChoices clicked");

				//noinspection ZeroLengthArrayAllocation
				final AudioOutputType[] types = availableAudioOutputTypes.toArray(new AudioOutputType[0]);
				int currentItem = 0;
				final String[] items = new String[types.length];
				for (int i = 0; i < types.length; i++) {
					items[i] = types[i].toDisplayName(this);
					if (types[i] == currentAudioOutput) {
						currentItem = i;
					}
				}

				new AlertDialog.Builder(this)
						.setSingleChoiceItems(items, currentItem, (dialog, which) -> {
							mCommunicator.getService().setCurrentAudioOutputType(types[which]);
							dialog.dismiss();
						}).create().show();
			});
		}
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

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void toggleMicrophoneMuted(final View view)
	{
		mCommunicator.getService().toggleMicrophoneMuted();
		setButtonMicrophoneMute();
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void toggleSpeakerMuted(final View view)
	{
		if (mCurrentAudioOutputType == AudioOutputType.PHONE) {
			mCommunicator.getService().setCurrentAudioOutputType(AudioOutputType.SPEAKER);
		} else if (mCurrentAudioOutputType == AudioOutputType.SPEAKER) {
			mCommunicator.getService().setCurrentAudioOutputType(AudioOutputType.PHONE);
		} else {
			Lg.e("toggleSpeakerMuted with unexpected AudioOutputType: ", mCurrentAudioOutputType);
		}
	}

	private void setButtonMicrophoneMute()
	{
		switch (mCommunicator.getService().getMicrophoneStatus()) {
			case DISABLED -> {
				mBinding.buttonMicro.setImageResource(R.drawable.micro_off_disabled);
				mBinding.buttonMicro.setContentDescription(getString(R.string.call_activity_microphone_disabled));
			}
			case MUTED -> {
				mBinding.buttonMicro.setImageResource(R.drawable.micro_off);
				mBinding.buttonMicro.setContentDescription(getString(R.string.call_activity_microphone_mute));
			}
			case ON -> {
				mBinding.buttonMicro.setImageResource(R.drawable.micro_on);
				mBinding.buttonMicro.setContentDescription(getString(R.string.call_activity_microphone_on));
			}
		}
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
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
}

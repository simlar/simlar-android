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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import java.util.Set;

import org.simlar.R;
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

	// gui elements
	private ImageView mImageViewContactImage = null;
	private TextView mTextViewContactName = null;
	private TextView mTextViewCallStatus = null;
	private TextView mTextViewCallTimer = null;

	private LinearLayout mLayoutConnectionQuality = null;
	private TextView mTextViewQuality = null;
	private ImageButton mButtonConnectionDetails = null;

	private LinearLayout mLayoutVerifiedAuthenticationToken = null;
	private TextView mTextViewVerifiedAuthenticationToken = null;

	private LinearLayout mLayoutAuthenticationToken = null;
	private TextView mTextViewAuthenticationToken = null;

	private LinearLayout mLayoutCallEndReason = null;
	private TextView mTextViewCallEndReason = null;

	private LinearLayout mLayoutCallControlButtons = null;
	private ProgressBar mProgressBarRequestingVideo = null;
	private ImageButton mButtonToggleVideo = null;
	private ImageButton mButtonMicro = null;
	private ImageButton mButtonSpeaker = null;
	private ImageButton mButtonSpeakerChoices = null;

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

		setContentView(R.layout.activity_call);

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mProximityScreenLocker = ProximityScreenLockerHelper.createProximityScreenLocker(this);

		mImageViewContactImage = findViewById(R.id.contactImage);
		mTextViewContactName = findViewById(R.id.contactName);
		mTextViewCallStatus = findViewById(R.id.textViewCallStatus);
		mTextViewCallTimer = findViewById(R.id.textViewCallTimer);

		mLayoutConnectionQuality = findViewById(R.id.linearLayoutConnectionQuality);
		mTextViewQuality = findViewById(R.id.textViewQuality);
		mButtonConnectionDetails = findViewById(R.id.buttonConnectionDetails);

		mLayoutVerifiedAuthenticationToken = findViewById(R.id.linearLayoutVerifiedAuthenticationToken);
		mTextViewVerifiedAuthenticationToken = findViewById(R.id.textViewVerifiedAuthenticationToken);

		mLayoutAuthenticationToken = findViewById(R.id.linearLayoutAuthenticationToken);
		mTextViewAuthenticationToken = findViewById(R.id.textViewAuthenticationToken);

		mLayoutCallEndReason = findViewById(R.id.linearLayoutCallEndReason);
		mTextViewCallEndReason = findViewById(R.id.textViewCallEndReason);

		mLayoutCallControlButtons = findViewById(R.id.linearLayoutCallControlButtons);
		mProgressBarRequestingVideo = findViewById(R.id.progressBarRequestingVideo);
		mButtonToggleVideo = findViewById(R.id.buttonToggleVideo);
		mButtonMicro = findViewById(R.id.buttonMicro);
		mButtonSpeaker = findViewById(R.id.buttonSpeaker);
		mButtonSpeakerChoices = findViewById(R.id.buttonSpeakerChoices);

		//
		// Presets
		mTextViewCallTimer.setVisibility(View.INVISIBLE);

		mLayoutConnectionQuality.setVisibility(View.INVISIBLE);
		mLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
		mLayoutAuthenticationToken.setVisibility(View.GONE);
		mProgressBarRequestingVideo.setVisibility(View.GONE);
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

		mButtonToggleVideo.setEnabled(simlarCallState.isVideoRequestPossible());

		setButtonMicrophoneMute();

		if (simlarCallState.isEndedCall()) {
			if (mAlertDialogRemoteRequestedVideo != null) {
				mAlertDialogRemoteRequestedVideo.hide();
			}
			if (mAlertDialogRemoteDeniedVideo != null) {
				mAlertDialogRemoteDeniedVideo.hide();
			}
			mLayoutConnectionQuality.setVisibility(View.INVISIBLE);
			mLayoutVerifiedAuthenticationToken.setVisibility(View.GONE);
			mLayoutAuthenticationToken.setVisibility(View.GONE);
			mLayoutCallEndReason.setVisibility(View.VISIBLE);
			mTextViewCallEndReason.setText(simlarCallState.getCallStatusDisplayMessage(this));
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
		case OFF:
		case REQUESTING:
		case REMOTE_REQUESTED:
		case DENIED:
			stopVideo();
			break;
		case ACCEPTED:
		case INITIALIZING:
		case PLAYING:
			startVideo();
			break;
		}

		final boolean requestingVideo = VideoState.REQUESTING == videoState;
		mProgressBarRequestingVideo.setVisibility(requestingVideo ? View.VISIBLE : View.GONE);
		mButtonToggleVideo.setVisibility(requestingVideo ? View.GONE : View.VISIBLE);

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

		mLayoutCallControlButtons.setVisibility(mLayoutCallControlButtons.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
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

		mLayoutCallControlButtons.setVisibility(View.VISIBLE);
	}

	private void startCallTimer()
	{
		if (mCallTimer != null) {
			return;
		}

		mCallTimer = this::iterateTimer;

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

		mHandler.postDelayed(() -> {
			finish();
			overridePendingTransition(R.anim.fadein, R.anim.fadeout);
		}, milliSeconds);
	}

	@SuppressWarnings({"unused", "RedundantSuppression"})
	public void verifyAuthenticationToken(final View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(true);
	}

	@SuppressWarnings({"unused", "RedundantSuppression"})
	public void wrongAuthenticationToken(final View view)
	{
		mCommunicator.getService().verifyAuthenticationTokenOfCurrentCall(false);
		mHideAuthenticationToken = true;
		mLayoutAuthenticationToken.setVisibility(View.GONE);
	}

	@SuppressWarnings({"unused", "RedundantSuppression"})
	public void showConnectionDetails(final View view)
	{
		if (mConnectionDetailsDialogFragment == null) {
			mConnectionDetailsDialogFragment = new ConnectionDetailsDialogFragment();
		}

		if (!mConnectionDetailsDialogFragment.isResumed()) {
			mConnectionDetailsDialogFragment.show(getSupportFragmentManager(), ConnectionDetailsDialogFragment.class.getCanonicalName());
		}
	}

	@SuppressWarnings({"unused", "RedundantSuppression"})
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

	@SuppressWarnings({"unused", "RedundantSuppression"})
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
			mButtonSpeaker.setVisibility(View.VISIBLE);
			mButtonSpeakerChoices.setVisibility(View.GONE);

			if (currentAudioOutput == AudioOutputType.SPEAKER) {
				mButtonSpeaker.setImageResource(R.drawable.speaker_on);
				mButtonSpeaker.setContentDescription(getString(R.string.call_activity_loudspeaker_on));
			} else {
				mButtonSpeaker.setImageResource(R.drawable.speaker_off);
				mButtonSpeaker.setContentDescription(getString(R.string.call_activity_loudspeaker_off));
			}
		} else {
			mButtonSpeaker.setVisibility(View.GONE);
			mButtonSpeakerChoices.setVisibility(View.VISIBLE);

			switch (currentAudioOutput) {
			case PHONE:
				mButtonSpeakerChoices.setImageResource(R.drawable.audio_output_phone);
				break;
			case WIRED_HEADSET:
				mButtonSpeakerChoices.setImageResource(R.drawable.audio_output_wired_headset);
				break;
			case SPEAKER:
				mButtonSpeakerChoices.setImageResource(R.drawable.audio_output_speaker);
				break;
			case BLUETOOTH:
				mButtonSpeakerChoices.setImageResource(R.drawable.audio_output_bluetooth);
				break;
			}

			mButtonSpeakerChoices.setOnClickListener(view -> {
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

	@SuppressWarnings({"unused", "RedundantSuppression"})
	public void toggleMicrophoneMuted(final View view)
	{
		mCommunicator.getService().toggleMicrophoneMuted();
		setButtonMicrophoneMute();
	}

	@SuppressWarnings({"unused", "RedundantSuppression"})
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

	@SuppressWarnings({"unused", "RedundantSuppression"})
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

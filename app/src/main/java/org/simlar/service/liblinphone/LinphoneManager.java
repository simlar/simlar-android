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

package org.simlar.service.liblinphone;

import android.content.Context;
import android.text.TextUtils;
import android.view.TextureView;

import androidx.annotation.NonNull;

import java.util.Set;

import org.linphone.core.Account;
import org.linphone.core.Address;
import org.linphone.core.AudioDevice;
import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ErrorInfo;
import org.linphone.core.IceState;
import org.linphone.core.PayloadType;
import org.linphone.core.Reason;
import org.linphone.core.RegistrationState;
import org.linphone.core.StreamType;

import org.simlar.R;
import org.simlar.helper.CallEndReason;
import org.simlar.helper.FileHelper;
import org.simlar.helper.FileHelper.NotInitedException;
import org.simlar.helper.NetworkQuality;
import org.simlar.helper.VideoState;
import org.simlar.helper.Volumes;
import org.simlar.helper.Volumes.MicrophoneStatus;
import org.simlar.logging.Lg;
import org.simlar.service.AudioOutputType;
import org.simlar.utils.Util;

public final class LinphoneManager extends CoreListenerStub
{
	private VideoState mVideoState = VideoState.OFF;
	private final LinphoneHandler mLinphoneHandler = new LinphoneHandler();

	private final LinphoneManagerListener mListener;
	private RegistrationState mRegistrationState = RegistrationState.None;
	private Volumes mVolumes = new Volumes();
	private final Context mContext;

	public LinphoneManager(final LinphoneManagerListener listener, final Context context)
	{
		mListener = listener;
		mListener.onCallStateChanged("", Call.State.Idle, null);
		mContext = context;
	}

	public void finish()
	{
		mLinphoneHandler.destroy(this);
	}

	public void register(final String mySimlarId, final String password)
	{
		Lg.i("register");

		try {
			final String linphoneInitialConfigFile = FileHelper.getLinphoneInitialConfigFile();
			final String rootCaFile = FileHelper.getRootCaFileName();
			final String zrtpSecretsCacheFile = FileHelper.getZrtpSecretsCacheFileName();
			final String ringbackSoundFile = FileHelper.getRingbackSoundFile();
			final String pauseSoundFile = FileHelper.getPauseSoundFile();
			final Volumes volumes = mVolumes;

			if (mLinphoneHandler.isInitialized()) {
				mLinphoneHandler.unregister();
			} else {
				mLinphoneHandler.initialize(this, mContext, linphoneInitialConfigFile, rootCaFile,
						zrtpSecretsCacheFile, ringbackSoundFile, pauseSoundFile);
				mLinphoneHandler.setVolumes(volumes);
			}
			mLinphoneHandler.setCredentials(mySimlarId, password);
		} catch (final NotInitedException e) {
			Lg.ex(e, "PreferencesHelper.NotInitedException");
		}
	}

	public void unregister()
	{
		mLinphoneHandler.unregister();
	}

	public void refreshRegisters()
	{
		mLinphoneHandler.refreshRegisters();
	}

	public void call(final String number)
	{
		if (Util.isNullOrEmpty(number)) {
			Lg.e("call: empty number aborting");
			return;
		}

		if (RegistrationState.Ok != mRegistrationState) {
			Lg.i("call: not registered");
			return;
		}

		mLinphoneHandler.call(number);
	}

	public void pickUp()
	{
		mLinphoneHandler.pickUp();
	}

	public void terminateAllCalls()
	{
		mLinphoneHandler.terminateAllCalls();
	}

	public void verifyAuthenticationToken(final String token, final boolean verified)
	{
		mLinphoneHandler.verifyAuthenticationToken(token, verified);
	}

	public void pauseAllCalls()
	{
		mLinphoneHandler.pauseAllCalls();
	}

	public void resumeCall()
	{
		mLinphoneHandler.resumeCall();
	}

	public void setVolumes(final Volumes volumes)
	{
		if (volumes == null) {
			Lg.e("volumes is null");
			return;
		}

		mVolumes = volumes;
		mLinphoneHandler.setVolumes(volumes);
	}

	public void setMicrophoneStatus(final MicrophoneStatus microphoneStatus)
	{
		if (mVolumes == null) {
			Lg.e("volumes not initialized");
			return;
		}

		setVolumes(mVolumes.setMicrophoneStatus(microphoneStatus));
	}

	public Volumes getVolumes()
	{
		return mVolumes;
	}

	public void setCurrentAudioOutputType(final AudioOutputType type)
	{
		mLinphoneHandler.setCurrentAudioOutputType(type);
	}

	public void requestVideoUpdate(final boolean enable)
	{
		if (enable) {
			updateVideoState(VideoState.REQUESTING);
		}
		mLinphoneHandler.requestVideoUpdate(enable);
	}

	private void updateVideoState(final VideoState videoState)
	{
		if (mVideoState == videoState) {
			return;
		}

		Lg.i("updating video state: ", mVideoState, " => ", videoState);
		mVideoState = videoState;

		mListener.onVideoStateChanged(videoState);
	}

	public void acceptVideoUpdate(final boolean accept)
	{
		mLinphoneHandler.acceptVideoUpdate(accept);
	}

	public void setVideoWindows(final TextureView videoView, final TextureView captureView)
	{
		if (videoView == null) {
			Lg.e("setVideoWindows: videoView is null => aborting");
			return;
		}

		if (captureView == null) {
			Lg.e("setVideoWindows: captureView is null => aborting");
			return;
		}

		setVideoWindow(videoView);
		setVideoPreviewWindow(captureView);
	}

	public void destroyVideoWindows()
	{
		Lg.i("destroyVideoWindows: disabling video windows");
		setVideoWindow(null);
		setVideoPreviewWindow(null);
	}

	private void setVideoWindow(final Object videoWindow)
	{
		mLinphoneHandler.setNativeVideoWindowId(videoWindow);
	}

	private void setVideoPreviewWindow(final Object videoPreviewWindow)
	{
		mLinphoneHandler.setVideoPreviewWindow(videoPreviewWindow);
	}

	public void toggleCamera()
	{
		mLinphoneHandler.toggleCamera();
	}

	@Lg.Anonymize
	private static class CallLogger
	{
		private final Call mCall;

		CallLogger(final Call call)
		{
			mCall = call;
		}

		@NonNull
		@Override
		public final String toString()
		{
			return getNumber(mCall);
		}
	}

	private static String getNumber(final Call call)
	{
		if (call == null) {
			return "";
		}

		return call.getRemoteAddress().asStringUriOnly().split("@")[0].replaceFirst("sip:", "");
	}

	//
	// CoreListener overloaded member functions
	//
	@Override
	public void onAccountRegistrationStateChanged(@NonNull final Core core, @NonNull final Account account, final RegistrationState state, @NonNull final String message)
	{
		final Address address = account.getParams().getIdentityAddress();
		final String identity = address == null ? "" : address.asString();

		if (Util.equals(mRegistrationState, state)) {
			Lg.v("registration state for ", new Lg.Anonymizer(identity), " not changed: state=", state, " message=", message);
			return;
		}

		Lg.i("registration state for ", new Lg.Anonymizer(identity), " changed: ", state, " ", message);
		mRegistrationState = state;

		mListener.onRegistrationStateChanged(state);
	}

	private Call.State fixCallState(final Call.State state)
	{
		if (Call.State.Released == state || Call.State.Error == state) {
			if (mLinphoneHandler.hasNoCurrentCalls()) {
				Lg.i("fixCallState: ", state, " -> ", Call.State.End);
				return Call.State.End;
			}
		}

		return state;
	}

	private VideoState createVideoState(final Call.State state, final Call call)
	{
		final boolean localVideo = call.getCurrentParams().isVideoEnabled();
		final boolean remoteVideo = call.getRemoteParams() != null && call.getRemoteParams().isVideoEnabled();

		Lg.i("creating videoState based on localVideo= ", localVideo, " remoteVideo=", remoteVideo);

		if (state != Call.State.End && localVideo && remoteVideo) {
			return mVideoState == VideoState.PLAYING ? VideoState.PLAYING : VideoState.INITIALIZING;
		}

		if (!localVideo && remoteVideo) {
			if (Call.State.UpdatedByRemote == state) {
				return VideoState.REMOTE_REQUESTED;
			}

			if (mVideoState == VideoState.REQUESTING && Call.State.StreamsRunning == state) {
				return VideoState.ACCEPTED;
			}
		}

		if (!remoteVideo && Call.State.StreamsRunning == state && mVideoState == VideoState.REQUESTING) {
			return VideoState.DENIED;
		}

		if (mVideoState == VideoState.REQUESTING) {
			return VideoState.REQUESTING;
		}

		return VideoState.OFF;
	}

	@Override
	public void onCallStateChanged(@NonNull final Core lc, @NonNull final Call call, final Call.State state, @NonNull final String message)
	{
		final String number = getNumber(call);
		final Call.State fixedState = fixCallState(state);
		final VideoState videoState = createVideoState(fixedState, call);

		final ErrorInfo errorInfo = call.getErrorInfo();
		final Reason reason = errorInfo.getReason();
		final CallEndReason callEndReason = CallEndReason.fromReason(reason);

		Lg.i("onCallStateChanged changed state=", fixedState, " number=", new CallLogger(call), " message=", message, " videoState=", videoState, " callEndReason=", callEndReason, "(", reason, ")");

		if (videoState == VideoState.REMOTE_REQUESTED) {
			Lg.i("remote requested video");
			/// NOTE: this needs to happen directly, posting to linphone thread might take too long
			LinphoneHandler.preventAutoAnswer(call);
		}

		updateVideoState(videoState);
		mListener.onCallStateChanged(number, fixedState, callEndReason);
	}

	@Override
	public void onCallStatsUpdated(@NonNull final Core lc, @NonNull final Call call, final CallStats statsDoNotUse)
	{
		final StreamType type = statsDoNotUse.getType();
		if (type != StreamType.Audio && type != StreamType.Video) {
			Lg.e("onCallStatsUpdated with unexpected type: ", type);
			return;
		}

		final CallStats stats = call.getStats(type);
		if (stats == null) {
			Lg.e("onCallStatsUpdated with no CallStats for type: ", type);
			return;
		}

		final int upload = getBandwidth(stats.getUploadBandwidth());
		final int download = getBandwidth(stats.getDownloadBandwidth());
		final String iceState = getIceStateUiString(stats.getIceState());
		final int jitter = Math.round((stats.getReceiverInterarrivalJitter() + stats.getSenderInterarrivalJitter()) * 1000.0f);
		final int packetLoss = Math.round((stats.getReceiverLossRate() + stats.getSenderLossRate()) / 2.0f * 10.0f); // sum of up and down stream loss in per mille
		final long latePackets = stats.getLatePacketsCumulativeNumber();
		final int roundTripDelay = Math.round(stats.getRoundTripDelay() * 1000.0f);
		final String codec = getCodec(call, type);
		final int duration = call.getDuration();
		final String encryptionDescription = stats.getZrtpKeyAgreementAlgo() + ' ' + stats.getZrtpHashAlgo() + ' ' + stats.getZrtpCipherAlgo();

		// set quality to unusable if up or download bandwidth is zero
		final float quality = upload > 0 && download > 0 ? call.getCurrentQuality() : 0;

		Lg.d("onCallStatsUpdated: number=", new CallLogger(call),
				" type=", type,
				" quality=", quality,
				" upload=", upload,
				" download=", download,
				" iceState=", iceState,
				" jitter=", jitter,
				" loss=", packetLoss,
				" latePackets=", latePackets,
				" roundTripDelay=", roundTripDelay,
				" codec=", codec,
				" encryption=", encryptionDescription,
				" duration=", duration);

		Lg.i("onCallStatsUpdated: ZrtpAuthTagAlgo=", stats.getZrtpAuthTagAlgo(),
				" ZrtpCipherAlgo=", stats.getZrtpCipherAlgo(),
				" ZrtpHashAlgo=", stats.getZrtpHashAlgo(),
				" ZrtpSasAlgo=", stats.getZrtpSasAlgo(),
				" ZrtpKeyAgreementAlgo ", stats.getZrtpKeyAgreementAlgo());

		if (type == StreamType.Video) {
			if (download > 0 && mVideoState == VideoState.INITIALIZING) {
				Lg.i("detect video playing based on video download bandwidth: ", download);
				updateVideoState(VideoState.PLAYING);
			}
		} else {
			mListener.onCallStatsChanged(NetworkQuality.fromFloat(quality), duration, codec, iceState, upload, download,
					jitter, packetLoss, latePackets, roundTripDelay, encryptionDescription);
		}
	}

	private String getIceStateUiString(final IceState iceState)
	{
		if (iceState == null) {
			return mContext.getString(R.string.linphone_ice_state_none);
		}

		return switch (iceState) {
			case NotActivated -> mContext.getString(R.string.linphone_ice_state_not_activated);
			case Failed -> mContext.getString(R.string.linphone_ice_state_failed);
			case InProgress -> mContext.getString(R.string.linphone_ice_state_in_progress);
			case HostConnection -> mContext.getString(R.string.linphone_ice_state_host_connection);
			case ReflexiveConnection -> mContext.getString(R.string.linphone_ice_state_reflexive_connection);
			case RelayConnection -> mContext.getString(R.string.linphone_ice_state_relay_connection);
		};
	}

	private static int getBandwidth(final float bandwidth)
	{
		return Math.round(bandwidth / 8.0f * 10.0f); // download bandwidth in 100 Bytes / second
	}

	private static String getCodec(final Call call, final StreamType type)
	{
		return getCodec(getPayload(call, type));
	}

	private static String getCodec(final PayloadType payloadType)
	{
		return payloadType == null ? null :
				payloadType.getMimeType() + ' ' + payloadType.getClockRate() / 1000;
	}

	private static PayloadType getPayload(final Call call, final StreamType type)
	{
		final CallParams params = call.getCurrentParams();

		return switch (type) {
			case Audio -> params.getUsedAudioPayloadType();
			case Video -> params.getUsedVideoPayloadType();
			case Text -> params.getUsedTextPayloadType();
			case Unknown -> null;
		};
	}

	@Override
	public void onCallEncryptionChanged(@NonNull final Core lc, final Call call, final boolean encrypted, final String authenticationToken)
	{
		final boolean isTokenVerified = call.getAuthenticationTokenVerified();

		Lg.i("onCallEncryptionChanged number=", new CallLogger(call), " encrypted=", encrypted,
				" authenticationToken=", authenticationToken);

		if (!encrypted) {
			Lg.e("unencrypted call: number=", new CallLogger(call), " with UserAgent ", call.getRemoteUserAgent());
		}

		if (encrypted && mVideoState == VideoState.INITIALIZING) {
			Lg.i("video encrypted");
			updateVideoState(VideoState.PLAYING);
		}

		mListener.onCallEncryptionChanged(authenticationToken, isTokenVerified);
	}

	@Override
	public void onAudioDevicesListUpdated(@NonNull final Core core)
	{
		final Set<AudioOutputType> availableAudioOutputTypes = mLinphoneHandler.getAvailableAudioOutputTypes();
		final AudioOutputType currentAudioOutputType = mLinphoneHandler.getCurrentAudioOutputType();
		Lg.i("onAudioDevicesListUpdated: ", TextUtils.join(", ", availableAudioOutputTypes), " current type=", currentAudioOutputType);

		mListener.onAudioOutputChanged(currentAudioOutputType, availableAudioOutputTypes);

		if (currentAudioOutputType != AudioOutputType.WIRED_HEADSET && availableAudioOutputTypes.contains(AudioOutputType.WIRED_HEADSET)) {
			mLinphoneHandler.setCurrentAudioOutputType(AudioOutputType.WIRED_HEADSET);
		} else if (currentAudioOutputType != AudioOutputType.BLUETOOTH && availableAudioOutputTypes.contains(AudioOutputType.BLUETOOTH)) {
			mLinphoneHandler.setCurrentAudioOutputType(AudioOutputType.BLUETOOTH);
		}
	}

	@Override
	public void onAudioDeviceChanged(@NonNull final Core core, @NonNull final AudioDevice audioDevice)
	{
		Lg.i("onAudioDeviceChanged: id=", audioDevice.getId(), " type=", audioDevice.getType(), " name=", audioDevice.getDeviceName());
		mListener.onAudioOutputChanged(mLinphoneHandler.getCurrentAudioOutputType(), mLinphoneHandler.getAvailableAudioOutputTypes());
	}
}

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
import androidx.annotation.Nullable;

import java.util.Set;

import org.linphone.core.Account;
import org.linphone.core.Address;
import org.linphone.core.AudioDevice;
import org.linphone.core.AuthInfo;
import org.linphone.core.AuthMethod;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Conference;
import org.linphone.core.ConferenceInfo;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.Core.LogCollectionUploadState;
import org.linphone.core.CoreListener;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.ErrorInfo;
import org.linphone.core.Event;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.GlobalState;
import org.linphone.core.IceState;
import org.linphone.core.InfoMessage;
import org.linphone.core.PayloadType;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.Reason;
import org.linphone.core.RegistrationState;
import org.linphone.core.StreamType;
import org.linphone.core.SubscriptionState;
import org.linphone.core.VersionUpdateCheckResult;

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

public final class LinphoneManager implements CoreListener
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

	@Lg.Anonymize
	private static class FriendLogger
	{
		private final Friend mFriend;

		FriendLogger(final Friend friend)
		{
			mFriend = friend;
		}

		@NonNull
		@Override
		public final String toString()
		{
			if (mFriend == null || mFriend.getAddress() == null) {
				return "";
			}

			return mFriend.getAddress().asString();
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
	@SuppressWarnings("deprecation")
	@Override
	public void onRegistrationStateChanged(@NonNull final Core lc, @NonNull final ProxyConfig cfg, final RegistrationState state, @NonNull final String message)
	{
	}

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
	public void onMessagesReceived(@NonNull final Core core, @NonNull final ChatRoom chatRoom, @NonNull final ChatMessage[] messages)
	{
		Lg.w("onMessagesReceived: ", TextUtils.join(", ", messages));
	}

	@Override
	public void onMessageReceived(@NonNull final Core lc, @NonNull final ChatRoom cr, @NonNull final ChatMessage message)
	{
		Lg.i("onMessageReceived message=", message);
	}

	@Override
	public void onEcCalibrationResult(@NonNull final Core core, final EcCalibratorStatus ecCalibratorStatus, final int i)
	{
		Lg.w("onEcCalibrationResult: ecCalibratorStatus=", ecCalibratorStatus, " i=", i);
	}

	@Override
	public void onSubscribeReceived(@NonNull final Core core, @NonNull final Event event, @NonNull final String s, @NonNull final Content content)
	{
		Lg.w("onSubscribeReceived: event=", event, " s=", s, " content=", content);
	}

	@Override
	public void onGlobalStateChanged(@NonNull final Core lc, final GlobalState state, @NonNull final String message)
	{
		Lg.i("onGlobalStateChanged state=", state, " message=", message);
	}

	@Override
	public void onNewSubscriptionRequested(@NonNull final Core lc, @NonNull final Friend lf, @NonNull final String url)
	{
		Lg.w("[", new FriendLogger(lf), "] wants to see your presence status => always accepting");
	}

	@Override
	public void onNotifyPresenceReceived(@NonNull final Core lc, @NonNull final Friend lf)
	{
		Lg.w("presence received: username=", new FriendLogger(lf));
	}

	@Override
	public void onCallIdUpdated(@NonNull final Core core, @NonNull final String previousCallId, @NonNull final String currentCallId)
	{
		Lg.w("onCallIdUpdated: previousCallId=", previousCallId, " currentCallId=", currentCallId);
	}

	@Override
	public void onEcCalibrationAudioInit(@NonNull final Core core)
	{
		Lg.w("onEcCalibrationAudioInit");
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

		switch (iceState) {
		case NotActivated:
			return mContext.getString(R.string.linphone_ice_state_not_activated);
		case Failed:
			return mContext.getString(R.string.linphone_ice_state_failed);
		case InProgress:
			return mContext.getString(R.string.linphone_ice_state_in_progress);
		case HostConnection:
			return mContext.getString(R.string.linphone_ice_state_host_connection);
		case ReflexiveConnection:
			return mContext.getString(R.string.linphone_ice_state_reflexive_connection);
		case RelayConnection:
			return mContext.getString(R.string.linphone_ice_state_relay_connection);
		default:
			return mContext.getString(R.string.linphone_ice_state_unknown);
		}
	}

	@Override
	public void onFirstCallStarted(@NonNull final Core core)
	{
		Lg.w("onFirstCallStarted");
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

		switch (type) {
		case Audio:
			return params.getUsedAudioPayloadType();
		case Video:
			return params.getUsedVideoPayloadType();
		case Text:
			return params.getUsedTextPayloadType();
		case Unknown:
		default:
			Lg.e("unknown StreamType: ", type);
			return null;
		}
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
	public void onDtmfReceived(@NonNull final Core lc, @NonNull final Call call, final int dtmf)
	{
		Lg.w("onDtmfReceived number=", new CallLogger(call), " dtmf=", dtmf);
	}

	@Override
	public void onConferenceInfoReceived(@NonNull final Core core, @NonNull final ConferenceInfo conferenceInfo)
	{
		Lg.w("onConferenceInfoReceived: ", conferenceInfo);
	}

	@Override
	public void onNotifySent(@NonNull final Core core, @NonNull final Event linphoneEvent, @NonNull final Content body)
	{
		Lg.w("onNotifySent linphoneEvent=", linphoneEvent, " body=", body);
	}

	@Override
	public void onChatRoomEphemeralMessageDeleted(@NonNull final Core lc, @NonNull final ChatRoom chatRoom)
	{
		Lg.w("onChatRoomEphemeralMessageDeleted chatRoom=", chatRoom);
	}

	@Override
	public void onMessageSent(@NonNull final Core core, @NonNull final ChatRoom chatRoom, @NonNull final ChatMessage chatMessage)
	{
		Lg.w("onMessageSent chatRoom=", chatRoom, " chatMessage=", chatMessage);
	}

	@Override
	public void onTransferStateChanged(@NonNull final Core lc, @NonNull final Call call, final Call.State state)
	{
		Lg.w("onTransferStateChanged number=", new CallLogger(call), " State=", state);
	}

	@Override
	public void onInfoReceived(@NonNull final Core lc, @NonNull final Call call, final InfoMessage info)
	{
		Lg.w("onInfoReceived number=", new CallLogger(call), " InfoMessage=", info.getContent());
	}

	@Override
	public void onChatRoomRead(@NonNull final Core core, @NonNull final ChatRoom chatRoom)
	{
		Lg.w("onChatRoomRead chatRoom=", chatRoom);
	}

	@Override
	public void onSubscriptionStateChanged(@NonNull final Core lc, final Event ev, final SubscriptionState state)
	{
		Lg.w("onSubscriptionStateChanged ev=", ev.getName(), " SubscriptionState=", state);
	}

	@Override
	public void onConferenceStateChanged(@NonNull final Core core, @NonNull final Conference conference, final Conference.State state)
	{
		Lg.w("onConferenceStateChanged: conference=", conference, " state=", state);
	}

	@Override
	public void onCallLogUpdated(@NonNull final Core core, @NonNull final CallLog callLog)
	{
		Lg.i("onCallLogUpdated: callLog=", callLog.getStatus());
	}

	@Override
	public void onIsComposingReceived(@NonNull final Core lc, final ChatRoom cr)
	{
		Lg.w("onIsComposingReceived PeerAddress=", cr.getPeerAddress());
	}

	@Override
	public void onMessageReceivedUnableDecrypt(@NonNull final Core core, @NonNull final ChatRoom chatRoom, @NonNull final ChatMessage chatMessage)
	{
		Lg.w("onMessageReceivedUnableDecrypt: chatRoom=", chatRoom, " chatMessage=", chatMessage);
	}

	@Override
	public void onConfiguringStatus(@NonNull final Core lc, final ConfiguringState state, final String message)
	{
		if (state == ConfiguringState.Skipped) {
			return;
		}
		Lg.w("onConfiguringStatus remoteProvisioningState=", state, " message=", message);
	}

	@Override
	public void onCallCreated(@NonNull final Core core, @NonNull final Call call)
	{
		Lg.i("onCallCreated; call=", new CallLogger(call));
	}

	@Override
	public void onPushNotificationReceived(@NonNull final Core core, @Nullable final String payload)
	{
		Lg.w("onPushNotificationReceived: ", payload);
	}

	@Override
	public void onPublishStateChanged(@NonNull final Core core, @NonNull final Event event, final PublishState publishState)
	{
		Lg.w("onPublishStateChanged: event=", event, " publishState=", publishState);
	}

	@Override
	public void onCallGoclearAckSent(@NonNull final Core core, @NonNull final Call call)
	{
		Lg.w("onCallGoClearAckSent number=", new CallLogger(call));
	}

	@Override
	public void onLogCollectionUploadProgressIndication(@NonNull final Core lc, final int offset, final int total)
	{
		Lg.w("onLogCollectionUploadProgressIndication: offset=", offset, " total=", total);
	}

	@Override
	public void onChatRoomSubjectChanged(@NonNull final Core core, @NonNull final ChatRoom chatRoom)
	{
		Lg.w("onChatRoomSubjectChanged chatRoom=", chatRoom);
	}

	@Override
	public void onVersionUpdateCheckResultReceived(@NonNull final Core core, @NonNull final VersionUpdateCheckResult versionUpdateCheckResult, final String s, final String s1)
	{
		Lg.w("onVersionUpdateCheckResultReceived: versionUpdateCheckResult=", versionUpdateCheckResult, " s=", s, " s1=", s);
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

	@Override
	public void onEcCalibrationAudioUninit(@NonNull final Core core)
	{
		Lg.w("onEcCalibrationAudioUninit");
	}

	@Override
	public void onLogCollectionUploadStateChanged(@NonNull final Core lc, final LogCollectionUploadState state, @NonNull final String info)
	{
		Lg.w("onLogCollectionUploadStateChanged: state=", state, " info=", info);
	}

	@Override
	public void onFriendListCreated(@NonNull final Core lc, @NonNull final FriendList linphoneFriendList)
	{
		Lg.w("onFriendListCreated: linphoneFriendList=", linphoneFriendList);
	}

	@Override
	public void onFriendListRemoved(@NonNull final Core lc, @NonNull final FriendList linphoneFriendList)
	{
		Lg.w("onFriendListRemoved: linphoneFriendList=", linphoneFriendList);
	}

	@Override
	public void onLastCallEnded(@NonNull final Core core)
	{
		Lg.w("onLastCallEnded");
	}

	@Override
	public void onReferReceived(@NonNull final Core core, @NonNull final String s)
	{
		Lg.w("onReferReceived: ", s);
	}

	@Override
	public void onQrcodeFound(@NonNull final Core core, final String s)
	{
		Lg.w("onQrcodeFound: ", s);
	}

	@Override
	public void onImeeUserRegistration(@NonNull final Core core, final boolean status, @NonNull final String userId, @NonNull final String info)
	{
		Lg.w("onImeeUserRegistration: status=", Boolean.toString(status), " userId=", userId, " info=", info);
	}

	@Override
	public void onNetworkReachable(@NonNull final Core linphoneCore, final boolean b)
	{
		Lg.i("onNetworkReachable reachable=", Boolean.toString(b));
	}

	@Override
	public void onNotifyReceived(@NonNull final Core core, @NonNull final Event event, @NonNull final String s, @NonNull final Content content)
	{
		Lg.w("onNotifyReceived: event=", event, " s=", s, " content=", content);
	}

	@Override
	public void onAuthenticationRequested(@NonNull final Core lc, @NonNull final AuthInfo linphoneAuthInfo, @NonNull final AuthMethod authMethod)
	{
		Lg.w("onAuthenticationRequested: linphoneAuthInfo=", linphoneAuthInfo, " authMethod=", authMethod);
	}

	@Override
	public void onNotifyPresenceReceivedForUriOrTel(@NonNull final Core core, @NonNull final Friend friend, @NonNull final String s, @NonNull final PresenceModel presenceModel)
	{
		Lg.w("onNotifyPresenceReceivedForUriOrTel: ", new FriendLogger(friend), " s=", s, " presenceModel=", presenceModel);
	}

	@Override
	public void onChatRoomStateChanged(@NonNull final Core core, @NonNull final ChatRoom chatRoom, final ChatRoom.State state)
	{
		Lg.w("onChatRoomStateChanged: chatRoom", chatRoom, " state=", state);
	}

	@Override
	public void onBuddyInfoUpdated(@NonNull final Core core, @NonNull final Friend friend)
	{
		Lg.w("onBuddyInfoUpdated; ", new FriendLogger(friend));
	}
}

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
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;

import org.linphone.core.AuthInfo;
import org.linphone.core.AuthMethod;
import org.linphone.core.Call;
import org.linphone.core.CallLog;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.GlobalState;
import org.linphone.core.Core.LogCollectionUploadState;
import org.linphone.core.PresenceModel;
import org.linphone.core.RegistrationState;
import org.linphone.core.ConfiguringState;
import org.linphone.core.CoreListener;
import org.linphone.core.Event;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.InfoMessage;
import org.linphone.core.ProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PublishState;
import org.linphone.core.StreamType;
import org.linphone.core.SubscriptionState;
import org.linphone.core.VersionUpdateCheckResult;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;
import org.simlar.helper.FileHelper;
import org.simlar.helper.FileHelper.NotInitedException;
import org.simlar.helper.NetworkQuality;
import org.simlar.helper.VideoState;
import org.simlar.helper.Volumes;
import org.simlar.helper.Volumes.MicrophoneStatus;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public final class LinphoneThread extends Thread implements CoreListener
{
	private Handler mLinphoneThreadHandler = null;
	private final Handler mMainThreadHandler = new Handler();
	private VideoState mVideoState = VideoState.OFF;

	// NOTICE: the linphone handler should only be used in the LINPHONE-THREAD
	private final LinphoneHandler mLinphoneHandler = new LinphoneHandler();

	// NOTICE: the following members should only be used in the MAIN-THREAD
	private final LinphoneThreadListener mListener;
	private RegistrationState mRegistrationState = RegistrationState.None;
	private Volumes mVolumes = new Volumes();
	private final Context mContext;
	private AndroidVideoWindowImpl mMediaStreamerVideoWindow = null;

	public LinphoneThread(final LinphoneThreadListener listener, final Context context)
	{
		mListener = listener;
		mListener.onCallStateChanged("", Call.State.Idle, null);
		mContext = context;

		start();
	}

	@Override
	public void run()
	{
		Lg.i("run");
		Looper.prepare();

		mLinphoneThreadHandler = new Handler();

		Lg.i("handler initialized");

		mMainThreadHandler.post(mListener::onInitialized);

		Looper.loop();
	}

	public void finish()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(() -> {
			mLinphoneThreadHandler.removeCallbacksAndMessages(null);
			mLinphoneThreadHandler = null;
			final Looper looper = Looper.myLooper();
			if (looper != null) {
				looper.quit();
			}
			mLinphoneHandler.destroy();
			mMainThreadHandler.post(() -> {
				mMainThreadHandler.removeCallbacksAndMessages(null);
				mListener.onJoin();
			});
		});
	}

	public void register(final String mySimlarId, final String password)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		try {
			final String linphoneInitialConfigFile = FileHelper.getLinphoneInitialConfigFile();
			final String rootCaFile = FileHelper.getRootCaFileName();
			final String zrtpSecretsCacheFile = FileHelper.getZrtpSecretsCacheFileName();
			final String ringbackSoundFile = FileHelper.getRingbackSoundFile();
			final String pauseSoundFile = FileHelper.getPauseSoundFile();
			final Volumes volumes = mVolumes;

			mLinphoneThreadHandler.post(() -> {
				if (mLinphoneHandler.isInitialized()) {
					mLinphoneHandler.unregister();
					mLinphoneHandler.setCredentials(mySimlarId, password);
				} else {
					// Core uses context only for getting audio manager. I think this is still thread safe.
					mLinphoneHandler.initialize(this, mContext, linphoneInitialConfigFile, rootCaFile,
							zrtpSecretsCacheFile, ringbackSoundFile, pauseSoundFile);
					mLinphoneHandler.setVolumes(volumes);
					mLinphoneHandler.setCredentials(mySimlarId, password);
					linphoneIterator();
				}
			});
		} catch (final NotInitedException e) {
			Lg.ex(e, "PreferencesHelper.NotInitedException");
		}
	}

	private void linphoneIterator()
	{
		mLinphoneHandler.linphoneCoreIterate();

		mLinphoneThreadHandler.postDelayed(this::linphoneIterator, 20);
	}

	public void unregister()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(mLinphoneHandler::unregister);
	}

	public void refreshRegisters()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(mLinphoneHandler::refreshRegisters);
	}

	public void call(final String number)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		if (Util.isNullOrEmpty(number)) {
			Lg.e("call: empty number aborting");
			return;
		}

		if (RegistrationState.Ok != mRegistrationState) {
			Lg.i("call: not registered");
			return;
		}

		mLinphoneThreadHandler.post(() -> mLinphoneHandler.call(number));
	}

	public void pickUp()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(mLinphoneHandler::pickUp);
	}

	public void terminateAllCalls()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(mLinphoneHandler::terminateAllCalls);
	}

	public void verifyAuthenticationToken(final String token, final boolean verified)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(() -> mLinphoneHandler.verifyAuthenticationToken(token, verified));
	}

	public void pauseAllCalls()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(mLinphoneHandler::pauseAllCalls);
	}

	public void resumeCall()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(mLinphoneHandler::resumeCall);
	}

	public void setVolumes(final Volumes volumes)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		if (volumes == null) {
			Lg.e("volumes is null");
			return;
		}

		mVolumes = volumes;

		mLinphoneThreadHandler.post(() -> mLinphoneHandler.setVolumes(volumes));
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

	public void requestVideoUpdate(final boolean enable)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(() -> {
			if (enable) {
				updateVideoState(VideoState.REQUESTING);
			}
			mLinphoneHandler.requestVideoUpdate(enable);
		});
	}

	private void updateVideoState(final VideoState videoState)
	{
		if (mVideoState == videoState) {
			return;
		}

		Lg.i("updating video state: ", mVideoState, " => ", videoState);
		mVideoState = videoState;

		mMainThreadHandler.post(() -> mListener.onVideoStateChanged(videoState));
	}

	public void acceptVideoUpdate(final boolean accept)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(() -> mLinphoneHandler.acceptVideoUpdate(accept));
	}

	public void setVideoWindows(final SurfaceView videoView, final SurfaceView captureView)
	{
		if (videoView == null) {
			Lg.e("setVideoWindows: videoView is null => aborting");
			return;
		}

		if (captureView == null) {
			Lg.e("setVideoWindows: captureView is null => aborting");
			return;
		}

		if (mMediaStreamerVideoWindow != null) {
			Lg.e("setVideoWindows: video windows already enabled => aborting");
			return;
		}

		/// Note: AndroidVideoWindowImpl needs to initiated in the gui thread
		mMediaStreamerVideoWindow = new AndroidVideoWindowImpl(videoView, captureView, new AndroidVideoWindowImpl.VideoWindowListener()
		{
			@Override
			public void onVideoRenderingSurfaceReady(final AndroidVideoWindowImpl videoWindow, final SurfaceView surface)
			{
				Lg.i("onVideoRenderingSurfaceReady");
				enableVideoWindow(true);
			}

			@Override
			public void onVideoRenderingSurfaceDestroyed(final AndroidVideoWindowImpl videoWindow)
			{
				Lg.i("onVideoRenderingSurfaceDestroyed");
				enableVideoWindow(false);
			}

			@Override
			public void onVideoPreviewSurfaceReady(final AndroidVideoWindowImpl videoWindowPreview, final SurfaceView surface)
			{
				Lg.i("onVideoPreviewSurfaceReady");
				setVideoPreviewWindow(surface);
			}

			@Override
			public void onVideoPreviewSurfaceDestroyed(final AndroidVideoWindowImpl videoWindowPreview)
			{
				Lg.i("onVideoPreviewSurfaceDestroyed");
				setVideoPreviewWindow(null);
			}
		});
	}

	public void destroyVideoWindows()
	{
		if (mMediaStreamerVideoWindow == null) {
			Lg.i("destroyVideoWindows: video windows already destroyed");
			return;
		}

		Lg.i("destroyVideoWindows: disabling video windows");
		setVideoWindow(null);
		setVideoPreviewWindow(null);
		mMediaStreamerVideoWindow.release();
		mMediaStreamerVideoWindow = null;
	}

	public void enableVideoWindow(final boolean enable)
	{
		if (enable && mMediaStreamerVideoWindow == null) {
			Lg.e("enableVideoWindow with no mMediaStreamerVideoWindow => disabling");
		}

		setVideoWindow(enable ? mMediaStreamerVideoWindow : null);
	}

	private void setVideoWindow(final Object videoWindow)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(() -> mLinphoneHandler.setNativeVideoWindowId(videoWindow));
	}

	private void setVideoPreviewWindow(final Object videoPreviewWindow)
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(() -> mLinphoneHandler.setVideoPreviewWindow(videoPreviewWindow));
	}

	public void toggleCamera()
	{
		if (mLinphoneThreadHandler == null) {
			Lg.e("handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(mLinphoneHandler::toggleCamera);
	}

	@Lg.Anonymize
	private static class CallLogger
	{
		private final Call mCall;

		CallLogger(final Call call)
		{
			mCall = call;
		}

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

		@Override
		public final String toString()
		{
			if (mFriend == null || mFriend.getAddress() == null) {
				return "";
			}

			return mFriend.getAddress().getUsername();
		}
	}

	private static String getNumber(final Call call)
	{
		if (call == null || call.getRemoteAddress() == null) {
			return "";
		}

		return call.getRemoteAddress().asStringUriOnly().split("@")[0].replaceFirst("sip:", "");
	}

	//
	// CoreListener overloaded member functions
	//
	@Override
	public void onRegistrationStateChanged(final Core lc, final ProxyConfig cfg, final RegistrationState state, final String message)
	{
		// ProxyConfig is probably mutable => use it only in the calling thread
		// RegistrationState is immutable

		final String identity = cfg.getIdentityAddress().getUsername();

		mMainThreadHandler.post(() -> {
			if (Util.equals(mRegistrationState, state)) {
				Lg.v("registration state for ", new Lg.Anonymizer(identity), " not changed: state=", state, " message=", message);
				return;
			}

			Lg.i("registration state for ", new Lg.Anonymizer(identity), " changed: ", state, " ", message);
			mRegistrationState = state;

			mListener.onRegistrationStateChanged(state);
		});

	}

	private Call.State fixCallState(final Call.State onCallStateChanged)
	{
		if (Call.State.Released == onCallStateChanged || Call.State.Error == onCallStateChanged) {
			if (mLinphoneHandler.hasNoCurrentCalls()) {
				Lg.i("fixCallState: ", onCallStateChanged, " -> ", Call.State.End);
				return Call.State.End;
			}
		}

		return onCallStateChanged;
	}

	private VideoState createVideoState(final Call.State state, final Call call)
	{
		final boolean localVideo = call.getCurrentParams() != null && call.getCurrentParams().videoEnabled();
		final boolean remoteVideo = call.getRemoteParams() != null && call.getRemoteParams().videoEnabled();

		Lg.i("creating videoState based on localVideo= ", localVideo, " remoteVideo=", remoteVideo);

		if (Call.State.End != state && localVideo && remoteVideo) {
			return VideoState.INITIALIZING;
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
	public void onCallStateChanged(final Core lc, final Call call, final Call.State state, final String message)
	{
		// Call is mutable => use it only in the calling thread
		// Call.State is immutable

		final String number = getNumber(call);
		final Call.State fixedState = fixCallState(state);
		final VideoState videoState = createVideoState(fixedState, call);

		Lg.i("onCallStateChanged changed state=", fixedState, " number=", new CallLogger(call), " message=", message, " videoState=", videoState);

		if (videoState == VideoState.REMOTE_REQUESTED) {
			Lg.i("remote requested video");
			/// NOTE: this needs to happen directly, posting to linphone thread might take to long
			LinphoneHandler.preventAutoAnswer(call);
		}

		updateVideoState(videoState);
		mMainThreadHandler.post(() -> mListener.onCallStateChanged(number, fixedState, message));
	}

	@Override
	public void onMessageReceived(final Core lc, final ChatRoom cr, final ChatMessage message)
	{
		Lg.i("onMessageReceived message=", message);
	}

	@Override
	public void onEcCalibrationResult(final Core core, final EcCalibratorStatus ecCalibratorStatus, final int i)
	{
		Lg.w("onEcCalibrationResult: ecCalibratorStatus=", ecCalibratorStatus, " i=", i);
	}

	@Override
	public void onSubscribeReceived(final Core core, final Event event, final String s, final Content content)
	{
		Lg.w("onSubscribeReceived: event=", event, " s=", s, " content=", content);
	}

	@Override
	public void onGlobalStateChanged(final Core lc, final GlobalState state, final String message)
	{
		Lg.i("onGlobalStateChanged state=", state, " message=", message);
	}

	@Override
	public void onNewSubscriptionRequested(final Core lc, final Friend lf, final String url)
	{
		// Friend is mutable => use it only in the calling thread

		Lg.w("[", new FriendLogger(lf), "] wants to see your presence status => always accepting");
	}

	@Override
	public void onNotifyPresenceReceived(final Core lc, final Friend lf)
	{
		// Friend is mutable => use it only in the calling thread
		// OnlineStatus is immutable

		Lg.w("presence received: username=", new FriendLogger(lf));
	}

	@Override
	public void onEcCalibrationAudioInit(final Core core)
	{
		Lg.w("onEcCalibrationAudioInit");
	}

	@Override
	public void onCallStatsUpdated(final Core lc, final Call call, final CallStats statsDoNotUse)
	{
		// Call is mutable => use it only in the calling thread
		// CallStats maybe mutable => use it only in the calling thread

		final CallStats stats = call.getStats(StreamType.Audio);
		final int duration = call.getDuration();
		final PayloadType payloadType = call.getCurrentParams().getUsedAudioPayloadType();
		final String codec = payloadType.getMimeType() + ' ' + payloadType.getClockRate() / 1000;
		final String iceState = stats.getIceState().toString();
		final int upload = Math.round(stats.getUploadBandwidth() / 8.0f * 10.0f); // upload bandwidth in 100 Bytes / second
		final int download = Math.round(stats.getDownloadBandwidth() / 8.0f * 10.0f); // download bandwidth in 100 Bytes / second
		final int jitter = Math.round((stats.getReceiverInterarrivalJitter() + stats.getSenderInterarrivalJitter()) * 1000.0f);
		final int packetLoss = Math.round((stats.getReceiverLossRate() + stats.getSenderLossRate()) / 2.0f * 10.0f); // sum of up and down stream loss in per mille
		final long latePackets = stats.getLatePacketsCumulativeNumber();
		final int roundTripDelay = Math.round(stats.getRoundTripDelay() * 1000.0f);

		// set quality to unusable if up or download bandwidth is zero
		final float quality = upload > 0 && download > 0 ? call.getCurrentQuality() : 0;

		Lg.d("onCallStatsUpdated: number=", new CallLogger(call), " quality=", quality,
				" duration=", duration,
				" codec=", codec, " iceState=", iceState,
				" upload=", upload, " download=", download,
				" jitter=", jitter, " loss=", packetLoss,
				" latePackets=", latePackets, " roundTripDelay=", roundTripDelay);

		final CallStats videoStats = call.getStats(StreamType.Video);
		final PayloadType videoPayloadType = call.getCurrentParams().getUsedVideoPayloadType();
		final String videoCodec = videoPayloadType.getMimeType() + ' ' + videoPayloadType.getClockRate() / 1000;
		final String videoIceState = videoStats.getIceState().toString();

		Lg.d("onCallStatsUpdated videoStats: number=", new CallLogger(call),
				" upload=",  Math.round(videoStats.getUploadBandwidth() / 8.0f * 10.0f), "(", upload, ")",
				" download=",  Math.round(videoStats.getDownloadBandwidth() / 8.0f * 10.0f), "(", download, ")",
				" codec=", videoCodec, " iceState=", videoIceState);

		if (videoStats.getDownloadBandwidth() != 0 && mVideoState == VideoState.INITIALIZING) {
			Lg.i("video playing");
			updateVideoState(VideoState.PLAYING);
		}

		mMainThreadHandler.post(() -> mListener.onCallStatsChanged(NetworkQuality.fromFloat(quality), duration, codec, iceState, upload, download,
				jitter, packetLoss, latePackets, roundTripDelay));
	}

	@Override
	public void onCallEncryptionChanged(final Core lc, final Call call, final boolean encrypted, final String authenticationToken)
	{
		// Call is mutable => use it only in the calling thread

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

		mMainThreadHandler.post(() -> mListener.onCallEncryptionChanged(authenticationToken, isTokenVerified));
	}

	@Override
	public void onDtmfReceived(final Core lc, final Call call, final int dtmf)
	{
		Lg.w("onDtmfReceived number=", new CallLogger(call), " dtmf=", dtmf);
	}

	@Override
	public void onTransferStateChanged(final Core lc, final Call call, final Call.State state)
	{
		Lg.w("onTransferStateChanged number=", new CallLogger(call), " State=", state);
	}

	@Override
	public void onInfoReceived(final Core lc, final Call call, final InfoMessage info)
	{
		Lg.w("onInfoReceived number=", new CallLogger(call), " InfoMessage=", info.getContent().getStringBuffer());
	}

	@Override
	public void onSubscriptionStateChanged(final Core lc, final Event ev, final SubscriptionState state)
	{
		Lg.w("onSubscriptionStateChanged ev=", ev.getName(), " SubscriptionState=", state);
	}

	@Override
	public void onCallLogUpdated(final Core core, final CallLog callLog)
	{
		Lg.i("onCallLogUpdated: callLog=", callLog == null ? null : callLog.getStatus());
	}

	@Override
	public void onIsComposingReceived(final Core lc, final ChatRoom cr)
	{
		Lg.w("onIsComposingReceived PeerAddress=", cr.getPeerAddress());
	}

	@Override
	public void onMessageReceivedUnableDecrypt(final Core core, final ChatRoom chatRoom, final ChatMessage chatMessage)
	{
		Lg.w("onMessageReceivedUnableDecrypt: chatRoom=", chatRoom, " chatMessage=", chatMessage);
	}

	@Override
	public void onConfiguringStatus(final Core lc, final ConfiguringState state, final String message)
	{
		if (state == ConfiguringState.Skipped) {
			return;
		}
		Lg.w("onConfiguringStatus remoteProvisioningState=", state, " message=", message);
	}

	@Override
	public void onCallCreated(final Core core, final Call call)
	{
		Lg.i("onCallCreated; call=", new CallLogger(call));
	}

	@Override
	public void onPublishStateChanged(final Core core, final Event event, final PublishState publishState)
	{
		Lg.w("onPublishStateChanged: event=", event, " publishState=", publishState);
	}

	@Override
	public void onLogCollectionUploadProgressIndication(final Core lc, final int offset, final int total)
	{
		Lg.w("onLogCollectionUploadProgressIndication: offset=", offset, " total=", total);
	}

	@Override
	public void onVersionUpdateCheckResultReceived(final Core core, final VersionUpdateCheckResult versionUpdateCheckResult, final String s, final String s1)
	{
		Lg.w("onVersionUpdateCheckResultReceived: versionUpdateCheckResult=", versionUpdateCheckResult, " s=", s, " s1=", s);
	}

	@Override
	public void onEcCalibrationAudioUninit(final Core core)
	{
		Lg.w("onEcCalibrationAudioUninit");
	}

	@Override
	public void onLogCollectionUploadStateChanged(final Core lc, final LogCollectionUploadState state, final String info)
	{
		Lg.w("onLogCollectionUploadStateChanged: state=", state, " info=", info);
	}

	@Override
	public void onFriendListCreated(final Core lc, final FriendList linphoneFriendList)
	{
		Lg.w("onFriendListCreated: linphoneFriendList=", linphoneFriendList);
	}

	@Override
	public void onFriendListRemoved(final Core lc, final FriendList linphoneFriendList)
	{
		Lg.w("onFriendListRemoved: linphoneFriendList=", linphoneFriendList);
	}

	@Override
	public void onReferReceived(final Core core, final String s)
	{
		Lg.w("onReferReceived: ", s);
	}

	@Override
	public void onQrcodeFound(final Core core, final String s)
	{
		Lg.w("onQrcodeFound; ", s);
	}

	@Override
	public void onNetworkReachable(final Core linphoneCore, final boolean b)
	{
		Lg.i("onNetworkReachable reachable=", Boolean.toString(b));
	}

	@Override
	public void onNotifyReceived(final Core core, final Event event, final String s, final Content content)
	{
		Lg.w("onNotifyReceived: event=", event, " s=", s, " content=", content);
	}

	@Override
	public void onAuthenticationRequested(final Core lc, final AuthInfo linphoneAuthInfo, final AuthMethod authMethod)
	{
		Lg.w("onAuthenticationRequested: linphoneAuthInfo=", linphoneAuthInfo, " authMethod=", authMethod);
	}

	@Override
	public void onNotifyPresenceReceivedForUriOrTel(final Core core, final Friend friend, final String s, final PresenceModel presenceModel)
	{
		Lg.w("onNotifyPresenceReceivedForUriOrTel: ", new FriendLogger(friend), " s=", s, " presenceModel=", presenceModel);
	}

	@Override
	public void onChatRoomStateChanged(final Core core, final ChatRoom chatRoom, final ChatRoom.State state)
	{
		Lg.w("onChatRoomStateChanged: chatRoom", chatRoom, " state=", state);
	}

	@Override
	public void onBuddyInfoUpdated(final Core core, final Friend friend)
	{
		Lg.w("onBuddyInfoUpdated; ", new FriendLogger(friend));
	}
}

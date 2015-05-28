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

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.LogCollectionUploadState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;
import org.simlar.helper.FileHelper;
import org.simlar.helper.FileHelper.NotInitedException;
import org.simlar.helper.NetworkQuality;
import org.simlar.helper.VideoState;
import org.simlar.helper.Volumes;
import org.simlar.helper.Volumes.MicrophoneStatus;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.nio.ByteBuffer;

public final class LinphoneThread
{
	private final LinphoneThreadImpl mImpl;

	private static final class LinphoneThreadImpl extends Thread implements LinphoneCoreListener
	{
		Handler mLinphoneThreadHandler = null;
		final Handler mMainThreadHandler = new Handler();
		private VideoState mVideoState = VideoState.OFF;

		// NOTICE: the linphone handler should only be used in the LINPHONE-THREAD
		final LinphoneHandler mLinphoneHandler = new LinphoneHandler();

		// NOTICE: the following members should only be used in the MAIN-THREAD
		final LinphoneThreadListener mListener;
		RegistrationState mRegistrationState = RegistrationState.RegistrationNone;
		Volumes mVolumes = new Volumes();
		final Context mContext;
		private AndroidVideoWindowImpl mMediaStreamerVideoWindow = null;

		LinphoneThreadImpl(final LinphoneThreadListener listener, final Context context)
		{
			mListener = listener;
			mListener.onCallStateChanged("", LinphoneCall.State.Idle, null);
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

		void finish()
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

		void register(final String mySimlarId, final String password)
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
						// LinphoneCore uses context only for getting audio manager. I think this is still thread safe.
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

		void linphoneIterator()
		{
			mLinphoneHandler.linphoneCoreIterate();

			mLinphoneThreadHandler.postDelayed(this::linphoneIterator, 20);
		}

		void unregister()
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(mLinphoneHandler::unregister);
		}

		void refreshRegisters()
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(mLinphoneHandler::refreshRegisters);
		}

		void call(final String number)
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			if (Util.isNullOrEmpty(number)) {
				Lg.e("call: empty number aborting");
				return;
			}

			if (!RegistrationState.RegistrationOk.equals(mRegistrationState)) {
				Lg.i("call: not registered");
				return;
			}

			mLinphoneThreadHandler.post(() -> mLinphoneHandler.call(number));
		}

		void pickUp()
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(mLinphoneHandler::pickUp);
		}

		void terminateAllCalls()
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(mLinphoneHandler::terminateAllCalls);
		}

		void verifyAuthenticationToken(final String token, final boolean verified)
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(() -> mLinphoneHandler.verifyAuthenticationToken(token, verified));
		}

		void pauseAllCalls()
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(mLinphoneHandler::pauseAllCalls);
		}

		void resumeCall()
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(mLinphoneHandler::resumeCall);
		}

		void setVolumes(final Volumes volumes)
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

		public void requestVideoUpdate(final boolean enable)
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					if (enable) {
						updateVideoState(VideoState.REQUESTING);
					}
					mLinphoneHandler.requestVideoUpdate(enable);
				}
			});
		}

		private void updateVideoState(final VideoState videoState)
		{
			if (mVideoState == videoState) {
				return;
			}

			Lg.i("updating video state: ", mVideoState, " => ", videoState);
			mVideoState = videoState;

			mMainThreadHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					mListener.onVideoStateChanged(videoState);
				}
			});
		}

		public void acceptVideoUpdate(final boolean accept)
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					mLinphoneHandler.acceptVideoUpdate(accept);
				}
			});
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
				public void onVideoRenderingSurfaceReady(final AndroidVideoWindowImpl videoWindow, final SurfaceView surface)
				{
					Lg.i("onVideoRenderingSurfaceReady");
					enableVideoWindow(true);
				}

				public void onVideoRenderingSurfaceDestroyed(final AndroidVideoWindowImpl videoWindow)
				{
					Lg.i("onVideoRenderingSurfaceDestroyed");
					enableVideoWindow(false);
				}

				public void onVideoPreviewSurfaceReady(final AndroidVideoWindowImpl videoWindowPreview, final SurfaceView surface)
				{
					Lg.i("onVideoPreviewSurfaceReady");
					setVideoPreviewWindow(surface);
				}

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

			mLinphoneThreadHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					mLinphoneHandler.setVideoWindow(videoWindow);
				}
			});
		}

		private void setVideoPreviewWindow(final Object videoPreviewWindow)
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					mLinphoneHandler.setVideoPreviewWindow(videoPreviewWindow);
				}
			});
		}

		public void toggleCamera()
		{
			if (mLinphoneThreadHandler == null) {
				Lg.e("handler is null, probably thread not started");
				return;
			}

			mLinphoneThreadHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					mLinphoneHandler.toggleCamera();
				}
			});
		}

		@Lg.Anonymize
		private static class CallLogger
		{
			private final LinphoneCall mCall;

			CallLogger(final LinphoneCall call)
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
			private final LinphoneFriend mFriend;

			FriendLogger(final LinphoneFriend friend)
			{
				mFriend = friend;
			}

			@Override
			public final String toString()
			{
				if (mFriend == null || mFriend.getAddress() == null) {
					return "";
				}

				return mFriend.getAddress().getUserName();
			}
		}

		static String getNumber(final LinphoneCall call)
		{
			if (call == null || call.getRemoteAddress() == null) {
				return "";
			}

			return call.getRemoteAddress().asStringUriOnly().split("@")[0].replaceFirst("sip:", "");
		}

		//
		// LinphoneCoreListener overloaded member functions
		//
		@Override
		public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig cfg, final RegistrationState state, final String message)
		{
			// LinphoneProxyConfig is probably mutable => use it only in the calling thread
			// RegistrationState is immutable

			final String identity = cfg.getIdentity();

			mMainThreadHandler.post(() -> {
				if (Util.equals(mRegistrationState, state)) {
					Lg.v("registration state for ", new Lg.Anonymizer(identity), " not changed: state=", state, " message=", message);
					return;
				}

				if (RegistrationState.RegistrationOk.equals(mRegistrationState) && RegistrationState.RegistrationProgress.equals(state)
						&& "Refresh registration".equals(message)) {
					Lg.i("registration state for ", new Lg.Anonymizer(identity), " ignored: ", state,
							" as it is caused by refreshRegisters");
					return;
				}

				Lg.i("registration state for ", new Lg.Anonymizer(identity), " changed: ", state, " ", message);
				mRegistrationState = state;

				mListener.onRegistrationStateChanged(state);
			});

		}

		@Override
		public void displayStatus(final LinphoneCore lc, final String message)
		{
			Lg.v("displayStatus message=", message);
		}

		LinphoneCall.State fixLinphoneCallState(final LinphoneCall.State callState)
		{
			if (LinphoneCall.State.CallReleased.equals(callState) || LinphoneCall.State.Error.equals(callState)) {
				if (mLinphoneHandler.hasNoCurrentCalls()) {
					Lg.i("fixLinphoneCallState: ", callState, " -> ", LinphoneCall.State.CallEnd);
					return LinphoneCall.State.CallEnd;
				}
			}

			return callState;
		}

		private VideoState createVideoState(final LinphoneCall.State state, final boolean localVideo, final boolean remoteVideo, final LinphoneCallStats videoStats)
		{
			if (!LinphoneCall.State.CallEnd.equals(state) && localVideo && remoteVideo) {
				if (videoStats == null || mVideoState == VideoState.ENCRYPTING) {
					return VideoState.ENCRYPTING;
				}

				if (!isConnectionEstablished(videoStats.getIceState())) {
					return VideoState.WAITING_FOR_ICE;
				}

				return VideoState.PLAYING;
			}

			if (LinphoneCall.State.CallUpdatedByRemote.equals(state) && !localVideo && remoteVideo) {
				return VideoState.REMOTE_REQUESTED;
			}

			if (LinphoneCall.State.StreamsRunning.equals(state) && mVideoState == VideoState.REQUESTING) {
				return VideoState.DENIED;
			}

			if (mVideoState == VideoState.REQUESTING) {
				return VideoState.REQUESTING;
			}

			return VideoState.OFF;
		}

		private static boolean isConnectionEstablished(final LinphoneCallStats.IceState iceState)
		{
			return LinphoneCallStats.IceState.HostConnection.equals(iceState) ||
					LinphoneCallStats.IceState.ReflexiveConnection.equals(iceState) ||
					LinphoneCallStats.IceState.RelayConnection.equals(iceState);
		}

		@Override
		public void callState(final LinphoneCore lc, final LinphoneCall call, final LinphoneCall.State state, final String message)
		{
			// LinphoneCall is mutable => use it only in the calling thread
			// LinphoneCall.State is immutable

			final String number = getNumber(call);
			final LinphoneCall.State fixedState = fixLinphoneCallState(state);
			final VideoState videoState = createVideoState(fixedState, call.getCurrentParamsCopy().getVideoEnabled(), call.getRemoteParams().getVideoEnabled(), call.getVideoStats());

			updateVideoState(videoState);

			Lg.i("callState changed state=", fixedState, " number=", new CallLogger(call), " message=", message, " videoState=", videoState);

			if (videoState == VideoState.REMOTE_REQUESTED) {
				Lg.i("remote requested video");
				/// NOTE: this needs to happen directly, posting to linphone thread might take to log
				mLinphoneHandler.preventAutoAnswer();
			}

			mMainThreadHandler.post(() -> mListener.onCallStateChanged(number, fixedState, message));
		}

		@Override
		public void messageReceived(final LinphoneCore lc, final LinphoneChatRoom cr, final LinphoneChatMessage message)
		{
			Lg.i("messageReceived message=", message);
		}

		@Override
		public void messageReceivedUnableToDecrypted(final LinphoneCore lc, final LinphoneChatRoom cr, final LinphoneChatMessage message)
		{
			Lg.w("messageReceivedUnableToDecrypted message=", message);
		}

		@Override
		public void show(final LinphoneCore lc)
		{
			Lg.i("show called");
		}

		@Override
		public void authInfoRequested(final LinphoneCore lc, final String realm, final String username, final String domain)
		{
			Lg.w("authInfoRequested realm=", realm, " username=", new Lg.Anonymizer(username), " domain=", domain);
		}

		@Override
		public void displayMessage(final LinphoneCore lc, final String message)
		{
			Lg.i("displayMessage message=", message);
		}

		@Override
		public void displayWarning(final LinphoneCore lc, final String message)
		{
			Lg.w("displayWarning message=", message);
		}

		@Override
		public void globalState(final LinphoneCore lc, final GlobalState state, final String message)
		{
			Lg.i("globalState state=", state, " message=", message);
		}

		@Override
		public void newSubscriptionRequest(final LinphoneCore lc, final LinphoneFriend lf, final String url)
		{
			// LinphoneFriend is mutable => use it only in the calling thread

			Lg.w("[", new FriendLogger(lf), "] wants to see your presence status => always accepting");
		}

		@Override
		public void notifyPresenceReceived(final LinphoneCore lc, final LinphoneFriend lf)
		{
			// LinphoneFriend is mutable => use it only in the calling thread
			// OnlineStatus is immutable

			Lg.w("presence received: username=", new FriendLogger(lf));
		}

		@Override
		public void callStatsUpdated(final LinphoneCore lc, final LinphoneCall call, final LinphoneCallStats statsDoNotUse)
		{
			// LinphoneCall is mutable => use it only in the calling thread
			// LinphoneCallStats maybe mutable => use it only in the calling thread

			/// crashing since liblinphone 3.2.5
			if (LinphoneCallStats.MediaType.Video.equals(statsDoNotUse.getMediaType())) {
				if (mVideoState == VideoState.WAITING_FOR_ICE && isConnectionEstablished(statsDoNotUse.getIceState())) {
					updateVideoState(VideoState.PLAYING);
				}
				return;
			}

			final LinphoneCallStats stats = call.getAudioStats();
			final int duration = call.getDuration();
			final PayloadType payloadType = call.getCurrentParams().getUsedAudioCodec();
			final String codec = payloadType.getMime() + ' ' + payloadType.getRate() / 1000;
			final String iceState = stats.getIceState().toString();
			final int upload = Math.round(stats.getUploadBandwidth() / 8.0f * 10.0f); // upload bandwidth in 100 Bytes / second
			final int download = Math.round(stats.getDownloadBandwidth() / 8.0f * 10.0f); // download bandwidth in 100 Bytes / second
			final int jitter = Math.round((stats.getReceiverInterarrivalJitter() + stats.getSenderInterarrivalJitter()) * 1000.0f);
			final int packetLoss = Math.round((stats.getReceiverLossRate() + stats.getSenderLossRate()) / 2.0f * 10.0f); // sum of up and down stream loss in per mille
			final long latePackets = stats.getLatePacketsCumulativeNumber();
			final int roundTripDelay = Math.round(stats.getRoundTripDelay() * 1000.0f);

			// set quality to unusable if up or download bandwidth is zero
			final float quality = upload > 0 && download > 0 ? call.getCurrentQuality() : 0;

			Lg.d("callStatsUpdated: number=", new CallLogger(call), " quality=", quality,
					" duration=", duration,
					" codec=", codec, " iceState=", iceState,
					" upload=", upload, " download=", download,
					" jitter=", jitter, " loss=", packetLoss,
					" latePackets=", latePackets, " roundTripDelay=", roundTripDelay);

			mMainThreadHandler.post(() -> mListener.onCallStatsChanged(NetworkQuality.fromFloat(quality), duration, codec, iceState, upload, download,
					jitter, packetLoss, latePackets, roundTripDelay));
		}

		@Override
		public void ecCalibrationStatus(final LinphoneCore lc, final EcCalibratorStatus status, final int delay_ms, final Object data)
		{
			Lg.i("ecCalibrationStatus status=", status, " delay_ms=", delay_ms);
		}

		@Override
		public void callEncryptionChanged(final LinphoneCore lc, final LinphoneCall call, final boolean encrypted, final String authenticationToken)
		{
			// LinphoneCall is mutable => use it only in the calling thread

			final boolean isTokenVerified = call.isAuthenticationTokenVerified();

			Lg.i("callEncryptionChanged number=", new CallLogger(call), " encrypted=", encrypted,
					" authenticationToken=", authenticationToken);

			if (!encrypted) {
				Lg.e("unencrypted call: number=", new CallLogger(call), " with UserAgent ", call.getRemoteUserAgent());
			}

			if (encrypted && mVideoState == VideoState.ENCRYPTING) {
				Lg.i("video encrypted");
				updateVideoState(VideoState.PLAYING);
			}

			mMainThreadHandler.post(() -> mListener.onCallEncryptionChanged(authenticationToken, isTokenVerified));
		}

		@Override
		public void notifyReceived(final LinphoneCore lc, final LinphoneCall call, final LinphoneAddress from, final byte[] event)
		{
			Lg.w("notifyReceived number=", new CallLogger(call), " from=", from);
		}

		@Override
		public void dtmfReceived(final LinphoneCore lc, final LinphoneCall call, final int dtmf)
		{
			Lg.w("dtmfReceived number=", new CallLogger(call), " dtmf=", dtmf);
		}

		@Override
		public void transferState(final LinphoneCore lc, final LinphoneCall call, final LinphoneCall.State state)
		{
			Lg.w("transferState number=", new CallLogger(call), " State=", state);
		}

		@Override
		public void infoReceived(final LinphoneCore lc, final LinphoneCall call, final LinphoneInfoMessage info)
		{
			Lg.w("infoReceived number=", new CallLogger(call), " LinphoneInfoMessage=", info.getContent().getDataAsString());
		}

		@Override
		public void subscriptionStateChanged(final LinphoneCore lc, final LinphoneEvent ev, final SubscriptionState state)
		{
			Lg.w("subscriptionStateChanged ev=", ev.getEventName(), " SubscriptionState=", state);
		}

		@Override
		public void notifyReceived(final LinphoneCore lc, final LinphoneEvent ev, final String eventName, final LinphoneContent content)
		{
			Lg.w("notifyReceived ev=", ev.getEventName(), " eventName=", eventName, " content=", content);
		}

		@Override
		public void publishStateChanged(final LinphoneCore lc, final LinphoneEvent ev, final PublishState state)
		{
			Lg.w("publishStateChanged ev=", ev.getEventName(), " state=", state);
		}

		@Override
		public void isComposingReceived(final LinphoneCore lc, final LinphoneChatRoom cr)
		{
			Lg.w("isComposingReceived PeerAddress=", cr.getPeerAddress());
		}

		@Override
		public void configuringStatus(final LinphoneCore lc, final RemoteProvisioningState state, final String message)
		{
			if (RemoteProvisioningState.ConfiguringSkipped.equals(state)) {
				return;
			}
			Lg.w("configuringStatus remoteProvisioningState=", state, " message=", message);
		}

		@Override
		public void fileTransferProgressIndication(final LinphoneCore lc, final LinphoneChatMessage message, final LinphoneContent content,
		                                           final int progress)
		{
			Lg.w("fileTransferProgressIndication: message=", message, " progress=", progress);
		}

		@Override
		public void fileTransferRecv(final LinphoneCore lc, final LinphoneChatMessage message, final LinphoneContent content, final byte[] buffer,
		                             final int size)
		{
			Lg.w("fileTransferRecv: message=", message, " size=", size);
		}

		@Override
		public int fileTransferSend(final LinphoneCore lc, final LinphoneChatMessage message, final LinphoneContent content, final ByteBuffer buffer,
		                            final int size)
		{
			Lg.w("fileTransferSend: message=", message, " size=", size);
			return 0;
		}

		@Override
		public void uploadProgressIndication(final LinphoneCore lc, final int offset, final int total)
		{
			Lg.w("uploadProgressIndication: offset=", offset, " total=", total);
		}

		@Override
		public void uploadStateChanged(final LinphoneCore lc, final LogCollectionUploadState state, final String info)
		{
			Lg.w("uploadStateChanged: state=", state, " info=", info);
		}

		@Override
		public void friendListCreated(final LinphoneCore lc, final LinphoneFriendList linphoneFriendList)
		{
			Lg.w("friendListCreated: linphoneFriendList=", linphoneFriendList);
		}

		@Override
		public void friendListRemoved(final LinphoneCore lc, final LinphoneFriendList linphoneFriendList)
		{
			Lg.w("friendListRemoved: linphoneFriendList=", linphoneFriendList);
		}

		@Override
		public void networkReachableChanged(final LinphoneCore linphoneCore, final boolean b)
		{
			Lg.i("networkReachableChanged reachable=", Boolean.toString(b));
		}

		@Override
		public void authenticationRequested(final LinphoneCore lc, final LinphoneAuthInfo linphoneAuthInfo, final LinphoneCore.AuthMethod authMethod)
		{
			Lg.w("authenticationRequested: linphoneAuthInfo=", linphoneAuthInfo, " authMethod=", authMethod);
		}
	}

	//
	// LinphoneThread
	//
	public LinphoneThread(final LinphoneThreadListener listener, final Context context)
	{
		mImpl = new LinphoneThreadImpl(listener, context);
	}

	public void finish()
	{
		mImpl.finish();
	}

	@SuppressWarnings("SameParameterValue")
	public void join(final long millis) throws InterruptedException
	{
		mImpl.join(millis);
	}

	public void register(final String mySimlarId, final String password)
	{
		mImpl.register(mySimlarId, password);
	}

	public void unregister()
	{
		mImpl.unregister();
	}

	public void refreshRegisters()
	{
		mImpl.refreshRegisters();
	}

	public void call(final String number)
	{
		mImpl.call(number);
	}

	public void pickUp()
	{
		mImpl.pickUp();
	}

	public void terminateAllCalls()
	{
		mImpl.terminateAllCalls();
	}

	public void verifyAuthenticationToken(final String token, final boolean verified)
	{
		mImpl.verifyAuthenticationToken(token, verified);
	}

	public void pauseAllCalls()
	{
		mImpl.pauseAllCalls();
	}

	public void resumeCall()
	{
		mImpl.resumeCall();
	}

	public void setVolumes(final Volumes volumes)
	{
		mImpl.setVolumes(volumes);
	}

	public void setMicrophoneStatus(final MicrophoneStatus microphoneStatus)
	{
		if (mImpl.mVolumes == null) {
			Lg.e("volumes not initialized");
			return;
		}

		setVolumes(mImpl.mVolumes.setMicrophoneStatus(microphoneStatus));
	}

	public Volumes getVolumes()
	{
		return mImpl.mVolumes;
	}

	public void requestVideoUpdate(final boolean enable)
	{
		mImpl.requestVideoUpdate(enable);
	}

	public void acceptVideoUpdate(final boolean accept)
	{
		mImpl.acceptVideoUpdate(accept);
	}

	public void setVideoWindows(final SurfaceView videoView, final SurfaceView captureView)
	{
		mImpl.setVideoWindows(videoView, captureView);
	}

	public void enableVideoWindow(final boolean enable)
	{
		mImpl.enableVideoWindow(enable);
	}

	public void destroyVideoWindows()
	{
		mImpl.destroyVideoWindows();
	}

	public void toggleCamera()
	{
		mImpl.toggleCamera();
	}
}

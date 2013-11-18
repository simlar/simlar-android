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

import java.util.HashSet;
import java.util.Set;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PresenceBasicStatus;
import org.linphone.core.PresenceModel;
import org.linphone.core.PresenceService;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.simlar.FileHelper.NotInitedException;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class LinphoneThread extends Thread implements LinphoneCoreListener
{
	static final String LOGTAG = LinphoneThread.class.getSimpleName();

	Handler mLinphoneThreadHandler = null;
	Handler mMainThreadHandler = new Handler();

	// NOTICE: the linphone handler should only be used in the LINPHONE-THREAD
	LinphoneHandler mLinphoneHandler = new LinphoneHandler();

	// NOTICE: the following members should only be used in the MAIN-THREAD
	LinphoneHandlerListener mListener = null;
	RegistrationState mRegistrationState = RegistrationState.RegistrationNone;
	Set<String> mFriends = new HashSet<String>();
	Volumes mVolumes = new Volumes();
	Context mContext = null;

	public LinphoneThread(LinphoneHandlerListener listener, Context context)
	{
		mListener = listener;
		mListener.onCallStateChanged("", LinphoneCall.State.Idle, -1);
		mContext = context;

		start();
	}

	@Override
	public void run()
	{
		Log.i(LOGTAG, "run");
		Looper.prepare();

		mLinphoneThreadHandler = new Handler();

		Log.i(LOGTAG, "handler initialized");

		Looper.loop();
	}

	public void finish()
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.destroy();
				Looper.myLooper().quit();
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run()
					{
						mListener.onJoin();
					}
				});
			}
		});
	}

	public void register(final String mySimlarId, final String password)
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		try {
			final String linphoneInitialConfigFile = FileHelper.getLinphoneInitialConfigFile();
			final String rootCaFile = FileHelper.getRootCaFileName();
			final String zrtpSecretsCacheFile = FileHelper.getZrtpSecretsCacheFileName();
			final Volumes volumes = mVolumes;
			final Context context = mContext;

			mLinphoneThreadHandler.post(new Runnable() {
				@Override
				public void run()
				{
					if (!mLinphoneHandler.isInitialized()) {
						// LinphoneCore uses context only for getting audio manager. I think this is still thread safe.
						mLinphoneHandler.initialize(LinphoneThread.this, context, linphoneInitialConfigFile, rootCaFile,
								zrtpSecretsCacheFile);
						mLinphoneHandler.setVolumes(volumes);
						mLinphoneHandler.setCredentials(mySimlarId, password);
						linphoneIterator();
					} else {
						mLinphoneHandler.unregister();
						mLinphoneHandler.setCredentials(mySimlarId, password);
					}
				}
			});
		} catch (NotInitedException e) {
			e.printStackTrace();
		}
	}

	void linphoneIterator()
	{
		if (mLinphoneHandler == null) {
			Log.e(LOGTAG, "linphoneIterator no handler => quitting");
			return;
		}

		mLinphoneHandler.linphoneCoreIterate();

		mLinphoneThreadHandler.postDelayed(new Runnable() {
			@Override
			public void run()
			{
				linphoneIterator();
			}
		}, 20);
	}

	public void refreshRegisters()
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		if (mLinphoneHandler == null) {
			Log.e(LOGTAG, "refreshRegisters no handler => quitting");
			return;
		}

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.refreshRegisters();
			}
		});
	}

	public void unregister()
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.unregister();
			}
		});
	}

	public void addFriend(final String number)
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		if (Util.isNullOrEmpty(number)) {
			Log.e(LOGTAG, "empty number aborting");
			return;
		}

		if (number.equals(PreferencesHelper.getMySimlarIdOrEmptyString())) {
			Log.i(LOGTAG, "not adding myself as a friend");
			return;
		}

		if (mRegistrationState == RegistrationState.RegistrationOk) {
			mLinphoneThreadHandler.post(new Runnable() {
				@Override
				public void run()
				{
					mLinphoneHandler.addFriend(number);
				}
			});
		} else {
			mFriends.add(number);
		}
	}

	public void call(final String number)
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		if (Util.isNullOrEmpty(number)) {
			Log.e(LOGTAG, "call: empty number aborting");
			return;
		}

		if (mRegistrationState != RegistrationState.RegistrationOk) {
			Log.i(LOGTAG, "call: not registered");
			return;
		}

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.call(number);
			}
		});
	}

	public void pickUp()
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.pickUp();
			}
		});
	}

	public void terminateAllCalls()
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.terminateAllCalls();
			}
		});
	}

	public void verifyAuthenticationToken(final String token, final boolean verified)
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.verifyAuthenticationToken(token, verified);
			}
		});
	}

	public void setVolumes(final Volumes volumes)
	{
		if (mLinphoneThreadHandler == null) {
			Log.e(LOGTAG, "handler is null, probably thread not started");
			return;
		}

		if (volumes == null) {
			Log.e(LOGTAG, "volumes is null");
			return;
		}

		mVolumes = volumes;

		mLinphoneThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mLinphoneHandler.setVolumes(volumes);
			}
		});
	}

	public RegistrationState getRegistrationState()
	{
		return mRegistrationState;
	}

	public Volumes getVolumes()
	{
		return mVolumes;
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
	public void registrationState(LinphoneCore lc, final LinphoneProxyConfig cfg, final RegistrationState state, final String message)
	{
		// LinphoneProxyConfig is probably mutable => use it only in the calling thread
		// RegistrationState is immutable

		final String identity = cfg.getIdentity();

		mMainThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				if (mRegistrationState == state) {
					Log.d(LOGTAG, "registration state for " + identity + " not changed : " + state.toString() + " " + message);
					return;
				}

				if (mRegistrationState == RegistrationState.RegistrationOk && state == RegistrationState.RegistrationProgress
						&& message.equals("Refresh registration")) {
					Log.i(LOGTAG, "registration state for " + identity + " ignored: " + state + "as it is because of refreshRegisters");
					return;
				}

				Log.i(LOGTAG, "registration state for " + identity + " changed: " + state.toString() + " " + message);
				mRegistrationState = state;

				mListener.onRegistrationStateChanged(state);

				if (state == RegistrationState.RegistrationOk) {
					for (final String friend : mFriends) {
						mLinphoneThreadHandler.post(new Runnable() {
							@Override
							public void run()
							{
								mLinphoneHandler.addFriend(friend);
							}
						});
					}
					mFriends.clear();
				}
			}
		});

	}

	@Override
	public void displayStatus(LinphoneCore lc, final String message)
	{
		Log.d(LOGTAG, "displayStatus message='" + message + "'");
	}

	LinphoneCall.State fixLinphoneCallState(final LinphoneCall.State callState)
	{
		if (callState == LinphoneCall.State.CallReleased || callState == LinphoneCall.State.Error) {
			if (mLinphoneHandler == null || mLinphoneHandler.hasNoCurrentCalls()) {
				Log.i(LOGTAG, "fixLinphoneCallState: " + callState + " -> " + LinphoneCall.State.CallEnd.toString());
				return LinphoneCall.State.CallEnd;
			}
		}

		return callState;
	}

	@Override
	public void callState(LinphoneCore lc, final LinphoneCall call, final LinphoneCall.State state, final String msg)
	{
		// LinphoneCall is mutable => use it only in the calling thread
		// LinphoneCall.State is immutable

		final String number = getNumber(call);

		Log.i(LOGTAG, "callState call='" + call.toString() + "' state='" + state + "' message='" + msg + "'");
		Log.i(LOGTAG, "AuthenticationToken='" + call.getAuthenticationToken() + "'");
		Log.i(LOGTAG, "RemoteAddress='" + call.getRemoteAddress().asStringUriOnly() + "'");
		Log.i(LOGTAG, "CurrentQuality='" + call.getCurrentQuality() + "' AverageQuality='" + call.getAverageQuality() + "'");
		Log.i(LOGTAG, "CurrentParams='" + call.getCurrentParamsCopy().getUsedAudioCodec() + "'");

		mMainThreadHandler.post(new Runnable() {

			@Override
			public void run()
			{
				int msgId = -1;
				if (msg != null
						&& (state == LinphoneCall.State.CallEnd || state == LinphoneCall.State.Error || state == LinphoneCall.State.CallReleased)) {
					if (msg.equals("Call declined.")) {
						msgId = R.string.call_ended_because_declined;
					} else if (msg.equals("User not found.")) {
						msgId = R.string.call_ended_because_user_offline;
					} else if (msg.equals("Incompatible media parameters.")) {
						msgId = R.string.call_ended_because_incompatible_media;
					}
				}
				mListener.onCallStateChanged(number, fixLinphoneCallState(state), msgId);
			}
		});
	}

	@Override
	public void messageReceived(LinphoneCore lc, final LinphoneChatRoom cr, final LinphoneChatMessage message)
	{
		Log.i(LOGTAG, "messageReceived " + message);
	}

	@Override
	public void show(LinphoneCore lc)
	{
		Log.i(LOGTAG, "show called");
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, final String realm, final String username)
	{
		Log.w(LOGTAG, "authInfoRequested realm='" + realm + "' username='" + username + "'");
	}

	@Override
	public void displayMessage(LinphoneCore lc, final String message)
	{
		Log.i(LOGTAG, "displayMessage message='" + message + "'");
	}

	@Override
	public void displayWarning(LinphoneCore lc, final String message)
	{
		Log.w(LOGTAG, "displayWarning message='" + message + "'");
	}

	@Override
	public void globalState(LinphoneCore lc, final GlobalState state, final String message)
	{
		Log.i(LOGTAG, "globalState state='" + state.toString() + " message='" + message + "'");
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, final LinphoneFriend lf, final String url)
	{
		// LinphoneFriend is mutable => use it only in the calling thread

		final String number = lf.getAddress().getUserName();
		Log.w(LOGTAG, "[" + number + "] wants to see your presence status => always accepting");
		this.addFriend(number);

//		// old style from example which may not be thread-safe
//		Log.i(LOGTAG, "newSubscriptionRequest friend='" + lf.toString() + "' url='" + url + "'");
//		Log.w(LOGTAG, "[" + lf.getAddress().getUserName() + "] wants to see your presence status => always accepting");
//		mLinphoneHandler.addFriend(lf);
//		Log.w(LOGTAG, "[" + lf.getAddress().getUserName() + "] accepted to see your presence status");
	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, final LinphoneFriend lf)
	{
		// LinphoneFriend is mutable => use it only in the calling thread
		// OnlineStatus is immutable

		if (LinphoneHandler.PRESENCE_DISABLED) {
			Log.w(LOGTAG, "notifyPresenceReceived although presence is disabled");
			return;
		}

		final String userName = lf.getAddress().getUserName();
		final boolean online = isOnline(lf.getPresenceModel());

		mMainThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mListener.onPresenceStateChanged(userName, online);
			}
		});
	}

	private static boolean isOnline(final PresenceModel presenceModel)
	{
		if (presenceModel == null) {
			Log.w(LOGTAG, "isOnline: no PresenceModel");
			return false;
		}

		final PresenceService service = presenceModel.getNthService(0);
		if (service == null) {
			Log.w(LOGTAG, "isOnline: no PresenceService");
			return false;
		}

		PresenceBasicStatus status = service.getBasicStatus();
		if (status == null) {
			return false;
		}

		//Log.i(LOGTAG, "NbServices" + presenceModel.getNbServices());

		for (long i = presenceModel.getNbServices() - 1; i >= 0; --i) {
			Log.w(LOGTAG, "Service " + presenceModel.getNthService(i).getBasicStatus());
			if (presenceModel.getNthService(i).getContact() == null) {
				status = presenceModel.getNthService(i).getBasicStatus();
				Log.w(LOGTAG, "using serivce no " + i);
			}
		}

		Log.i(LOGTAG, "PresenceBasicStatus: " + status);

		return status.equals(PresenceBasicStatus.Open);
	}

	@Override
	public void textReceived(LinphoneCore lc, final LinphoneChatRoom cr, final LinphoneAddress from, final String message)
	{
		Log.i(LOGTAG, "textReceived chatroom='" + cr + "' from='" + from.toString() + "' message='" + message);
	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, final LinphoneCall call, final LinphoneCallStats stats)
	{
		// LinphoneCall is mutable => use it only in the calling thread
		// LinphoneCallStats maybe mutable => use it only in the calling thread

		Log.i(LOGTAG, "callStatsUpdated call='" + call.toString() + "' stats='" + stats.toString() + "'");

		final float upload = stats.getUploadBandwidth() / 8.0f;
		final float download = stats.getDownloadBandwidth() / 8.0f;
		final float quality = call.getCurrentQuality();
		final PayloadType payloadType = call.getCurrentParamsCopy().getUsedAudioCodec();
		final String codec = payloadType.getMime() + " " + payloadType.getRate() / 1000;
		final String iceState = stats.getIceState().toString();

		Log.i(LOGTAG, "upload=" + upload + " download=" + download + " quality=" + quality + " codec=" + codec + " iceState=" + iceState);

		mMainThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mListener.onCallStatsChanged(upload, download, quality, codec, iceState);
			}
		});
	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, final EcCalibratorStatus status, final int delay_ms, final Object data)
	{
		Log.i(LOGTAG, "ecCalibrationStatus status='" + status.toString() + "' delay_ms='" + delay_ms + "'");
	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, final LinphoneCall call, final boolean encrypted, final String authenticationToken)
	{
		// LinphoneCall is mutable => use it only in the calling thread

		final boolean isTokenVerified = call.isAuthenticationTokenVerified();

		Log.i(LOGTAG, "callEncryptionChanged call='" + call.toString() + "' encrypted='" + encrypted + "' authenticationToken='"
				+ authenticationToken + "'");

		if (!encrypted) {
			Log.e(LOGTAG, "unencrypted call: " + call.getRemoteAddress().asStringUriOnly() + " with UserAgent " + call.getRemoteUserAgent());
		}

		mMainThreadHandler.post(new Runnable() {
			@Override
			public void run()
			{
				mListener.onCallEncryptionChanged(encrypted, authenticationToken, isTokenVerified);
			}
		});
	}

	@Override
	public void notifyReceived(LinphoneCore lc, final LinphoneCall call, final LinphoneAddress from, final byte[] event)
	{
		Log.i(LOGTAG, "notifyReceived call='" + call.toString() + "' from='" + from.toString());
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, final LinphoneCall call, final int dtmf)
	{
		Log.i(LOGTAG, "dtmfReceived call='" + call.toString() + "' dtmf='" + dtmf + "'");
	}

	@Override
	public void transferState(LinphoneCore lc, final LinphoneCall call, final LinphoneCall.State state)
	{
		Log.w(LOGTAG, "transferState call=" + call + " State=" + state);
	}

	@Override
	public void infoReceived(LinphoneCore lc, final LinphoneCall call, final LinphoneInfoMessage info)
	{
		Log.w(LOGTAG, "infoReceived call=" + call + " LinphoneInfoMessage=" + info.getContent().getDataAsString());
	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, final LinphoneEvent ev, final SubscriptionState state)
	{
		Log.w(LOGTAG, "subscriptionStateChanged ev=" + ev.getEventName() + " SubscriptionState=" + state);
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, final String eventName, final LinphoneContent content)
	{
		Log.w(LOGTAG, "notifyReceived ev=" + ev.getEventName() + " eventName=" + eventName + " content=" + content);
	}

	@Override
	public void publishStateChanged(LinphoneCore lc, final LinphoneEvent ev, final PublishState state)
	{
		Log.w(LOGTAG, "publishStateChanged ev=" + ev.getEventName() + " state=" + state);
	}
}

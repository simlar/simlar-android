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

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.FirewallPolicy;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;

public class LinphoneHandler
{
	// liblinphone version since belle-sip suffer from io errors while adding friends
	// like before presence is not working properly with kamailio so it is disabled at the moment
	// liblinphone git revision: a7466b990d270a5336307b2b3235f22137500782
	public static final boolean PRESENCE_DISABLED = true;

	private static final String LOGTAG = LinphoneHandler.class.getSimpleName();

	private static final String DOMAIN = "sip.simlar.org";
	private static final String STUN_SERVER = "stun.simlar.org";

	private LinphoneCore mLinphoneCore = null;

	public LinphoneHandler()
	{
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void doDestroy()
	{
		Log.i(LOGTAG, "doDestroy called");
		destroy();
	}

	public void destroy()
	{
		Log.i(LOGTAG, "destroy called => forcing unregister");

		if (mLinphoneCore != null) {
			try {
				mLinphoneCore.destroy();
			} catch (RuntimeException e) {
				Log.e(LOGTAG, "RuntimeException during mLinphoneCore.destroy()", e);
			} finally {
				mLinphoneCore = null;
			}
		}
		Log.i(LOGTAG, "destroy ended");
	}

	private void enableAudioCodec(String codec, int rate, int channels, boolean enable)
	{
		PayloadType pt = mLinphoneCore.findPayloadType(codec, rate, channels);
		if (pt != null) {
			try {
				mLinphoneCore.enablePayloadType(pt, enable);
				Log.v(LOGTAG, "AudioCodec: codec=" + codec + " rate=" + rate + " channels=" + channels + " enable=" + enable);
			} catch (LinphoneCoreException e) {
				Log.e(LOGTAG, "LinphoneCoreException during enabling Audio Codec: " + e.getMessage(), e);
			}
		} else {
			Log.w(LOGTAG, "AudioCodec: payload not found for codec=" + codec + " rate=" + rate);
		}
	}

	public boolean isInitialized()
	{
		return mLinphoneCore != null;
	}

	public void initialize(LinphoneCoreListener listener, Context context, final String linphoneInitialConfigFile, final String rootCaFile,
			final String zrtpSecretsCacheFile)
	{
		if (listener == null) {
			Log.e(LOGTAG, "Error: initialize without listener");
			return;
		}

		if (mLinphoneCore != null) {
			Log.e(LOGTAG, "Error: already initialized");
			return;
		}

		if (Util.isNullOrEmpty(linphoneInitialConfigFile)) {
			Log.e(LOGTAG, "Error: linphoneInitialConfigFile not set");
			return;
		}

		if (Util.isNullOrEmpty(rootCaFile)) {
			Log.e(LOGTAG, "Error: rootCaFile not set");
			return;
		}

		if (Util.isNullOrEmpty(zrtpSecretsCacheFile)) {
			Log.e(LOGTAG, "Error: zrtpSecretsCacheFile not set");
			return;
		}

		try {
			Log.i(LOGTAG, "initialize linphone");

			enableDebugMode(false);

			// First instantiate the core Linphone object given only a listener.
			// The listener will react to events in Linphone core.
			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(listener, "", linphoneInitialConfigFile, null);
			mLinphoneCore.setContext(context);
			mLinphoneCore.setUserAgent("Simlar", "0.0.0");

			// enable STUN with ICE
			mLinphoneCore.setStunServer(STUN_SERVER);
			mLinphoneCore.setFirewallPolicy(FirewallPolicy.UseIce);

			// Use TLS for registration with random port
			Transports transports = new Transports();
			transports.udp = 0;
			transports.tcp = 0;
			transports.tls = (int) (Math.random() * (0xFFFF - 1024)) + 1024;
			mLinphoneCore.setSignalingTransportPorts(transports);
			Log.i(LOGTAG, "using random port: " + transports.tls);

			// set audio port range
			mLinphoneCore.setAudioPortRange(6000, 8000);

			// CA file
			mLinphoneCore.setRootCA(rootCaFile);

			// enable zrtp
			mLinphoneCore.setMediaEncryption(MediaEncryption.ZRTP);
			mLinphoneCore.setZrtpSecretsCache(zrtpSecretsCacheFile);

			// Audio Codecs
			enableAudioCodec("speex", 32000, 1, false);
			enableAudioCodec("speex", 16000, 1, false);
			enableAudioCodec("speex", 8000, 1, false);
			enableAudioCodec("iLBC", 8000, 1, false);
			enableAudioCodec("GSM", 8000, 1, false);
			enableAudioCodec("G722", 8000, 1, false);
			//enableDisableAudioCodec("G729", 8000, 1, true);
			enableAudioCodec("PCMU", 8000, 1, false);
			enableAudioCodec("PCMA", 8000, 1, false);
			enableAudioCodec("AMR", 8000, 1, false);
			//enableDisableAudioCodec("AMR-WB", 16000, 1, true);
			enableAudioCodec("SILK", 24000, 1, false);
			enableAudioCodec("SILK", 16000, 1, true);
			enableAudioCodec("SILK", 12000, 1, false);
			enableAudioCodec("SILK", 8000, 1, true);
			enableAudioCodec("OPUS", 48000, 1, true);

			// enable echo cancellation
			mLinphoneCore.enableEchoCancellation(true);
			mLinphoneCore.enableEchoLimiter(true);

			// disable video
			mLinphoneCore.enableVideo(false, false);
			mLinphoneCore.setVideoPolicy(false, false);

			// set number of threads for MediaStreamer
			final int cpuCount = Runtime.getRuntime().availableProcessors();
			Log.i(LOGTAG, "Threads for MediaStreamer: " + cpuCount);
			mLinphoneCore.setCpuCount(cpuCount);

			// make sure we only handle one call
			mLinphoneCore.setMaxCalls(1);
		} catch (LinphoneCoreException e) {
			Log.e(LOGTAG, "LinphoneCoreException during initialize: " + e.getMessage(), e);
		}
	}

	void linphoneCoreIterate()
	{
		mLinphoneCore.iterate();
	}

	public void refreshRegisters()
	{
		if (!isInitialized()) {
			Log.i(LOGTAG, "refreshRegisters called but linphoneCore not initialized");
			return;
		}

		Log.i(LOGTAG, "refreshRegisters");
		mLinphoneCore.refreshRegisters();
	}

	public void setCredentials(final String mySimlarId, final String password)
	{
		if (mLinphoneCore == null) {
			Log.e(LOGTAG, "setCredentials called with: mLinphoneCore == null");
			return;
		}

		if (Util.isNullOrEmpty(mySimlarId)) {
			Log.e(LOGTAG, "setCredentials called with empty mySimlarId");
			return;
		}

		if (Util.isNullOrEmpty(password)) {
			Log.e(LOGTAG, "setCredentials called with empty password");
			return;
		}

		try {
			Log.i(LOGTAG, "registering: " + mySimlarId);

			mLinphoneCore.clearAuthInfos();
			mLinphoneCore.addAuthInfo(LinphoneCoreFactory.instance().createAuthInfo(mySimlarId, password, DOMAIN, DOMAIN));

			// create linphone proxy config
			mLinphoneCore.clearProxyConfigs();
			LinphoneProxyConfig proxyCfg = LinphoneCoreFactory.instance().createProxyConfig(
					"sip:" + mySimlarId + "@" + DOMAIN, "sip:" + DOMAIN, null, true);
			proxyCfg.setExpires(1800); // kamailio setting is 3600
			proxyCfg.enablePublish(!PRESENCE_DISABLED);
			mLinphoneCore.addProxyConfig(proxyCfg);
			mLinphoneCore.setDefaultProxyConfig(proxyCfg);
		} catch (LinphoneCoreException e) {
			Log.e(LOGTAG, "LinphoneCoreException during setCredentials: " + e.getMessage(), e);
		}
	}

	public void unregister()
	{
		Log.i(LOGTAG, "unregister triggered");

		LinphoneProxyConfig proxyConfig = mLinphoneCore.getDefaultProxyConfig();
		proxyConfig.edit();
		try {
			proxyConfig.enableRegister(false);
		} catch (LinphoneCoreException e) {
			Log.e(LOGTAG, "LinphoneCoreException during unregister: " + e.getMessage(), e);
		}
		proxyConfig.done();
	}

	public void addFriend(final LinphoneFriend lf)
	{
		if (PRESENCE_DISABLED) {
			return;
		}

		if (lf == null) {
			Log.e(LOGTAG, "addFriend without linphone friend");
			return;
		}

		lf.enableSubscribes(true);
		try {
			mLinphoneCore.addFriend(lf);
		} catch (LinphoneCoreException e) {
			Log.e(LOGTAG, "LinphoneCoreException: during addFriend [" + lf.getAddress().getUserName() + "]: " + e.getMessage(), e);
		}
	}

	public void addFriend(final String number)
	{
		if (PRESENCE_DISABLED) {
			return;
		}

		if (Util.isNullOrEmpty(number)) {
			Log.e(LOGTAG, "addFriend empty number");
			return;
		}

		final String sipUrl = "sip:" + number + "@" + DOMAIN;
		if (mLinphoneCore.findFriendByAddress(sipUrl) != null) {
			Log.i(LOGTAG, "presence already registered: " + sipUrl);
			return;
		}

		addFriend(LinphoneCoreFactory.instance().createLinphoneFriend(sipUrl));

		Log.i(LOGTAG, "successfully added friend: " + number);
	}

	public void call(final String number)
	{
		if (Util.isNullOrEmpty(number)) {
			Log.e(LOGTAG, "call: empty number aborting");
			return;
		}

		Log.i(LOGTAG, "calling " + number);
		try {
			LinphoneCall call = mLinphoneCore.invite("sip:" + number + "@" + DOMAIN);
			if (call == null) {
				Log.i(LOGTAG, "Could not place call to: " + number);
				Log.i(LOGTAG, "Aborting");
				return;
			}
		} catch (LinphoneCoreException e) {
			Log.e(LOGTAG, "LinphoneCoreException during invite: " + e.getMessage(), e);
			return;
		}

		Log.i(LOGTAG, "Call to " + number + " is in progress...");
	}

	private LinphoneCall getCurrentCall()
	{
		if (mLinphoneCore == null) {
			return null;
		}
		return mLinphoneCore.getCurrentCall();
	}

	public void pickUp()
	{
		LinphoneCall currentCall = getCurrentCall();
		if (currentCall == null) {
			return;
		}

		Log.i(LOGTAG, "Picking up call: " + currentCall.getRemoteAddress().asStringUriOnly());
		LinphoneCallParams params = mLinphoneCore.createDefaultCallParameters();
		try {
			mLinphoneCore.acceptCallWithParams(currentCall, params);
		} catch (LinphoneCoreException e) {
			Log.e(LOGTAG, "LinphoneCoreException during acceptCallWithParams: " + e.getMessage(), e);
		}
	}

	public void terminateAllCalls()
	{
		Log.i(LOGTAG, "terminating all calls");
		mLinphoneCore.terminateAllCalls();
	}

	public void verifyAuthenticationToken(final String token, final boolean verified)
	{
		if (Util.isNullOrEmpty(token)) {
			Log.e(LOGTAG, "ERROR in verifyAuthenticationToken: empty token");
			return;
		}

		LinphoneCall call = mLinphoneCore.getCurrentCall();

		if (!token.equals(call.getAuthenticationToken())) {
			Log.e(LOGTAG, "ERROR in verifyAuthenticationToken: token(" + token +
					") does not match current calls token(" + call.getAuthenticationToken() + ")");
			return;
		}

		call.setAuthenticationTokenVerified(verified);
	}

	public boolean hasNoCurrentCalls()
	{
		return mLinphoneCore.getCallsNb() == 0;
	}

	public void setVolumes(final Volumes volumes)
	{
		if (mLinphoneCore == null) {
			Log.e(LOGTAG, "setVolumes: mLinphoneCore is null => aborting");
			return;
		}

		if (volumes == null) {
			Log.e(LOGTAG, "setVolumes: volumes is null => aborting");
			return;
		}

		mLinphoneCore.setPlaybackGain(volumes.getPlayGain());
		mLinphoneCore.setMicrophoneGain(volumes.getMicrophoneGain());

		mLinphoneCore.enableSpeaker(volumes.getExternalSpeaker());
		mLinphoneCore.muteMic(volumes.getMicrophoneMuted());

		Log.i(LOGTAG, "volumes set " + volumes);
	}

	public static void enableDebugMode(final boolean enabled)
	{
		LinphoneCoreFactory.instance().setDebugMode(enabled, "DEBUG");
	}
}

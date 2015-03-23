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
import org.linphone.core.LinphoneProxyConfig;

import android.content.Context;

final class LinphoneHandler
{
	private static final String LOGTAG = LinphoneHandler.class.getSimpleName();

	private static final String DOMAIN = "sip.simlar.org";
	private static final String STUN_SERVER = "stun.simlar.org";

	private LinphoneCore mLinphoneCore = null;

	public LinphoneHandler()
	{
	}

	public void destroy()
	{
		Lg.i(LOGTAG, "destroy called => forcing unregister");

		if (mLinphoneCore != null) {
			final LinphoneCore tmp = mLinphoneCore;
			mLinphoneCore = null;
			try {
				tmp.destroy();
			} catch (final RuntimeException e) {
				Lg.ex(LOGTAG, e, "RuntimeException during mLinphoneCore.destroy()");
			}
		}
		Lg.i(LOGTAG, "destroy ended");
	}

	public boolean isInitialized()
	{
		return mLinphoneCore != null;
	}

	public void initialize(final LinphoneCoreListener listener, final Context context, final String linphoneInitialConfigFile,
			final String rootCaFile, final String zrtpSecretsCacheFile, final String pauseSoundFile)
	{
		if (listener == null) {
			Lg.e(LOGTAG, "Error: initialize without listener");
			return;
		}

		if (mLinphoneCore != null) {
			Lg.e(LOGTAG, "Error: already initialized");
			return;
		}

		if (Util.isNullOrEmpty(linphoneInitialConfigFile)) {
			Lg.e(LOGTAG, "Error: linphoneInitialConfigFile not set");
			return;
		}

		if (Util.isNullOrEmpty(rootCaFile)) {
			Lg.e(LOGTAG, "Error: rootCaFile not set");
			return;
		}

		if (Util.isNullOrEmpty(zrtpSecretsCacheFile)) {
			Lg.e(LOGTAG, "Error: zrtpSecretsCacheFile not set");
			return;
		}

		if (Util.isNullOrEmpty(pauseSoundFile)) {
			Lg.e(LOGTAG, "Error: pauseSoundFile not set");
			return;
		}

		try {
			Lg.i(LOGTAG, "initialize linphone");

			enableDebugMode(false);

			// First instantiate the core Linphone object given only a listener.
			// The listener will react to events in Linphone core.
			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(listener, "", linphoneInitialConfigFile, null, context);
			mLinphoneCore.setContext(context);
			mLinphoneCore.setUserAgent("Simlar", Version.getVersionName(context));

			// enable STUN with ICE
			mLinphoneCore.setStunServer(STUN_SERVER);
			mLinphoneCore.setFirewallPolicy(FirewallPolicy.UseIce);

			// Use TLS for registration with random port
			Transports transports = new Transports();
			transports.udp = 0;
			transports.tcp = 0;
			transports.tls = (int) (Math.random() * (0xFFFF - 1024)) + 1024;
			mLinphoneCore.setSignalingTransportPorts(transports);
			Lg.i(LOGTAG, "using random port: ", transports.tls);

			// set audio port range
			mLinphoneCore.setAudioPortRange(6000, 8000);

			// CA file
			mLinphoneCore.setRootCA(rootCaFile);

			// enable zrtp
			mLinphoneCore.setMediaEncryption(MediaEncryption.ZRTP);
			mLinphoneCore.setZrtpSecretsCache(zrtpSecretsCacheFile);

			// pause sound file
			mLinphoneCore.setPlayFile(pauseSoundFile);

			// enable echo cancellation
			mLinphoneCore.enableEchoCancellation(true);
			mLinphoneCore.enableEchoLimiter(false);

			// disable video
			mLinphoneCore.enableVideo(false, false);
			mLinphoneCore.setVideoPolicy(false, false);

			// set number of threads for MediaStreamer
			final int cpuCount = Runtime.getRuntime().availableProcessors();
			Lg.i(LOGTAG, "Threads for MediaStreamer: ", cpuCount);
			mLinphoneCore.setCpuCount(cpuCount);

			// We do not want a call response with "486 busy here" if you are not on the phone. So we take a high value of 1 hour.
			// The Simlar sip server is responsible for terminating a call. Right now it does that after 2 minutes.
			mLinphoneCore.setIncomingTimeout(3600);

			// make sure we only handle one call
			mLinphoneCore.setMaxCalls(1);
		} catch (final LinphoneCoreException e) {
			Lg.ex(LOGTAG, e, "LinphoneCoreException during initialize");
		}
	}

	void linphoneCoreIterate()
	{
		mLinphoneCore.iterate();
	}

	public void refreshRegisters()
	{
		if (!isInitialized()) {
			Lg.i(LOGTAG, "refreshRegisters called but linphoneCore not initialized");
			return;
		}

		Lg.i(LOGTAG, "refreshRegisters");
		mLinphoneCore.refreshRegisters();
	}

	public void setCredentials(final String mySimlarId, final String password)
	{
		if (mLinphoneCore == null) {
			Lg.e(LOGTAG, "setCredentials called with: mLinphoneCore == null");
			return;
		}

		if (Util.isNullOrEmpty(mySimlarId)) {
			Lg.e(LOGTAG, "setCredentials called with empty mySimlarId");
			return;
		}

		if (Util.isNullOrEmpty(password)) {
			Lg.e(LOGTAG, "setCredentials called with empty password");
			return;
		}

		try {
			Lg.i(LOGTAG, "registering: ", new Lg.Anonymizer(mySimlarId));

			mLinphoneCore.clearAuthInfos();
			mLinphoneCore.addAuthInfo(LinphoneCoreFactory.instance().createAuthInfo(mySimlarId, password, DOMAIN, DOMAIN));

			// create linphone proxy config
			mLinphoneCore.clearProxyConfigs();
			LinphoneProxyConfig proxyCfg = mLinphoneCore.createProxyConfig(
					"sip:" + mySimlarId + "@" + DOMAIN, "sip:" + DOMAIN, null, true);
			proxyCfg.setExpires(60); // connection times out after 1 minute. This overrides kamailio setting which is 3600 (1 hour).
			proxyCfg.enablePublish(false);
			mLinphoneCore.addProxyConfig(proxyCfg);
			mLinphoneCore.setDefaultProxyConfig(proxyCfg);
		} catch (final LinphoneCoreException e) {
			Lg.ex(LOGTAG, e, "LinphoneCoreException during setCredentials");
		}
	}

	public void unregister()
	{
		Lg.i(LOGTAG, "unregister triggered");

		final LinphoneProxyConfig proxyConfig = mLinphoneCore.getDefaultProxyConfig();
		proxyConfig.edit();
		proxyConfig.enableRegister(false);
		proxyConfig.done();
	}

	public void call(final String number)
	{
		if (Util.isNullOrEmpty(number)) {
			Lg.e(LOGTAG, "call: empty number aborting");
			return;
		}

		Lg.i(LOGTAG, "calling ", new Lg.Anonymizer(number));
		try {
			final LinphoneCall call = mLinphoneCore.invite("sip:" + number + "@" + DOMAIN);
			if (call == null) {
				Lg.i(LOGTAG, "Could not place call to: ", new Lg.Anonymizer(number));
				Lg.i(LOGTAG, "Aborting");
				return;
			}
		} catch (final LinphoneCoreException e) {
			Lg.ex(LOGTAG, e, "LinphoneCoreException during invite");
			return;
		}

		Lg.i(LOGTAG, "Call to ", new Lg.Anonymizer(number), " is in progress...");
	}

	public LinphoneCall getCurrentCall()
	{
		/// NOTE LinphoneCore.getCurrentCall() does not return paused calls

		if (hasNoCurrentCalls()) {
			return null;
		}

		return mLinphoneCore.getCalls()[0];
	}

	public void pickUp()
	{
		final LinphoneCall currentCall = getCurrentCall();
		if (currentCall == null) {
			return;
		}

		Lg.i(LOGTAG, "Picking up call: ", new Lg.Anonymizer(currentCall.getRemoteAddress().asStringUriOnly()));
		final LinphoneCallParams params = mLinphoneCore.createDefaultCallParameters();
		try {
			mLinphoneCore.acceptCallWithParams(currentCall, params);
		} catch (final LinphoneCoreException e) {
			Lg.ex(LOGTAG, e, "LinphoneCoreException during acceptCallWithParams");
		}
	}

	public void terminateAllCalls()
	{
		Lg.i(LOGTAG, "terminating all calls");
		mLinphoneCore.terminateAllCalls();
	}

	public void verifyAuthenticationToken(final String token, final boolean verified)
	{
		if (Util.isNullOrEmpty(token)) {
			Lg.e(LOGTAG, "ERROR in verifyAuthenticationToken: empty token");
			return;
		}

		final LinphoneCall call = mLinphoneCore.getCurrentCall();

		if (!token.equals(call.getAuthenticationToken())) {
			Lg.e(LOGTAG, "ERROR in verifyAuthenticationToken: token(", token,
					") does not match token of current call(", call.getAuthenticationToken(), ")");
			return;
		}

		call.setAuthenticationTokenVerified(verified);
	}

	public boolean hasNoCurrentCalls()
	{
		return mLinphoneCore == null || mLinphoneCore.getCallsNb() == 0;
	}

	public void pauseAllCalls()
	{
		if (mLinphoneCore == null) {
			Lg.e(LOGTAG, "pauseAllCalls: mLinphoneCore is null => aborting");
			return;
		}

		Lg.i(LOGTAG, "pausing all calls");
		mLinphoneCore.pauseAllCalls();
	}

	public void resumeCall()
	{
		if (mLinphoneCore == null) {
			Lg.e(LOGTAG, "resumeCall: mLinphoneCore is null => aborting");
			return;
		}

		final LinphoneCall call = getCurrentCall();
		if (call == null) {
			Lg.e(LOGTAG, "resuming call but no current call");
			return;
		}

		Lg.i(LOGTAG, "resuming call");
		mLinphoneCore.resumeCall(call);
	}

	public void setVolumes(final Volumes volumes)
	{
		if (mLinphoneCore == null) {
			Lg.e(LOGTAG, "setVolumes: mLinphoneCore is null => aborting");
			return;
		}

		if (volumes == null) {
			Lg.e(LOGTAG, "setVolumes: volumes is null => aborting");
			return;
		}

		mLinphoneCore.setPlaybackGain(volumes.getPlayGain());
		mLinphoneCore.setMicrophoneGain(volumes.getMicrophoneGain());

		mLinphoneCore.enableSpeaker(volumes.getExternalSpeaker());
		mLinphoneCore.muteMic(volumes.getMicrophoneMuted());

		setEchoLimiter(volumes.getEchoLimiter());

		Lg.i(LOGTAG, "volumes set ", volumes);
	}

	private void setEchoLimiter(final boolean enable)
	{
		final LinphoneCall currentCall = getCurrentCall();
		if (currentCall == null) {
			Lg.w(LOGTAG, "EchoLimiter no current call");
			return;
		}

		if (currentCall.isEchoLimiterEnabled() == enable) {
			Lg.i(LOGTAG, "EchoLimiter already: ", Boolean.toString(enable));
			return;
		}

		Lg.i(LOGTAG, "set EchoLimiter: ", Boolean.toString(enable));
		currentCall.enableEchoLimiter(enable);
	}

	private static void enableDebugMode(final boolean enabled)
	{
		LinphoneCoreFactory.instance().setDebugMode(enabled, "DEBUG");
	}
}

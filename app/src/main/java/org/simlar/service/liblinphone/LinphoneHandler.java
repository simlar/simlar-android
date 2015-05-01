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
import org.simlar.helper.ServerSettings;
import org.simlar.helper.Version;
import org.simlar.helper.Volumes;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

final class LinphoneHandler
{
	private static final String STUN_SERVER = "stun.simlar.org";

	private LinphoneCore mLinphoneCore = null;

	public LinphoneHandler()
	{
	}

	public void destroy()
	{
		Lg.i("destroy called => forcing unregister");

		if (mLinphoneCore != null) {
			final LinphoneCore tmp = mLinphoneCore;
			mLinphoneCore = null;
			try {
				tmp.destroy();
			} catch (final RuntimeException e) {
				Lg.ex(e, "RuntimeException during mLinphoneCore.destroy()");
			}
		}
		Lg.i("destroy ended");
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isInitialized()
	{
		return mLinphoneCore != null;
	}

	public void initialize(final LinphoneCoreListener listener, final Context context, final String linphoneInitialConfigFile,
	                       final String rootCaFile, final String zrtpSecretsCacheFile, final String ringbackSoundFile, final String pauseSoundFile)
	{
		if (listener == null) {
			Lg.e("Error: initialize without listener");
			return;
		}

		if (mLinphoneCore != null) {
			Lg.e("Error: already initialized");
			return;
		}

		if (Util.isNullOrEmpty(linphoneInitialConfigFile)) {
			Lg.e("Error: linphoneInitialConfigFile not set");
			return;
		}

		if (Util.isNullOrEmpty(rootCaFile)) {
			Lg.e("Error: rootCaFile not set");
			return;
		}

		if (Util.isNullOrEmpty(zrtpSecretsCacheFile)) {
			Lg.e("Error: zrtpSecretsCacheFile not set");
			return;
		}

		if (Util.isNullOrEmpty(pauseSoundFile)) {
			Lg.e("Error: pauseSoundFile not set");
			return;
		}

		try {
			Lg.i("initialize linphone");

			enableDebugMode(false);

			// First instantiate the core Linphone object given only a listener.
			// The listener will react to events in Linphone core.
			mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(listener, null, linphoneInitialConfigFile, null, context);
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
			Lg.i("using random port: ", transports.tls);

			// set audio port range
			mLinphoneCore.setAudioPortRange(6000, 8000);

			// CA file
			mLinphoneCore.setRootCA(rootCaFile);

			// enable zrtp
			mLinphoneCore.setMediaEncryption(MediaEncryption.ZRTP);
			mLinphoneCore.setZrtpSecretsCache(zrtpSecretsCacheFile);
			mLinphoneCore.setMediaEncryptionMandatory(true);

			// set sound files
			mLinphoneCore.setRingback(ringbackSoundFile);
			mLinphoneCore.setPlayFile(pauseSoundFile);

			// enable echo cancellation
			mLinphoneCore.enableEchoCancellation(true);
			mLinphoneCore.enableEchoLimiter(false);

			// disable video
			mLinphoneCore.enableVideo(false, false);
			mLinphoneCore.setVideoPolicy(false, false);

			// set number of threads for MediaStreamer
			final int cpuCount = Runtime.getRuntime().availableProcessors();
			Lg.i("Threads for MediaStreamer: ", cpuCount);
			mLinphoneCore.setCpuCount(cpuCount);

			// We do not want a call response with "486 busy here" if you are not on the phone. So we take a high value of 1 hour.
			// The Simlar sip server is responsible for terminating a call. Right now it does that after 2 minutes.
			mLinphoneCore.setIncomingTimeout(3600);

			// make sure we only handle one call
			mLinphoneCore.setMaxCalls(1);
		} catch (final LinphoneCoreException e) {
			Lg.ex(e, "LinphoneCoreException during initialize");
		}
	}

	void linphoneCoreIterate()
	{
		mLinphoneCore.iterate();
	}

	public void refreshRegisters()
	{
		if (!isInitialized()) {
			Lg.i("refreshRegisters called but linphoneCore not initialized");
			return;
		}

		Lg.i("refreshRegisters");
		mLinphoneCore.refreshRegisters();
	}

	public void setCredentials(final String mySimlarId, final String password)
	{
		if (mLinphoneCore == null) {
			Lg.e("setCredentials called with: mLinphoneCore == null");
			return;
		}

		if (Util.isNullOrEmpty(mySimlarId)) {
			Lg.e("setCredentials called with empty mySimlarId");
			return;
		}

		if (Util.isNullOrEmpty(password)) {
			Lg.e("setCredentials called with empty password");
			return;
		}

		try {
			Lg.i("registering: ", new Lg.Anonymizer(mySimlarId));

			mLinphoneCore.clearAuthInfos();
			mLinphoneCore.addAuthInfo(LinphoneCoreFactory.instance().createAuthInfo(mySimlarId, password, ServerSettings.DOMAIN, ServerSettings.DOMAIN));

			// create linphone proxy config
			mLinphoneCore.clearProxyConfigs();
			LinphoneProxyConfig proxyCfg = mLinphoneCore.createProxyConfig(
					"sip:" + mySimlarId + "@" + ServerSettings.DOMAIN, "sip:" + ServerSettings.DOMAIN, null, true);
			proxyCfg.setExpires(60); // connection times out after 1 minute. This overrides kamailio setting which is 3600 (1 hour).
			proxyCfg.enablePublish(false);
			mLinphoneCore.addProxyConfig(proxyCfg);
			mLinphoneCore.setDefaultProxyConfig(proxyCfg);
		} catch (final LinphoneCoreException e) {
			Lg.ex(e, "LinphoneCoreException during setCredentials");
		}
	}

	public void unregister()
	{
		Lg.i("unregister triggered");

		final LinphoneProxyConfig proxyConfig = mLinphoneCore.getDefaultProxyConfig();
		proxyConfig.edit();
		proxyConfig.enableRegister(false);
		proxyConfig.done();
	}

	public void call(final String number)
	{
		if (Util.isNullOrEmpty(number)) {
			Lg.e("call: empty number aborting");
			return;
		}

		Lg.i("calling ", new Lg.Anonymizer(number));
		try {
			final LinphoneCall call = mLinphoneCore.invite("sip:" + number + "@" + ServerSettings.DOMAIN);
			if (call == null) {
				Lg.i("Could not place call to: ", new Lg.Anonymizer(number));
				Lg.i("Aborting");
				return;
			}
		} catch (final LinphoneCoreException e) {
			Lg.ex(e, "LinphoneCoreException during invite");
			return;
		}

		Lg.i("Call to ", new Lg.Anonymizer(number), " is in progress...");
	}

	private LinphoneCall getCurrentCall()
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

		Lg.i("Picking up call: ", new Lg.Anonymizer(currentCall.getRemoteAddress().asStringUriOnly()));
		final LinphoneCallParams params = mLinphoneCore.createDefaultCallParameters();
		try {
			mLinphoneCore.acceptCallWithParams(currentCall, params);
		} catch (final LinphoneCoreException e) {
			Lg.ex(e, "LinphoneCoreException during acceptCallWithParams");
		}
	}

	public void terminateAllCalls()
	{
		Lg.i("terminating all calls");
		mLinphoneCore.terminateAllCalls();
	}

	public void verifyAuthenticationToken(final String token, final boolean verified)
	{
		if (Util.isNullOrEmpty(token)) {
			Lg.e("ERROR in verifyAuthenticationToken: empty token");
			return;
		}

		final LinphoneCall call = mLinphoneCore.getCurrentCall();

		if (!token.equals(call.getAuthenticationToken())) {
			Lg.e("ERROR in verifyAuthenticationToken: token(", token,
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
			Lg.e("pauseAllCalls: mLinphoneCore is null => aborting");
			return;
		}

		Lg.i("pausing all calls");
		mLinphoneCore.pauseAllCalls();
	}

	public void resumeCall()
	{
		if (mLinphoneCore == null) {
			Lg.e("resumeCall: mLinphoneCore is null => aborting");
			return;
		}

		final LinphoneCall call = getCurrentCall();
		if (call == null) {
			Lg.e("resuming call but no current call");
			return;
		}

		Lg.i("resuming call");
		mLinphoneCore.resumeCall(call);
	}

	public void setVolumes(final Volumes volumes)
	{
		if (mLinphoneCore == null) {
			Lg.e("setVolumes: mLinphoneCore is null => aborting");
			return;
		}

		if (volumes == null) {
			Lg.e("setVolumes: volumes is null => aborting");
			return;
		}

		mLinphoneCore.setPlaybackGain(volumes.getPlayGain());
		mLinphoneCore.setMicrophoneGain(volumes.getMicrophoneGain());

		mLinphoneCore.enableSpeaker(volumes.getExternalSpeaker());
		mLinphoneCore.muteMic(volumes.getMicrophoneMuted());

		setEchoLimiter(volumes.getEchoLimiter());

		Lg.i("volumes set ", volumes);
	}

	private void setEchoLimiter(final boolean enable)
	{
		final LinphoneCall currentCall = getCurrentCall();
		if (currentCall == null) {
			Lg.w("EchoLimiter no current call");
			return;
		}

		if (currentCall.isEchoLimiterEnabled() == enable) {
			Lg.i("EchoLimiter already: ", Boolean.toString(enable));
			return;
		}

		Lg.i("set EchoLimiter: ", Boolean.toString(enable));
		currentCall.enableEchoLimiter(enable);
	}

	@SuppressWarnings("SameParameterValue")
	private static void enableDebugMode(final boolean enabled)
	{
		LinphoneCoreFactory.instance().setDebugMode(enabled, "DEBUG");
	}
}

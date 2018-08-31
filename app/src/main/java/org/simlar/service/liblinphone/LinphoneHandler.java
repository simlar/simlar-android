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
import android.util.Log;

import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.Core;
import org.linphone.core.LogCollectionState;
import org.linphone.core.LogLevel;
import org.linphone.core.MediaEncryption;
import org.linphone.core.NatPolicy;
import org.linphone.core.Transports;
import org.linphone.core.Factory;
import org.linphone.core.CoreListener;
import org.linphone.core.ProxyConfig;
import org.linphone.core.VideoActivationPolicy;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.simlar.helper.ServerSettings;
import org.simlar.helper.Version;
import org.simlar.helper.Volumes;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.util.Random;

final class LinphoneHandler
{
	private static final String STUN_SERVER = "stun.simlar.org";

	private Core mLinphoneCore = null;

	public void destroy()
	{
		Lg.i("destroy called => forcing unregister");

		if (mLinphoneCore != null) {
			try {
				mLinphoneCore.setNetworkReachable(false);
				mLinphoneCore = null;
			} catch (final RuntimeException e) {
				Lg.ex(e, "RuntimeException during mLinphoneCore destruction");
			}
		}
		Lg.i("destroy ended");
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isInitialized()
	{
		return mLinphoneCore != null;
	}

	public void initialize(final CoreListener listener, final Context context, final String linphoneInitialConfigFile,
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

		Lg.i("initialize linphone");

		enableDebugMode(false);

		// First instantiate the core Linphone object given only a listener.
		// The listener will react to events in Linphone core.
		mLinphoneCore = Factory.instance().createCore(linphoneInitialConfigFile, null, context);
		mLinphoneCore.addListener(listener);
		mLinphoneCore.start();
		mLinphoneCore.setUserAgent("Simlar", Version.getVersionName(context));

		mLinphoneCore.setNatPolicy(createNatPolicy());

		// Use TLS for registration with random port
		final Transports transports = mLinphoneCore.getTransports();
		transports.setUdpPort(0);
		transports.setTcpPort(0);
		transports.setTlsPort(new Random().nextInt(Short.MAX_VALUE - 1023) + 1024);
		mLinphoneCore.setTransports(transports); // liblinphone requires setting transports again.
		Lg.i("using random port: ", transports.getTlsPort());

		// set audio port range
		mLinphoneCore.setAudioPortRange(6000, 8000);

		// CA file
		mLinphoneCore.setRootCa(rootCaFile);

		// enable zrtp
		mLinphoneCore.setMediaEncryption(MediaEncryption.ZRTP);
		mLinphoneCore.setZrtpSecretsFile(zrtpSecretsCacheFile);
		mLinphoneCore.setMediaEncryptionMandatory(true);

		// set sound files
		mLinphoneCore.setRingback(ringbackSoundFile);
		mLinphoneCore.setPlayFile(pauseSoundFile);
		mLinphoneCore.setRing(null);

		// enable echo cancellation
		mLinphoneCore.enableEchoCancellation(true);
		mLinphoneCore.enableEchoLimiter(false);

		// enable video
		if (mLinphoneCore.videoSupported()) {
			mLinphoneCore.enableVideoCapture(true);
			mLinphoneCore.enableVideoDisplay(true);
		} else {
			mLinphoneCore.enableVideoDisplay(false);
			mLinphoneCore.enableVideoCapture(false);
			Lg.e("video not supported by sdk");
		}

		final VideoActivationPolicy videoActivationPolicy = mLinphoneCore.getVideoActivationPolicy();
		videoActivationPolicy.setAutomaticallyInitiate(false);
		videoActivationPolicy.setAutomaticallyAccept(false);
		mLinphoneCore.setVideoActivationPolicy(videoActivationPolicy);

		// We do not want a call response with "486 busy here" if you are not on the phone. So we take a high value of 1 hour.
		// The Simlar sip server is responsible for terminating a call. Right now it does that after 2 minutes.
		mLinphoneCore.setIncTimeout(3600);

		// make sure we only handle one call
		mLinphoneCore.setMaxCalls(1);

		// make sure DNS SRV is disabled
		mLinphoneCore.enableDnsSrv(false);
	}

	private NatPolicy createNatPolicy()
	{
		// enable STUN with ICE
		final NatPolicy natPolicy = mLinphoneCore.createNatPolicy();
		natPolicy.setStunServer(STUN_SERVER);
		natPolicy.enableStun(true);
		natPolicy.enableIce(true);
		natPolicy.enableTurn(false);
		natPolicy.enableUpnp(false);
		return natPolicy;
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

		Lg.i("registering: ", new Lg.Anonymizer(mySimlarId));

		mLinphoneCore.clearAllAuthInfo();
		mLinphoneCore.addAuthInfo(Factory.instance().createAuthInfo(mySimlarId, mySimlarId, password, null, ServerSettings.DOMAIN, ServerSettings.DOMAIN));

		// create linphone proxy config
		mLinphoneCore.clearProxyConfig();
		final ProxyConfig proxyCfg = mLinphoneCore.createProxyConfig();
		proxyCfg.setIdentityAddress(Factory.instance().createAddress("sip:" + mySimlarId + '@' + ServerSettings.DOMAIN));
		proxyCfg.setServerAddr("sip:" + ServerSettings.DOMAIN);
		proxyCfg.enableRegister(true);
		proxyCfg.setExpires(60); // connection times out after 1 minute. This overrides kamailio setting which is 3600 (1 hour).
		proxyCfg.enablePublish(false);
		proxyCfg.setPushNotificationAllowed(false);
		proxyCfg.setNatPolicy(createNatPolicy());

		mLinphoneCore.addProxyConfig(proxyCfg);
		mLinphoneCore.setDefaultProxyConfig(proxyCfg);
	}

	public void unregister()
	{
		Lg.i("unregister triggered");

		final ProxyConfig proxyConfig = mLinphoneCore.getDefaultProxyConfig();
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
		final Call call = mLinphoneCore.invite("sip:" + number + '@' + ServerSettings.DOMAIN);
		if (call == null) {
			Lg.i("Could not place call to: ", new Lg.Anonymizer(number));
			Lg.i("Aborting");
			return;
		}

		Lg.i("Call to ", new Lg.Anonymizer(number), " is in progress...");
	}

	private Call getCurrentCall()
	{
		/// NOTE Core.getCurrentCall() does not return paused calls

		if (hasNoCurrentCalls()) {
			return null;
		}

		return mLinphoneCore.getCalls()[0];
	}

	public void pickUp()
	{
		final Call currentCall = getCurrentCall();
		if (currentCall == null) {
			return;
		}

		Lg.i("Picking up call: ", new Lg.Anonymizer(currentCall.getRemoteAddress().asStringUriOnly()));
		currentCall.accept();
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

		final Call call = mLinphoneCore.getCurrentCall();

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

		final Call call = getCurrentCall();
		if (call == null) {
			Lg.e("resuming call but no current call");
			return;
		}

		Lg.i("resuming call");
		call.resume();
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

		mLinphoneCore.setPlaybackGainDb(volumes.getPlayGain());
		mLinphoneCore.setMicGainDb(volumes.getMicrophoneGain());
		mLinphoneCore.enableMic(!volumes.getMicrophoneMuted());

		setEchoLimiter(volumes.getEchoLimiter());

		Lg.i("volumes set ", volumes);
	}

	private void setEchoLimiter(final boolean enable)
	{
		final Call currentCall = getCurrentCall();
		if (currentCall == null) {
			Lg.w("EchoLimiter no current call");
			return;
		}

		if (currentCall.echoLimiterEnabled() == enable) {
			Lg.i("EchoLimiter already: ", Boolean.toString(enable));
			return;
		}

		Lg.i("set EchoLimiter: ", Boolean.toString(enable));
		currentCall.enableEchoLimiter(enable);
	}

	private static int convertLogLevel(final LogLevel logLevel)
	{
		switch (logLevel) {
		case Debug:
			return Log.VERBOSE;
		case Trace:
			return Log.DEBUG;
		case Message:
			return Log.INFO;
		case Warning:
			return Log.WARN;
		case Error:
			return Log.ERROR;
		case Fatal:
		default:
			return Log.ERROR;
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static void enableDebugMode(final boolean enabled)
	{
		Factory.instance().setDebugMode(enabled, "DEBUG");
		Factory.instance().enableLogCollection(LogCollectionState.EnabledWithoutPreviousLogHandler);
		Factory.instance().getLoggingService().setListener(
				(logService, domain, logLevel, message) -> Lg.log(convertLogLevel(logLevel), "liblinphone ", domain, message));
	}

	public void requestVideoUpdate(final boolean enable)
	{
		Lg.i("requestVideoUpdate: enable=", enable);

		final Call currentCall = getCurrentCall();
		if (currentCall == null) {
			Lg.w("no current call to add video to");
			return;
		}

		final CallParams params = mLinphoneCore.createCallParams(currentCall);
		if (enable && params.videoEnabled()) {
			Lg.i("request enable video with already enabled video => skipping");
			return;
		}

		params.enableVideo(enable);
		currentCall.update(params);
	}

	public static void preventAutoAnswer(final Call currentCall)
	{
		Lg.i("preventAutoAnswer");

		if (currentCall == null) {
			Lg.w("no current call to prevent auto answer for");
			return;
		}

		currentCall.deferUpdate();
	}

	public void acceptVideoUpdate(final boolean accept)
	{
		Lg.i("acceptVideoUpdate accept=", accept);

		final Call currentCall = getCurrentCall();
		if (currentCall == null) {
			Lg.w("no current call to accept video for");
			return;
		}

		final CallParams params = currentCall.getCurrentParams();
		if (accept) {
			params.enableVideo(true);
		}

		currentCall.acceptUpdate(params);
	}

	public void setNativeVideoWindowId(final Object videoWindow)
	{
		Lg.i("setVideoWindow");

		if (mLinphoneCore == null) {
			Lg.e("setVideoWindow: mLinphoneCore is null => aborting");
			return;
		}

		mLinphoneCore.setNativeVideoWindowId(videoWindow);
	}

	public void setVideoPreviewWindow(final Object videoPreviewWindow)
	{
		Lg.i("setVideoPreviewWindow");

		if (mLinphoneCore == null) {
			Lg.e("setVideoPreviewWindow: mLinphoneCore is null => aborting");
			return;
		}

		enableCamera(videoPreviewWindow != null);
		mLinphoneCore.setNativePreviewWindowId(videoPreviewWindow);
	}

	private static int getFrontCameraId()
	{
		for (final AndroidCameraConfiguration.AndroidCamera androidCamera : AndroidCameraConfiguration.retrieveCameras()) {
			if (androidCamera.frontFacing) {
				return androidCamera.id;
			}
		}

		Lg.w("no front facing camera found");
		return 0;
	}

	private void setFrontCameraAsDefault()
	{
		final String[] cameras = mLinphoneCore.getVideoDevicesList();
		if (cameras.length < 1) {
			Lg.w("no camera found");
			return;
		}

		mLinphoneCore.setVideoDevice(cameras[getFrontCameraId()]);
	}

	private void enableCamera(final boolean enable)
	{
		Lg.i("enableCamera: ", enable);

		final Call currentCall = getCurrentCall();
		if (currentCall == null) {
			Lg.w("no current call to enable camera for");
			return;
		}

		currentCall.enableCamera(enable);

		if (enable) {
			setFrontCameraAsDefault();
		}

		currentCall.update(null);
	}

	public void toggleCamera()
	{
		Lg.i("toggleCamera");

		final String[] cameras = mLinphoneCore.getVideoDevicesList();
		if (cameras.length < 1) {
			Lg.i("not enough cameras to toggle through");
			return;
		}

		final Call currentCall = getCurrentCall();
		if (currentCall == null) {
			Lg.w("no current call to toggle camera for");
			return;
		}

		final String currentCamera = mLinphoneCore.getVideoDevice();
		for (int i = 0; i < cameras.length; i++) {
			if (Util.equalString(currentCamera, cameras[i])) {
				final String newCamera = cameras[(i + 1) % cameras.length];
				Lg.i("toggling cameraId: ", currentCamera, " => ", newCamera);
				mLinphoneCore.setVideoDevice(newCamera);
				currentCall.update(null);
				return;
			}
		}

		Lg.e("failed to toggle camera");
	}
}

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

import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.simlar.ContactsProvider.ContactListener;
import org.simlar.PreferencesHelper.NotInitedException;
import org.simlar.SoundEffectManager.SoundEffectType;
import org.simlar.Volumes.MicrophoneStatus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;
import android.widget.Toast;

public final class SimlarService extends Service implements LinphoneThreadListener
{
	static final String LOGTAG = SimlarService.class.getSimpleName();
	private static final int NOTIFICATION_ID = 1;
	private static final long TERMINATE_CHECKER_INTERVAL = 20 * 1000; // milliseconds
	public static final String INTENT_EXTRA_SIMLAR_ID = "SimlarServiceSimlarId";

	LinphoneThread mLinphoneThread = null;
	final Handler mHandler = new Handler();
	private final IBinder mBinder = new SimlarServiceBinder();
	private SimlarStatus mSimlarStatus = SimlarStatus.OFFLINE;
	final SimlarCallState mSimlarCallState = new SimlarCallState();
	private CallConnectionDetails mCallConnectionDetails = new CallConnectionDetails();
	private WakeLock mWakeLock = null;
	private WakeLock mDisplayWakeLock = null;
	private WifiLock mWifiLock = null;
	private boolean mGoingDown = false;
	private boolean mTerminatePrivateAlreadyCalled = false;
	private Class<?> mNotificationActivity = null;
	private VibratorManager mVibratorManager = null;
	private SoundEffectManager mSoundEffectManager = null;
	private boolean mHasAudioFocus = false;
	private final NetworkChangeReceiver mNetworkChangeReceiver = new NetworkChangeReceiver();
	private String mSimlarIdToCall = null;

	public final class SimlarServiceBinder extends Binder
	{
		SimlarService getService()
		{
			return SimlarService.this;
		}
	}

	private final class NetworkChangeReceiver extends BroadcastReceiver
	{
		public NetworkChangeReceiver()
		{
		}

		@Override
		public void onReceive(Context context, Intent intent)
		{
			SimlarService.this.checkNetworkConnectivityAndRefreshRegisters();
		}
	}

	@Override
	public IBinder onBind(final Intent intent)
	{
		Log.i(LOGTAG, "onBind");
		return mBinder;
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		Log.i(LOGTAG, "onStartCommand intent=" + intent + " startId=" + startId);

		Log.i(LOGTAG, "acquiring simlar wake lock");
		acquireWakeLock();
		acquireWifiLock();

		// releasing wakelock of gcm's WakefulBroadcastReceiver if needed
		if (intent != null) {
			WakefulBroadcastReceiver.completeWakefulIntent(intent);
			mSimlarIdToCall = intent.getStringExtra(INTENT_EXTRA_SIMLAR_ID);
			intent.removeExtra(INTENT_EXTRA_SIMLAR_ID);

			// make sure we have a contact name for the CallActivity
			if (!Util.isNullOrEmpty(mSimlarIdToCall)) {
				ContactsProvider.getNameAndPhotoId(mSimlarIdToCall, this, new ContactListener() {
					@Override
					public void onGetNameAndPhotoId(final String name, final String photoId)
					{
						mSimlarCallState.updateContactNameAndImage(name, photoId);
					}
				});
			}
		} else {
			Log.w(LOGTAG, "onStartCommand: with no intent");
			mSimlarIdToCall = null;
		}
		Log.i(LOGTAG, "onStartCommand simlarIdToCall=" + mSimlarIdToCall);

		handlePendingCall();

		// We want this service to continue running until it is explicitly stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onCreate()
	{
		Log.i(LOGTAG, "started with simlar version=" + Version.getVersionName(this)
				+ " on device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ") with android version=" + Build.VERSION.RELEASE);

		FileHelper.init(this);
		mVibratorManager = new VibratorManager(this.getApplicationContext());
		mSoundEffectManager = new SoundEffectManager(this.getApplicationContext());

		mWakeLock = ((PowerManager) this.getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimlarWakeLock");
		mDisplayWakeLock = createDisplayWakeLock();
		mWifiLock = ((WifiManager) this.getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL, "SimlarWifiLock");

		startForeground(NOTIFICATION_ID, createNotification());

		mLinphoneThread = new LinphoneThread(this, this);

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mNetworkChangeReceiver, intentFilter);

		PreferencesHelper.readPrefencesFromFile(this);

		ContactsProvider.preLoadContacts(this);

		mHandler.post(new Runnable() {
			@Override
			public void run()
			{
				initializeCredentials();
			}
		});

		terminateChecker();
	}

	@SuppressWarnings("deprecation")
	private WakeLock createDisplayWakeLock()
	{
		return ((PowerManager) getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "SimlarDisplayWakeLock");
	}

	void terminateChecker()
	{
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run()
			{
				if (terminateCheck()) {
					return;
				}

				terminateChecker();
			}
		}, TERMINATE_CHECKER_INTERVAL);
	}

	protected boolean terminateCheck()
	{
		if (mGoingDown) {
			return true;
		}

		if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
			return false;
		}

		Log.i(LOGTAG, "terminateChecker triggered on status=" + mSimlarStatus);
		handleTerminate();

		return true;
	}

	public void registerActivityToNotification(final Class<?> activity)
	{
		if (activity == null) {
			Log.e(LOGTAG, "registerActivityToNotification with empty activity");
			return;
		}

		Log.i(LOGTAG, "registerActivityToNotification: " + activity.getSimpleName());
		mNotificationActivity = activity;

		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification());
	}

	Notification createNotification()
	{
		if (mNotificationActivity == null) {
			if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
				if (mSimlarCallState.isRinging()) {
					mNotificationActivity = RingingActivity.class;
				} else {
					mNotificationActivity = CallActivity.class;
				}
			} else {
				mNotificationActivity = MainActivity.class;
			}
			Log.i(LOGTAG, "no activity registered based on mSimlarStatus=" + mSimlarStatus + " we now take: " + mNotificationActivity.getSimpleName());
		}

		final PendingIntent activity = PendingIntent.getActivity(this, 0,
				new Intent(this, mNotificationActivity).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), 0);

		final String text = String.format(getString(mSimlarStatus.getNotificationTextId()), PreferencesHelper.getMySimlarIdOrEmptyString());

		final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(mSimlarStatus.getNotificationIcon());
		notificationBuilder.setLargeIcon(mSimlarCallState.getContactPhotoBitmap(this, R.drawable.app_logo));
		notificationBuilder.setContentTitle(getString(R.string.app_name));
		notificationBuilder.setContentText(text);
		notificationBuilder.setTicker(text);
		notificationBuilder.setOngoing(true);
		notificationBuilder.setContentIntent(activity);
		notificationBuilder.setWhen(System.currentTimeMillis());
		return notificationBuilder.build();
	}

	void initializeCredentials()
	{
		notifySimlarStatusChanged(SimlarStatus.OFFLINE);

		if (!PreferencesHelper.readPrefencesFromFile(this)) {
			Log.e(LOGTAG, "failed to initialize credentials");
			return;
		}

		connect();
	}

	public void connect()
	{
		if (mLinphoneThread == null) {
			return;
		}

		notifySimlarStatusChanged(SimlarStatus.CONNECTING);

		try {
			mLinphoneThread.register(PreferencesHelper.getMySimlarId(), PreferencesHelper.getPassword());
		} catch (final NotInitedException e) {
			Log.e(LOGTAG, "PreferencesHelper.NotInitedException", e);
		}
	}

	@Override
	public synchronized void onDestroy()
	{
		Log.i(LOGTAG, "onDestroy");

		mVibratorManager.stop();
		mSoundEffectManager.stopAll();

		unregisterReceiver(mNetworkChangeReceiver);

		// just in case
		releaseWakeLock();
		releaseDisplayWakeLock();
		releaseWifiLock();

		// Tell the user we stopped.
		Toast.makeText(this, R.string.simlarservice_on_destroy, Toast.LENGTH_SHORT).show();

		Log.i(LOGTAG, "onDestroy ended");
	}

	private void acquireWakeLock()
	{
		if (!mWakeLock.isHeld()) {
			mWakeLock.acquire();
		}
	}

	private void acquireDisplayWakeLock()
	{
		if (!mDisplayWakeLock.isHeld()) {
			mDisplayWakeLock.acquire();
		}
	}

	private void acquireWifiLock()
	{
		if (!mWifiLock.isHeld()) {
			mWifiLock.acquire();
		}
	}

	private void releaseWakeLock()
	{
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}

	private void releaseDisplayWakeLock()
	{
		if (mDisplayWakeLock.isHeld()) {
			mDisplayWakeLock.release();
		}
	}

	private void releaseWifiLock()
	{
		if (mWifiLock.isHeld()) {
			mWifiLock.release();
		}
	}

	void checkNetworkConnectivityAndRefreshRegisters()
	{
		final NetworkInfo ni = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

		if (ni == null) {
			Log.e(LOGTAG, "no NetworkInfo found");
			return;
		}

		Log.i(LOGTAG, "NetworkInfo " + ni.getTypeName() + " " + ni.getState());
		if (ni.isConnected()) {
			mLinphoneThread.refreshRegisters();
		}
	}

	@Override
	public void onRegistrationStateChanged(final RegistrationState state)
	{
		Log.i(LOGTAG, "onRegistrationStateChanged: " + state);

		final SimlarStatus status = SimlarStatus.fromRegistrationState(state);

		if (mGoingDown && !status.isConnectedToSipServer()) {
			mHandler.post(new Runnable() {
				@Override
				public void run()
				{
					terminatePrivate();
				}
			});
		}

		notifySimlarStatusChanged(status);
	}

	void notifySimlarStatusChanged(final SimlarStatus status)
	{
		Log.i(LOGTAG, "notifySimlarStatusChanged: " + status);

		mSimlarStatus = status;

		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification());

		SimlarServiceBroadcast.sendSimlarStatusChanged(this);

		handlePendingCall();
	}

	private void handlePendingCall()
	{
		if (getSimlarStatus() != SimlarStatus.ONLINE || Util.isNullOrEmpty(mSimlarIdToCall) || mGoingDown) {
			return;
		}

		final String simlarId = mSimlarIdToCall;
		mSimlarIdToCall = null;
		mHandler.post(new Runnable() {
			@Override
			public void run()
			{
				call(simlarId);
			}
		});
	}

	@Override
	public void onCallStatsChanged(final NetworkQuality quality, final int callDuration, final String codec, final String iceState,
			final int upload, final int download, final int jitter, final int packetLoss, final long latePackets, final int roundTripDelay)
	{
		final boolean simlarCallStateChanged = mSimlarCallState.updateCallStats(quality, callDuration);

		if (mSimlarCallState.isEmpty()) {
			Log.e(LOGTAG, "SimlarCallState is empty: " + mSimlarCallState);
			return;
		}

		if (!simlarCallStateChanged) {
			Log.d(LOGTAG, "SimlarCallState staying the same: " + mSimlarCallState);
		} else {
			Log.i(LOGTAG, "SimlarCallState updated: " + mSimlarCallState);
			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		}

		if (!mCallConnectionDetails.updateCallStats(quality, codec, iceState, upload, download, jitter, packetLoss, latePackets, roundTripDelay)) {
			Log.d(LOGTAG, "CallConnectionDetails staying the same: " + mCallConnectionDetails);
			return;
		}

		Log.i(LOGTAG, "CallConnectionDetails updated: " + mCallConnectionDetails);
		SimlarServiceBroadcast.sendCallConnectionDetailsChanged(this);
	}

	@Override
	public void onCallStateChanged(final String number, final State callState, final String message)
	{
		if (!mSimlarCallState.updateCallStateChanged(number, LinphoneCallState.fromLinphoneCallState(callState), CallEndReason.fromMessage(message))) {
			Log.d(LOGTAG, "SimlarCallState staying the same: " + mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Log.e(LOGTAG, "SimlarCallState is empty: " + mSimlarCallState);
			return;
		}

		Log.i(LOGTAG, "SimlarCallState updated: " + mSimlarCallState);

		if (mSimlarCallState.isRinging()) {
			mSoundEffectManager.start(SoundEffectType.RINGTONE);
			mVibratorManager.start();
		} else {
			mVibratorManager.stop();
			mSoundEffectManager.stop(SoundEffectType.RINGTONE);
		}

		if (mSimlarCallState.isBeforeEncryption()) {
			mLinphoneThread.setMicrophoneStatus(MicrophoneStatus.DISABLED);
			mSoundEffectManager.setInCallMode(true);
			mSoundEffectManager.startPrepared(SoundEffectType.ENCRYPTION_HANDSHAKE);
		}

		// make sure WLAN is not suspended while calling
		if (mSimlarCallState.isNewCall()) {
			mSoundEffectManager.prepare(SoundEffectType.ENCRYPTION_HANDSHAKE);
			notifySimlarStatusChanged(SimlarStatus.ONGOING_CALL);

			mCallConnectionDetails = new CallConnectionDetails();

			if (!mHasAudioFocus) {
				// We acquire AUDIOFOCUS_GAIN_TRANSIENT instead of AUDIOFOCUS_GAIN because we want the music to resume after ringing or call
				final AudioManager audioManger = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManger.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					mHasAudioFocus = true;
					Log.i(LOGTAG, "audio focus granted");
				} else {
					Log.e(LOGTAG, "audio focus not granted");
				}

			}

			if (mSimlarCallState.isRinging()) {
				Log.i(LOGTAG, "starting RingingActivity");
				startActivity(new Intent(SimlarService.this, RingingActivity.class).addFlags(
						Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
			}
		}

		if (mSimlarCallState.isEndedCall()) {
			notifySimlarStatusChanged(SimlarStatus.ONLINE);
			mSoundEffectManager.stopAll();
			mSoundEffectManager.setInCallMode(false);
			if (mHasAudioFocus) {
				final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManager.abandonAudioFocus(null) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					Log.i(LOGTAG, "audio focus released");
				} else {
					Log.e(LOGTAG, "releasing audio focus not granted");
				}
				mHasAudioFocus = false;
			}

			if (mCallConnectionDetails.updateEndedCall()) {
				SimlarServiceBroadcast.sendCallConnectionDetailsChanged(this);
			}

			acquireDisplayWakeLock();
			terminate();
		}

		ContactsProvider.getNameAndPhotoId(number, this, new ContactListener() {
			@Override
			public void onGetNameAndPhotoId(String name, String photoId)
			{
				mSimlarCallState.updateContactNameAndImage(name, photoId);

				final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				nm.notify(NOTIFICATION_ID, createNotification());

				SimlarServiceBroadcast.sendSimlarCallStateChanged(SimlarService.this);
			}
		});
	}

	@Override
	public void onCallEncryptionChanged(final boolean encrypted, final String authenticationToken, final boolean authenticationTokenVerified)
	{
		if (!mSimlarCallState.updateCallEncryption(encrypted, authenticationToken, authenticationTokenVerified)) {
			Log.d(LOGTAG, "callEncryptionChanged but no difference in SimlarCallState: " + mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Log.e(LOGTAG, "callEncryptionChanged but SimlarCallState isEmpty: ");
			return;
		}

		Log.i(LOGTAG, "SimlarCallState updated encryption: " + mSimlarCallState);

		mLinphoneThread.setMicrophoneStatus(MicrophoneStatus.ON);
		mSoundEffectManager.stop(SoundEffectType.ENCRYPTION_HANDSHAKE);

		if (encrypted) {
			// just to be sure
			mVibratorManager.stop();
			mSoundEffectManager.stop(SoundEffectType.UNENCRYPTED_CALL_ALARM);
		} else {
			Log.w(LOGTAG, "unencrypted call");
			mVibratorManager.start();
			mSoundEffectManager.start(SoundEffectType.UNENCRYPTED_CALL_ALARM);
		}

		SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
	}

	public void acceptUnencryptedCall()
	{
		Log.w(LOGTAG, "user accepts unencrypted call");
		mVibratorManager.stop();
		mSoundEffectManager.stop(SoundEffectType.UNENCRYPTED_CALL_ALARM);
	}

	public void call(final String simlarId)
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.call(simlarId);
	}

	public void pickUp()
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.pickUp();
	}

	public void terminateCall()
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.terminateAllCalls();
	}

	public void terminate()
	{
		Log.i(LOGTAG, "terminate");
		mHandler.post(new Runnable() {
			@Override
			public void run()
			{
				handleTerminate();
			}
		});
	}

	void handleTerminate()
	{
		Log.i(LOGTAG, "handleTerminate");
		if (mGoingDown) {
			Log.w(LOGTAG, "handleTerminate: alreaday going down");
			return;
		}
		mGoingDown = true;
		SimlarServiceBroadcast.sendSimlarStatusChanged(this);

		if (mLinphoneThread != null && mSimlarStatus.isConnectedToSipServer()) {
			mLinphoneThread.unregister();

			// make sure terminatePrivate is called after at least 5 seconds
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run()
				{
					terminatePrivate();
				}
			}, 5000);
		} else {
			mHandler.post(new Runnable() {
				@Override
				public void run()
				{
					terminatePrivate();
				}
			});
		}
	}

	void terminatePrivate()
	{
		// make sure this function is only called once
		if (mTerminatePrivateAlreadyCalled) {
			Log.i(LOGTAG, "terminatePrivate already called");
			return;
		}
		mTerminatePrivateAlreadyCalled = true;

		Log.i(LOGTAG, "terminatePrivate");
		if (mLinphoneThread != null) {
			mLinphoneThread.finish();
		} else {
			onJoin();
		}
	}

	@Override
	public void onJoin()
	{
		try {
			mLinphoneThread.join(2000);
		} catch (final InterruptedException e) {
			Log.e(LOGTAG, "join interrupted: " + e.getMessage(), e);
		}
		SimlarServiceBroadcast.sendServiceFinishes(this);

		// make sure the notification update is done before destruction by firing destruction event to the handler
		mHandler.post(new Runnable() {
			@Override
			public void run()
			{
				Log.i(LOGTAG, "onJoin: calling stopSelf");
				stopForeground(true);
				stopSelf();
			}
		});
	}

	public void verifyAuthenticationTokenOfCurrentCall(final boolean verified)
	{
		if (mLinphoneThread == null) {
			Log.e(LOGTAG, "ERROR: verifyAuthenticationToken called but no linphonehandler");
			return;
		}

		if (mSimlarCallState == null || Util.isNullOrEmpty(mSimlarCallState.getAuthenticationToken())) {
			Log.e(LOGTAG, "ERROR: verifyAuthenticationToken called but no token available");
			return;
		}

		mLinphoneThread.verifyAuthenticationToken(mSimlarCallState.getAuthenticationToken(), verified);
	}

	public SimlarStatus getSimlarStatus()
	{
		return mSimlarStatus;
	}

	public boolean isGoingDown()
	{
		return mGoingDown;
	}

	public SimlarCallState getSimlarCallState()
	{
		return mSimlarCallState;
	}

	public Volumes getVolumes()
	{
		if (mLinphoneThread == null) {
			return new Volumes();
		}

		return mLinphoneThread.getVolumes();
	}

	public void setVolumes(final Volumes volumes)
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.setVolumes(volumes);
	}

	public CallConnectionDetails getCallConnectionDetails()
	{
		return mCallConnectionDetails;
	}
}

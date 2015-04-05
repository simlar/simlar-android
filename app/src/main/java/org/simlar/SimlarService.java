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

import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.simlar.ContactsProvider.ContactListener;
import org.simlar.PreferencesHelper.NotInitedException;
import org.simlar.SoundEffectManager.SoundEffectType;
import org.simlar.Volumes.MicrophoneStatus;

import android.app.Activity;
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
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public final class SimlarService extends Service implements LinphoneThreadListener
{
	private static final String LOGTAG = SimlarService.class.getSimpleName();
	private static final int NOTIFICATION_ID = 1;
	private static final long TERMINATE_CHECKER_INTERVAL = 20 * 1000; // milliseconds
	public static final String INTENT_EXTRA_SIMLAR_ID = "SimlarServiceSimlarId";
	public static final String INTENT_EXTRA_GCM = "SimlarServiceGCM";

	private LinphoneThread mLinphoneThread = null;
	private final Handler mHandler = new Handler();
	private final IBinder mBinder = new SimlarServiceBinder();
	private SimlarStatus mSimlarStatus = SimlarStatus.OFFLINE;
	private final SimlarCallState mSimlarCallState = new SimlarCallState();
	private CallConnectionDetails mCallConnectionDetails = new CallConnectionDetails();
	private WakeLock mWakeLock = null;
	private WakeLock mDisplayWakeLock = null;
	private WifiLock mWifiLock = null;
	private boolean mGoingDown = false;
	private boolean mTerminatePrivateAlreadyCalled = false;
	private static volatile Class<? extends Activity> mNotificationActivity = null;
	private VibratorManager mVibratorManager = null;
	private SoundEffectManager mSoundEffectManager = null;
	private boolean mHasAudioFocus = false;
	private final NetworkChangeReceiver mNetworkChangeReceiver = new NetworkChangeReceiver();
	private String mSimlarIdToCall = null;
	private static volatile boolean mRunning = false;
	private final TelephonyCallStateListener mTelephonyCallStateListener = new TelephonyCallStateListener();
	private int mCurrentRingerMode = -1;

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

	private final class TelephonyCallStateListener extends PhoneStateListener
	{
		private boolean mInCall;

		public TelephonyCallStateListener()
		{
		}

		public boolean isInCall()
		{
			return mInCall;
		}

		@Override
		public void onCallStateChanged(final int state, final String incomingNumber)
		{
			switch (state) {
			case TelephonyManager.CALL_STATE_IDLE:
				Lg.i(LOGTAG, "onTelephonyCallStateChanged: state=IDLE");
				mInCall = false;
				SimlarService.this.onTelephonyCallStateIdle();
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				Lg.i(LOGTAG, "onTelephonyCallStateChanged: [", new Lg.Anonymizer(incomingNumber), "] state=OFFHOOK");
				mInCall = true;
				SimlarService.this.onTelephonyCallStateOffHook();
				break;
			case TelephonyManager.CALL_STATE_RINGING:
				Lg.i(LOGTAG, "onTelephonyCallStateChanged: [", new Lg.Anonymizer(incomingNumber), "] state=RINGING");
				mInCall = false; /// TODO Think about
				SimlarService.this.onTelephonyCallStateRinging();
				break;
			default:
				Lg.i(LOGTAG, "onTelephonyCallStateChanged: [", new Lg.Anonymizer(incomingNumber), "] state=", state);
				break;
			}
		}
	}

	@Override
	public IBinder onBind(final Intent intent)
	{
		Lg.i(LOGTAG, "onBind");
		return mBinder;
	}

	void onTelephonyCallStateOffHook()
	{
		restoreRingerModeIfNeeded();
		if (mSimlarStatus != SimlarStatus.ONGOING_CALL) {
			return;
		}

		if (mLinphoneThread == null) {
			return;
		}

		mSoundEffectManager.stop(SoundEffectType.CALL_INTERRUPTION);

		mLinphoneThread.pauseAllCalls();
	}

	void onTelephonyCallStateIdle()
	{
		restoreRingerModeIfNeeded();
		if (mSimlarStatus != SimlarStatus.ONGOING_CALL) {
			return;
		}

		if (mLinphoneThread == null) {
			return;
		}

		mSoundEffectManager.stop(SoundEffectType.CALL_INTERRUPTION);

		mLinphoneThread.resumeCall();
	}

	void onTelephonyCallStateRinging()
	{
		if (mSimlarStatus != SimlarStatus.ONGOING_CALL) {
			return;
		}

		silenceAndStoreRingerMode();

		mSoundEffectManager.start(SoundEffectType.CALL_INTERRUPTION);
	}

	private void silenceAndStoreRingerMode()
	{
		// steam roller tactics to silence incoming call
		final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		final int ringerMode = audioManager.getRingerMode();
		if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
			return;
		}

		mCurrentRingerMode = ringerMode;
		Lg.i(LOGTAG, "saving RingerMode: ", mCurrentRingerMode, " and switch to ringer mode silent");
		audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
	}

	private void restoreRingerModeIfNeeded()
	{
		if (mCurrentRingerMode == -1 || mCurrentRingerMode == AudioManager.RINGER_MODE_SILENT) {
			return;
		}

		final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		/// NOTE: On lollipop getRingerMode sometimes does not report silent mode correctly, so checking it here may be dangerous.
		Lg.i(LOGTAG, "restoring RingerMode: ", audioManager.getRingerMode(), " -> ", mCurrentRingerMode);
		audioManager.setRingerMode(mCurrentRingerMode);
		mCurrentRingerMode = -1;
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		Lg.i(LOGTAG, "onStartCommand intent=", intent, " startId=", startId);

		Lg.i(LOGTAG, "acquiring simlar wake lock");
		acquireWakeLock();
		acquireWifiLock();

		// releasing wakelock of gcm's WakefulBroadcastReceiver if needed
		if (intent != null) {
			WakefulBroadcastReceiver.completeWakefulIntent(intent);

			if (!Util.isNullOrEmpty(intent.getStringExtra(INTENT_EXTRA_GCM))) {
				intent.removeExtra(INTENT_EXTRA_GCM);
			}

			// make sure we have a contact name for the CallActivity
			mSimlarIdToCall = intent.getStringExtra(INTENT_EXTRA_SIMLAR_ID);
			intent.removeExtra(INTENT_EXTRA_SIMLAR_ID);
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
			Lg.w(LOGTAG, "onStartCommand: with no intent");
			mSimlarIdToCall = null;
		}
		Lg.i(LOGTAG, "onStartCommand simlarIdToCall=", new Lg.Anonymizer(mSimlarIdToCall));

		handlePendingCall();

		if (mGoingDown) {
			Lg.i(LOGTAG, "onStartCommand called while service is going down => recovering");
			mGoingDown = false;
			if (mLinphoneThread == null) {
				startLinphone();
			}
		}

		// We want this service to continue running until it is explicitly stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onCreate()
	{
		Lg.i(LOGTAG, "started with simlar version=", Version.getVersionName(this),
				" on device: ", Build.MANUFACTURER, " ", Build.MODEL, " (", Build.DEVICE, ") with android version=", Build.VERSION.RELEASE);

		mRunning = true;

		FileHelper.init(this);
		mVibratorManager = new VibratorManager(this.getApplicationContext());
		mSoundEffectManager = new SoundEffectManager(this.getApplicationContext());

		mWakeLock = ((PowerManager) this.getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimlarWakeLock");
		mDisplayWakeLock = createDisplayWakeLock();
		mWifiLock = createWifiWakeLock();

		startForeground(NOTIFICATION_ID, createNotification());

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mNetworkChangeReceiver, intentFilter);

		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mTelephonyCallStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		PreferencesHelper.readPreferencesFromFile(this);

		ContactsProvider.preLoadContacts(this);

		startLinphone();
	}

	private void startLinphone()
	{
		Lg.i(LOGTAG, "startLinphone");
		mLinphoneThread = new LinphoneThread(this, this);
		mTerminatePrivateAlreadyCalled = false;
		terminateChecker();
	}

	@SuppressWarnings("deprecation")
	private WakeLock createDisplayWakeLock()
	{
		return ((PowerManager) getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "SimlarDisplayWakeLock");
	}

	static private int createWifiWakeLockType()
	{
		if  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			return WifiManager.WIFI_MODE_FULL_HIGH_PERF;
		}

		return WifiManager.WIFI_MODE_FULL;
	}

	private WifiLock createWifiWakeLock()
	{
		return ((WifiManager) this.getSystemService(Context.WIFI_SERVICE)).createWifiLock(createWifiWakeLockType(), "SimlarWifiLock");
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

	boolean terminateCheck()
	{
		if (mGoingDown) {
			return true;
		}

		if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
			return false;
		}

		Lg.i(LOGTAG, "terminateChecker triggered on status=", mSimlarStatus);

		if (!mSimlarStatus.isConnectedToSipServer()) {
			mSimlarCallState.connectingToSimlarServerTimedOut();
			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		}
		handleTerminate();

		return true;
	}

	public void registerActivityToNotification(final Class<? extends Activity> activity)
	{
		if (activity == null) {
			Lg.e(LOGTAG, "registerActivityToNotification with empty activity");
			return;
		}

		Lg.i(LOGTAG, "registerActivityToNotification: ", activity.getSimpleName());
		mNotificationActivity = activity;

		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification());
	}

	private static void createMissedCallNotification(final Context context, final String simlarId)
	{
		if (Util.isNullOrEmpty(simlarId)) {
			Lg.w(LOGTAG, "no simlarId for missed call");
			return;
		}

		Lg.i(LOGTAG, "missed call: ", new Lg.Anonymizer(simlarId));
		ContactsProvider.getNameAndPhotoId(simlarId, context, new ContactListener()
		{
			@Override
			public void onGetNameAndPhotoId(final String name, final String photoId)
			{
				createMissedCallNotification(context, name, photoId);
			}
		});
	}

	private static void createMissedCallNotification(final Context context, final String name, final String photoId)
	{
		final PendingIntent activity = PendingIntent.getActivity(context, 0,
				new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), 0);

		final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
		notificationBuilder.setSmallIcon(R.drawable.ic_notification_missed_calls);
		notificationBuilder.setLargeIcon(ContactsProvider.getContactPhotoBitmap(context, R.drawable.ic_launcher, photoId));
		notificationBuilder.setContentTitle(context.getString(R.string.missed_call_notification));
		notificationBuilder.setContentText(name);
		notificationBuilder.setContentIntent(activity);
		notificationBuilder.setAutoCancel(true);
		notificationBuilder.setWhen(System.currentTimeMillis());

		((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(
				PreferencesHelper.getNextMissedCallNotificationId(context), notificationBuilder.build());
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
			Lg.i(LOGTAG, "no activity registered based on mSimlarStatus=", mSimlarStatus, " we now take: ", mNotificationActivity.getSimpleName());
		}

		/// Note: we do not want the TaskStackBuilder here
		final PendingIntent activity = PendingIntent.getActivity(this, 0,
				new Intent(this, mNotificationActivity).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), 0);

		final String text = mSimlarCallState.createNotificationText(this, mGoingDown);
		final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(R.drawable.ic_notification_ongoing_call);
		notificationBuilder.setLargeIcon(mSimlarCallState.getContactPhotoBitmap(this, R.drawable.ic_launcher));
		notificationBuilder.setContentTitle(getString(R.string.app_name));
		notificationBuilder.setContentText(text);
		notificationBuilder.setOngoing(true);
		notificationBuilder.setContentIntent(activity);
		notificationBuilder.setWhen(System.currentTimeMillis());
		return notificationBuilder.build();
	}

	void connect()
	{
		if (mLinphoneThread == null) {
			return;
		}

		notifySimlarStatusChanged(SimlarStatus.CONNECTING);

		try {
			mLinphoneThread.register(PreferencesHelper.getMySimlarId(), PreferencesHelper.getPassword());
		} catch (final NotInitedException e) {
			Lg.ex(LOGTAG, e, "PreferencesHelper.NotInitedException");
		}
	}

	@Override
	public synchronized void onDestroy()
	{
		Lg.i(LOGTAG, "onDestroy");

		((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);

		mVibratorManager.stop();
		mSoundEffectManager.stopAll();

		unregisterReceiver(mNetworkChangeReceiver);

		((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).listen(mTelephonyCallStateListener, PhoneStateListener.LISTEN_NONE);

		// just in case
		releaseWakeLock();
		releaseDisplayWakeLock();
		releaseWifiLock();

		mRunning = false;

		Lg.i(LOGTAG, "onDestroy ended");
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
		if (mLinphoneThread == null) {
			Lg.w(LOGTAG, "checkNetworkConnectivityAndRefreshRegisters triggered but no linphone thread");
			return;
		}

		final NetworkInfo ni = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
		if (ni == null) {
			Lg.e(LOGTAG, "no NetworkInfo found");
			return;
		}

		Lg.i(LOGTAG, "NetworkInfo ", ni.getTypeName(), " ", ni.getState());
		if (ni.isConnected()) {
			mLinphoneThread.refreshRegisters();
		}
	}

	@Override
	public void onInitialized()
	{
		notifySimlarStatusChanged(SimlarStatus.OFFLINE);

		if (!PreferencesHelper.readPreferencesFromFile(this)) {
			Lg.e(LOGTAG, "failed to initialize credentials");
			return;
		}

		connect();
	}

	@Override
	public void onRegistrationStateChanged(final RegistrationState state)
	{
		Lg.i(LOGTAG, "onRegistrationStateChanged: ", state);

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
		Lg.i(LOGTAG, "notifySimlarStatusChanged: ", status);

		mSimlarStatus = status;

		SimlarServiceBroadcast.sendSimlarStatusChanged(this);

		handlePendingCall();

		if (mSimlarStatus == SimlarStatus.CONNECTING && mSimlarCallState.updateConnectingToServer()) {
			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		}
	}

	private void handlePendingCall()
	{
		if (getSimlarStatus() != SimlarStatus.ONLINE || Util.isNullOrEmpty(mSimlarIdToCall) || mGoingDown) {
			return;
		}

		final String simlarId = mSimlarIdToCall;
		mSimlarIdToCall = null;
		mHandler.post(new Runnable()
		{
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
			Lg.e(LOGTAG, "SimlarCallState is empty: ", mSimlarCallState);
			return;
		}

		if (!simlarCallStateChanged) {
			Lg.v(LOGTAG, "SimlarCallState staying the same: ", mSimlarCallState);
		} else {
			Lg.i(LOGTAG, "SimlarCallState updated: ", mSimlarCallState);
			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		}

		if (!mCallConnectionDetails.updateCallStats(quality, codec, iceState, upload, download, jitter, packetLoss, latePackets, roundTripDelay)) {
			Lg.v(LOGTAG, "CallConnectionDetails staying the same: ", mCallConnectionDetails);
			return;
		}

		Lg.d(LOGTAG, "CallConnectionDetails updated: ", mCallConnectionDetails);
		SimlarServiceBroadcast.sendCallConnectionDetailsChanged(this);
	}

	@Override
	public void onCallStateChanged(final String number, final State callState, final String message)
	{
		final boolean oldCallStateRinging = mSimlarCallState.isRinging();
		if (!mSimlarCallState.updateCallStateChanged(number, LinphoneCallState.fromLinphoneCallState(callState), CallEndReason.fromMessage(message))) {
			Lg.v(LOGTAG, "SimlarCallState staying the same: ", mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Lg.e(LOGTAG, "SimlarCallState is empty: ", mSimlarCallState);
			return;
		}

		Lg.i(LOGTAG, "SimlarCallState updated: ", mSimlarCallState);

		if (mSimlarCallState.isRinging() && !mGoingDown) {
			if (mTelephonyCallStateListener.isInCall()) {
				Lg.i(LOGTAG, "incoming call while gsm call is active");
				mSoundEffectManager.start(SoundEffectType.CALL_INTERRUPTION);
			} else {
				mSoundEffectManager.start(SoundEffectType.RINGTONE);
				mVibratorManager.start();
			}
		} else {
			mVibratorManager.stop();
			mSoundEffectManager.stop(SoundEffectType.RINGTONE);
			mSoundEffectManager.stop(SoundEffectType.CALL_INTERRUPTION);
		}

		if (mSimlarCallState.isWaitingForContact() && !mGoingDown) {
			mSoundEffectManager.start(SoundEffectType.WAITING_FOR_CONTACT);
		} else {
			mSoundEffectManager.stop(SoundEffectType.WAITING_FOR_CONTACT);
		}

		if (mSimlarCallState.isBeforeEncryption()) {
			mLinphoneThread.setMicrophoneStatus(MicrophoneStatus.DISABLED);
			mSoundEffectManager.setInCallMode(true);
			mSoundEffectManager.startPrepared(SoundEffectType.ENCRYPTION_HANDSHAKE);
		}

		if (mSimlarCallState.isNewCall() && !mGoingDown) {
			mSoundEffectManager.prepare(SoundEffectType.ENCRYPTION_HANDSHAKE);
			notifySimlarStatusChanged(SimlarStatus.ONGOING_CALL);

			mCallConnectionDetails = new CallConnectionDetails();

			if (!mHasAudioFocus) {
				// We acquire AUDIOFOCUS_GAIN_TRANSIENT instead of AUDIOFOCUS_GAIN because we want the music to resume after ringing or call
				final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					mHasAudioFocus = true;
					Lg.i(LOGTAG, "audio focus granted");
				} else {
					Lg.e(LOGTAG, "audio focus not granted");
				}
			}

			if (mSimlarCallState.isRinging()) {
				Lg.i(LOGTAG, "starting RingingActivity");
				mNotificationActivity = RingingActivity.class;
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
					Lg.i(LOGTAG, "audio focus released");
				} else {
					Lg.e(LOGTAG, "releasing audio focus not granted");
				}
				mHasAudioFocus = false;
			}

			restoreRingerModeIfNeeded();

			if (mCallConnectionDetails.updateEndedCall()) {
				SimlarServiceBroadcast.sendCallConnectionDetailsChanged(this);
			}

			acquireDisplayWakeLock();
			if (oldCallStateRinging) {
				createMissedCallNotification(this, number);
			}
			terminate();
		}

		ContactsProvider.getNameAndPhotoId(number, this, new ContactListener()
		{
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
			Lg.v(LOGTAG, "callEncryptionChanged but no difference in SimlarCallState: ", mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Lg.e(LOGTAG, "callEncryptionChanged but SimlarCallState isEmpty");
			return;
		}

		Lg.i(LOGTAG, "SimlarCallState updated encryption: ", mSimlarCallState);

		mLinphoneThread.setMicrophoneStatus(MicrophoneStatus.ON);
		mSoundEffectManager.stop(SoundEffectType.ENCRYPTION_HANDSHAKE);

		if (encrypted) {
			// just to be sure
			mVibratorManager.stop();
			mSoundEffectManager.stop(SoundEffectType.UNENCRYPTED_CALL_ALARM);
		} else {
			Lg.w(LOGTAG, "unencrypted call");
			mVibratorManager.start();
			mSoundEffectManager.start(SoundEffectType.UNENCRYPTED_CALL_ALARM);
		}

		SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
	}

	public void acceptUnencryptedCall()
	{
		Lg.w(LOGTAG, "user accepts unencrypted call");
		mVibratorManager.stop();
		mSoundEffectManager.stop(SoundEffectType.UNENCRYPTED_CALL_ALARM);
	}

	void call(final String simlarId)
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

		mNotificationActivity = MainActivity.class;
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification());

		if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
			mLinphoneThread.terminateAllCalls();
		} else {
			terminate();
		}
	}

	public void terminate()
	{
		Lg.i(LOGTAG, "terminate");
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
		Lg.i(LOGTAG, "handleTerminate");
		if (mGoingDown) {
			Lg.w(LOGTAG, "handleTerminate: already going down");
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
			Lg.i(LOGTAG, "terminatePrivate already called");
			return;
		}
		mTerminatePrivateAlreadyCalled = true;

		Lg.i(LOGTAG, "terminatePrivate");
		if (mLinphoneThread != null) {
			mLinphoneThread.finish();
		} else {
			onJoin();
		}
	}

	@Override
	public void onJoin()
	{
		if (mLinphoneThread != null) {
			try {
				mLinphoneThread.join(2000);
			} catch (final InterruptedException e) {
				Lg.ex(LOGTAG, e, "join interrupted");
			}
			mLinphoneThread = null;
		}

		SimlarServiceBroadcast.sendServiceFinishes(this);

		// make sure we remove the terminateChecker by removing all events
		mHandler.removeCallbacksAndMessages(null);

		if (mGoingDown) {
			Lg.i(LOGTAG, "onJoin: canceling notification");
			((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
			Lg.i(LOGTAG, "onJoin: calling stopSelf");
			stopSelf();
			Lg.i(LOGTAG, "onJoin: stopSelf called");
			// calling stopForeground before stopSelf could trigger a restart
			stopForeground(true);
			Lg.i(LOGTAG, "onJoin: stopForeground called");
		} else {
			Lg.i(LOGTAG, "onJoin: recovering calling startLinphone");
			startLinphone();
		}
	}

	public void verifyAuthenticationTokenOfCurrentCall(final boolean verified)
	{
		if (mLinphoneThread == null) {
			Lg.e(LOGTAG, "ERROR: verifyAuthenticationToken called but no linphone thread");
			return;
		}

		if (Util.isNullOrEmpty(mSimlarCallState.getAuthenticationToken())) {
			Lg.e(LOGTAG, "ERROR: verifyAuthenticationToken called but no token available");
			return;
		}

		mLinphoneThread.verifyAuthenticationToken(mSimlarCallState.getAuthenticationToken(), verified);
	}

	public SimlarStatus getSimlarStatus()
	{
		return mSimlarStatus;
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

	public static boolean isRunning()
	{
		return mRunning;
	}

	public static Class<? extends Activity> getActivity()
	{
		return mNotificationActivity;
	}

	public static void startService(final Context context, final Intent intent)
	{
		context.startService(intent);
		mRunning = true;
	}
}

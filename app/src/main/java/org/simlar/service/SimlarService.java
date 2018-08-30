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

package org.simlar.service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.SurfaceView;
import android.widget.Toast;

import org.linphone.core.Call.State;
import org.linphone.core.RegistrationState;
import org.simlar.R;
import org.simlar.contactsprovider.ContactsProvider;
import org.simlar.helper.CallConnectionDetails;
import org.simlar.helper.CallEndReason;
import org.simlar.helper.FlavourHelper;
import org.simlar.helper.NetworkQuality;
import org.simlar.helper.PermissionsHelper;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.PreferencesHelper.NotInitedException;
import org.simlar.helper.VideoState;
import org.simlar.helper.Volumes;
import org.simlar.helper.Volumes.MicrophoneStatus;
import org.simlar.logging.Lg;
import org.simlar.service.SoundEffectManager.SoundEffectType;
import org.simlar.service.liblinphone.LinphoneCallState;
import org.simlar.service.liblinphone.LinphoneThread;
import org.simlar.service.liblinphone.LinphoneThreadListener;
import org.simlar.utils.Util;

public final class SimlarService extends Service implements LinphoneThreadListener
{
	private static final int NOTIFICATION_ID = 1;
	private static final long TERMINATE_CHECKER_INTERVAL = 20 * 1000; // milliseconds
	private static ServiceActivities ACTIVITIES = null;
	public static final String INTENT_EXTRA_SIMLAR_ID = "SimlarServiceSimlarId";
	@SuppressWarnings("WeakerAccess") // is only used in flavour push
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
	private AudioFocus mAudioFocus = null;
	private final NetworkChangeReceiver mNetworkChangeReceiver = new NetworkChangeReceiver();
	private String mSimlarIdToCall = null;
	private static volatile boolean mRunning = false;
	private final TelephonyCallStateListener mTelephonyCallStateListener = new TelephonyCallStateListener();
	private int mCurrentRingerMode = -1;
	private PendingIntent mKeepAwakePendingIntent = null;
	private final KeepAwakeReceiver mKeepAwakeReceiver = FlavourHelper.isGcmEnabled() ? null : new KeepAwakeReceiver();
	private VideoState mVideoState = VideoState.OFF;

	public final class SimlarServiceBinder extends Binder
	{
		SimlarService getService()
		{
			return SimlarService.this;
		}
	}

	private final class NetworkChangeReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			checkNetworkConnectivityAndRefreshRegisters();
		}
	}

	private final class KeepAwakeReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			keepAwake();
		}
	}


	private final class TelephonyCallStateListener extends PhoneStateListener
	{
		private boolean mInCall = false;

		boolean isInCall()
		{
			return mInCall;
		}

		@Override
		public void onCallStateChanged(final int state, final String incomingNumber)
		{
			switch (state) {
			case TelephonyManager.CALL_STATE_IDLE:
				Lg.i("onTelephonyCallStateChanged: state=IDLE");
				mInCall = false;
				onTelephonyCallStateIdle();
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				Lg.i("onTelephonyCallStateChanged: [", new Lg.Anonymizer(incomingNumber), "] state=OFFHOOK");
				mInCall = true;
				onTelephonyCallStateOffHook();
				break;
			case TelephonyManager.CALL_STATE_RINGING:
				Lg.i("onTelephonyCallStateChanged: [", new Lg.Anonymizer(incomingNumber), "] state=RINGING");
				mInCall = false; /// TODO Think about
				onTelephonyCallStateRinging();
				break;
			default:
				Lg.i("onTelephonyCallStateChanged: [", new Lg.Anonymizer(incomingNumber), "] state=", state);
				break;
			}
		}
	}

	@Override
	public IBinder onBind(final Intent intent)
	{
		Lg.i("onBind");
		return mBinder;
	}

	private void onTelephonyCallStateOffHook()
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

	private void onTelephonyCallStateIdle()
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

	private void onTelephonyCallStateRinging()
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
		final AudioManager audioManager = Util.getSystemService(this, Context.AUDIO_SERVICE);
		final int ringerMode = audioManager.getRingerMode();
		if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
			return;
		}

		if (!PermissionsHelper.isNotificationPolicyAccessGranted(this)) {
			Lg.i("permission not granted to Do Not Disturb state");
			return;
		}

		mCurrentRingerMode = ringerMode;
		Lg.i("saving RingerMode: ", mCurrentRingerMode, " and switch to ringer mode silent");
		audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
	}

	private void restoreRingerModeIfNeeded()
	{
		if (mCurrentRingerMode == -1 || mCurrentRingerMode == AudioManager.RINGER_MODE_SILENT) {
			return;
		}

		final AudioManager audioManager = Util.getSystemService(this, Context.AUDIO_SERVICE);
		/// NOTE: On lollipop getRingerMode sometimes does not report silent mode correctly, so checking it here may be dangerous.
		Lg.i("restoring RingerMode: ", audioManager.getRingerMode(), " -> ", mCurrentRingerMode);
		audioManager.setRingerMode(mCurrentRingerMode);
		mCurrentRingerMode = -1;
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		Lg.i("onStartCommand intent=", intent, " startId=", startId);

		if (FlavourHelper.isGcmEnabled()) {
			Lg.i("acquiring simlar wake lock");
			acquireWakeLock();
			acquireWifiLock();
		}

		if (intent != null) {
			if (!Util.isNullOrEmpty(intent.getStringExtra(INTENT_EXTRA_GCM))) {
				intent.removeExtra(INTENT_EXTRA_GCM);
			}

			mSimlarIdToCall = intent.getStringExtra(INTENT_EXTRA_SIMLAR_ID);
			intent.removeExtra(INTENT_EXTRA_SIMLAR_ID);
			if (!Util.isNullOrEmpty(mSimlarIdToCall)) {
				if (!FlavourHelper.isGcmEnabled()) {
					if (mSimlarStatus.isOffline()) {
						mSimlarCallState.updateCallStateChanged(mSimlarIdToCall, LinphoneCallState.CALL_END, CallEndReason.SERVER_CONNECTION_TIMEOUT);
					} else {
						mSimlarCallState.updateCallStateChanged(mSimlarIdToCall, LinphoneCallState.OUTGOING_INIT, CallEndReason.NONE);
					}
				}

				// make sure we have a contact name for the CallActivity
				ContactsProvider.getNameAndPhotoId(mSimlarIdToCall, this, mSimlarCallState::updateContactNameAndImage);
			}
		} else {
			Lg.w("onStartCommand: with no intent");
			mSimlarIdToCall = null;
		}
		Lg.i("onStartCommand simlarIdToCall=", new Lg.Anonymizer(mSimlarIdToCall));

		handlePendingCall();

		if (mGoingDown) {
			Lg.i("onStartCommand called while service is going down => recovering");
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
		Lg.i("onCreate");

		mRunning = true;

		mVibratorManager = new VibratorManager(getApplicationContext());
		mSoundEffectManager = new SoundEffectManager(getApplicationContext());
		mAudioFocus = new AudioFocus(this);

		mWakeLock = ((PowerManager) Util.getSystemService(this, Context.POWER_SERVICE))
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimlarWakeLock");
		mDisplayWakeLock = createDisplayWakeLock();
		mWifiLock = createWifiWakeLock();

		startForeground(NOTIFICATION_ID, createNotification());

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mNetworkChangeReceiver, intentFilter);

		((TelephonyManager) Util.getSystemService(this, Context.TELEPHONY_SERVICE))
				.listen(mTelephonyCallStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		ContactsProvider.preLoadContacts(this);

		startLinphone();
	}

	private void startLinphone()
	{
		Lg.i("startLinphone");
		mLinphoneThread = new LinphoneThread(this, this);
		mTerminatePrivateAlreadyCalled = false;

		if (FlavourHelper.isGcmEnabled()) {
			terminateChecker();
		} else {
			startKeepAwake();
		}
	}

	@SuppressWarnings("deprecation")
	private WakeLock createDisplayWakeLock()
	{
		return ((PowerManager) Util.getSystemService(this, Context.POWER_SERVICE))
				.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "SimlarDisplayWakeLock");
	}

	private WifiLock createWifiWakeLock()
	{
		return ((WifiManager) Util.getSystemService(this, Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SimlarWifiLock");
	}

	private void terminateChecker()
	{
		mHandler.postDelayed(() -> {
			if (terminateCheck()) {
				return;
			}

			terminateChecker();
		}, TERMINATE_CHECKER_INTERVAL);
	}

	private boolean terminateCheck()
	{
		if (mGoingDown) {
			return true;
		}

		if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
			return false;
		}

		Lg.i("terminateChecker triggered on status=", mSimlarStatus);

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
			Lg.e("registerActivityToNotification with empty activity");
			return;
		}

		Lg.i("registerActivityToNotification: ", activity.getSimpleName());
		mNotificationActivity = activity;

		final NotificationManager nm = Util.getSystemService(this, Context.NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification());
	}

	private static void createMissedCallNotification(final Context context, final String simlarId)
	{
		if (Util.isNullOrEmpty(simlarId)) {
			Lg.w("no simlarId for missed call");
			return;
		}

		Lg.i("missed call: ", new Lg.Anonymizer(simlarId));
		ContactsProvider.getNameAndPhotoId(simlarId, context, (name, photoId) -> createMissedCallNotification(context, name, photoId));
	}

	private static void createMissedCallNotification(final Context context, final String name, final String photoId)
	{
		final PendingIntent activity = PendingIntent.getActivity(context, 0,
				new Intent(context, ACTIVITIES.getMainActivity()).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), 0);

		final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, SimlarNotificationChannel.MISSED_CALL.name());
		notificationBuilder.setSmallIcon(R.drawable.ic_notification_missed_calls);
		notificationBuilder.setLargeIcon(ContactsProvider.getContactPhotoBitmap(context, R.drawable.ic_launcher, photoId));
		notificationBuilder.setContentTitle(context.getString(R.string.missed_call_notification));
		notificationBuilder.setContentText(name);
		notificationBuilder.setContentIntent(activity);
		notificationBuilder.setAutoCancel(true);
		notificationBuilder.setWhen(System.currentTimeMillis());

		((NotificationManager) Util.getSystemService(context, Context.NOTIFICATION_SERVICE))
				.notify(PreferencesHelper.getNextMissedCallNotificationId(context), notificationBuilder.build());
	}

	private Notification createNotification()
	{
		if (mNotificationActivity == null) {
			if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
				mNotificationActivity = mSimlarCallState.isRinging() ? ACTIVITIES.getRingingActivity() : ACTIVITIES.getCallActivity();
			} else {
				mNotificationActivity = ACTIVITIES.getMainActivity();
			}
			Lg.i("no activity registered based on mSimlarStatus=", mSimlarStatus, " we now take: ", mNotificationActivity.getSimpleName());
		}

		/// Note: we do not want the TaskStackBuilder here
		final PendingIntent activity = PendingIntent.getActivity(this, 0,
				new Intent(this, mNotificationActivity).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), 0);

		final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, SimlarNotificationChannel.CALL.name());
		notificationBuilder.setSmallIcon(FlavourHelper.isGcmEnabled() ? R.drawable.ic_notification_ongoing_call : mSimlarStatus.getNotificationIcon());
		notificationBuilder.setLargeIcon(mSimlarCallState.getContactPhotoBitmap(this, R.drawable.ic_launcher));
		notificationBuilder.setContentTitle(getString(R.string.app_name));
		notificationBuilder.setContentText(createNotificationText());
		notificationBuilder.setOngoing(true);
		notificationBuilder.setContentIntent(activity);
		notificationBuilder.setWhen(System.currentTimeMillis());
		return notificationBuilder.build();
	}

	private String createNotificationText()
	{
		return FlavourHelper.isGcmEnabled() || mSimlarStatus == SimlarStatus.ONGOING_CALL
				? mSimlarCallState.createNotificationText(this, mGoingDown)
				: getString(mSimlarStatus.getNotificationTextId());
	}

	private void connect()
	{
		if (mLinphoneThread == null) {
			return;
		}

		notifySimlarStatusChanged(SimlarStatus.CONNECTING);

		try {
			mLinphoneThread.register(PreferencesHelper.getMySimlarId(), PreferencesHelper.getPassword());
			muteExternalSpeaker();
		} catch (final NotInitedException e) {
			Lg.ex(e, "PreferencesHelper.NotInitedException");
		}
	}

	@Override
	public synchronized void onDestroy()
	{
		Lg.i("onDestroy");

		((NotificationManager) Util.getSystemService(this, Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);

		mVibratorManager.stop();
		mSoundEffectManager.stopAll();

		unregisterReceiver(mNetworkChangeReceiver);

		stopKeepAwake();

		((TelephonyManager) Util.getSystemService(this, Context.TELEPHONY_SERVICE))
				.listen(mTelephonyCallStateListener, PhoneStateListener.LISTEN_NONE);

		// just in case
		releaseWakeLock();
		releaseDisplayWakeLock();
		releaseWifiLock();

		mRunning = false;

		if (!FlavourHelper.isGcmEnabled()) {
			Toast.makeText(this, R.string.simlar_service_on_destroy, Toast.LENGTH_SHORT).show();
		}

		Lg.i("onDestroy ended");
	}

	@SuppressLint("WakelockTimeout") // expected to be acquired for a complete call
	private void acquireWakeLock()
	{
		if (!mWakeLock.isHeld()) {
			mWakeLock.acquire();
		}
	}

	@SuppressLint("WakelockTimeout") // expected to be acquired for a complete call
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

	private void checkNetworkConnectivityAndRefreshRegisters()
	{
		if (mLinphoneThread == null) {
			Lg.w("checkNetworkConnectivityAndRefreshRegisters triggered but no linphone thread");
			return;
		}

		final NetworkInfo ni = ((ConnectivityManager) Util.getSystemService(this, Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
		if (ni == null) {
			Lg.e("no NetworkInfo found");
			return;
		}

		Lg.i("NetworkInfo ", ni.getTypeName(), " ", ni.getState());
		if (ni.isConnected()) {
			mLinphoneThread.refreshRegisters();
		}
	}

	private void startKeepAwake()
	{
		if (mKeepAwakeReceiver == null) {
			return;
		}

		final Intent startIntent = new Intent("org.simlar.keepAwake");
		mKeepAwakePendingIntent = PendingIntent.getBroadcast(this, 0, startIntent, 0);

		((AlarmManager) Util.getSystemService(this, Context.ALARM_SERVICE))
				.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 600000, 600000, mKeepAwakePendingIntent);

		final IntentFilter filter = new IntentFilter();
		filter.addAction("org.simlar.keepAwake");
		registerReceiver(mKeepAwakeReceiver, filter);
	}

	private void stopKeepAwake()
	{
		if (mKeepAwakeReceiver == null) {
			return;
		}

		unregisterReceiver(mKeepAwakeReceiver);
		((AlarmManager) Util.getSystemService(this, Context.ALARM_SERVICE)).cancel(mKeepAwakePendingIntent);
	}

	private void keepAwake()
	{
		// idea from linphone KeepAliveHandler
		checkNetworkConnectivityAndRefreshRegisters();

		// make sure iterate will have enough time before device eventually goes to sleep
		acquireWakeLock();
		mHandler.postDelayed(() -> {
			if (mSimlarStatus != SimlarStatus.ONGOING_CALL) {
				releaseWakeLock();
			}
		}, 4000);
	}

	@Override
	public void onInitialized()
	{
		notifySimlarStatusChanged(SimlarStatus.OFFLINE);

		if (!PreferencesHelper.readPreferencesFromFile(this)) {
			Lg.e("failed to initialize credentials");
			return;
		}

		connect();
	}

	@Override
	public void onRegistrationStateChanged(final RegistrationState state)
	{
		Lg.i("onRegistrationStateChanged: ", state);

		final SimlarStatus status = SimlarStatus.fromRegistrationState(state);

		if (mGoingDown && !status.isConnectedToSipServer()) {
			mHandler.post(this::terminatePrivate);
		}

		notifySimlarStatusChanged(status);

		if (!FlavourHelper.isGcmEnabled()) {
			Lg.i("updating notification based on registration state");
			final NotificationManager nm = Util.getSystemService(this, Context.NOTIFICATION_SERVICE);
			nm.notify(NOTIFICATION_ID, createNotification());
		}
	}

	private void notifySimlarStatusChanged(final SimlarStatus status)
	{
		Lg.i("notifySimlarStatusChanged: ", status);

		mSimlarStatus = status;

		SimlarServiceBroadcast.sendSimlarStatusChanged(this);

		handlePendingCall();

		if (mSimlarStatus == SimlarStatus.CONNECTING && mSimlarCallState.updateConnectingToServer()) {
			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		}
	}

	private void handlePendingCall()
	{
		if (mSimlarStatus != SimlarStatus.ONLINE || Util.isNullOrEmpty(mSimlarIdToCall) || mGoingDown) {
			return;
		}

		final String simlarId = mSimlarIdToCall;
		mSimlarIdToCall = null;
		mHandler.post(() -> call(simlarId));
	}

	@Override
	public void onCallStatsChanged(final NetworkQuality quality, final int callDuration, final String codec, final String iceState,
	                               final int upload, final int download, final int jitter, final int packetLoss, final long latePackets, final int roundTripDelay)
	{
		final boolean simlarCallStateChanged = mSimlarCallState.updateCallStats(quality, callDuration);

		if (mSimlarCallState.isEmpty()) {
			Lg.e("SimlarCallState is empty: ", mSimlarCallState);
			return;
		}

		if (simlarCallStateChanged) {
			Lg.i("updated ", mSimlarCallState);
			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		} else {
			Lg.v("SimlarCallState staying the same: ", mSimlarCallState);
		}

		if (!mCallConnectionDetails.updateCallStats(quality, codec, iceState, upload, download, jitter, packetLoss, latePackets, roundTripDelay)) {
			Lg.v("CallConnectionDetails staying the same: ", mCallConnectionDetails);
			return;
		}

		Lg.d("CallConnectionDetails updated: ", mCallConnectionDetails);
		SimlarServiceBroadcast.sendCallConnectionDetailsChanged(this);
	}

	@Override
	public void onCallStateChanged(final String number, final State callState, final String message)
	{
		final boolean oldCallStateRinging = mSimlarCallState.isRinging();
		if (!mSimlarCallState.updateCallStateChanged(number, LinphoneCallState.fromLinphoneCallState(callState), CallEndReason.fromMessage(message))) {
			Lg.v("SimlarCallState staying the same: ", mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Lg.e("SimlarCallState is empty: ", mSimlarCallState);
			return;
		}

		Lg.i("updated ", mSimlarCallState);

		if (mSimlarCallState.isRinging() && !mGoingDown) {
			if (mTelephonyCallStateListener.isInCall()) {
				Lg.i("incoming call while gsm call is active");
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

			if (!FlavourHelper.isGcmEnabled()) {
				Lg.i("acquiring simlar wake lock because of new call");
				acquireWakeLock();
				acquireWifiLock();
			}

			mAudioFocus.request();

			if (mSimlarCallState.isRinging()) {
				Lg.i("starting RingingActivity");
				mNotificationActivity = ACTIVITIES.getRingingActivity();
				startActivity(new Intent(this, ACTIVITIES.getRingingActivity()).addFlags(
						Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
			}
		}

		if (mSimlarCallState.isEndedCall()) {
			notifySimlarStatusChanged(SimlarStatus.ONLINE);
			mSoundEffectManager.stopAll();
			mSoundEffectManager.setInCallMode(false);
			mAudioFocus.release();

			restoreRingerModeIfNeeded();

			if (mCallConnectionDetails.updateEndedCall()) {
				SimlarServiceBroadcast.sendCallConnectionDetailsChanged(this);
			}

			if (oldCallStateRinging) {
				createMissedCallNotification(this, number);
			}

			if (FlavourHelper.isGcmEnabled()) {
				acquireDisplayWakeLock();
				terminate();
			} else {
				releaseWakeLock();
				releaseWifiLock();
			}
		}

		ContactsProvider.getNameAndPhotoId(number, this, (name, photoId) -> {
			mSimlarCallState.updateContactNameAndImage(name, photoId);

			final NotificationManager nm = Util.getSystemService(this, Context.NOTIFICATION_SERVICE);
			nm.notify(NOTIFICATION_ID, createNotification());

			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		});
	}

	@Override
	public void onCallEncryptionChanged(final String authenticationToken, final boolean authenticationTokenVerified)
	{
		if (!mSimlarCallState.updateCallEncryption(authenticationToken, authenticationTokenVerified)) {
			Lg.v("callEncryptionChanged but no difference in SimlarCallState: ", mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Lg.e("callEncryptionChanged but SimlarCallState isEmpty");
			return;
		}

		Lg.i("SimlarCallState updated encryption: authenticationToken=", authenticationToken, " authenticationTokenVerified=", authenticationTokenVerified);

		mLinphoneThread.setMicrophoneStatus(MicrophoneStatus.ON);
		mSoundEffectManager.stop(SoundEffectType.ENCRYPTION_HANDSHAKE);

		SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
	}

	@Override
	public void onVideoStateChanged(final VideoState videoState)
	{
		if (mVideoState == videoState) {
			return;
		}

		mVideoState = videoState;
		Lg.i("updated video state: ", videoState);
		SimlarServiceBroadcast.sendVideoStateChanged(this, videoState);
	}

	private void call(final String simlarId)
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

		mNotificationActivity = ACTIVITIES.getMainActivity();
		final NotificationManager nm = Util.getSystemService(this, Context.NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification());

		if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
			mLinphoneThread.terminateAllCalls();
		} else if (FlavourHelper.isGcmEnabled()) {
			terminate();
		}
	}

	public void terminate()
	{
		Lg.i("terminate");
		mHandler.post(this::handleTerminate);
	}

	private void handleTerminate()
	{
		Lg.i("handleTerminate");
		if (mGoingDown) {
			Lg.w("handleTerminate: already going down");
			return;
		}
		mGoingDown = true;
		SimlarServiceBroadcast.sendSimlarStatusChanged(this);

		if (mLinphoneThread != null && mSimlarStatus.isConnectedToSipServer()) {
			mLinphoneThread.unregister();

			// make sure terminatePrivate is called after at least 5 seconds
			mHandler.postDelayed(this::terminatePrivate, 5000);
		} else {
			mHandler.post(this::terminatePrivate);
		}
	}

	private void terminatePrivate()
	{
		// make sure this function is only called once
		if (mTerminatePrivateAlreadyCalled) {
			Lg.i("terminatePrivate already called");
			return;
		}
		mTerminatePrivateAlreadyCalled = true;

		Lg.i("terminatePrivate");
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
				Lg.ex(e, "join interrupted");
			}
			mLinphoneThread = null;
		}

		SimlarServiceBroadcast.sendServiceFinishes(this);

		// make sure we remove the terminateChecker by removing all events
		mHandler.removeCallbacksAndMessages(null);

		if (mGoingDown) {
			Lg.i("onJoin: canceling notification");
			((NotificationManager) Util.getSystemService(this, Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
			Lg.i("onJoin: calling stopSelf");
			stopSelf();
			Lg.i("onJoin: stopSelf called");
			// calling stopForeground before stopSelf could trigger a restart
			stopForeground(true);
			Lg.i("onJoin: stopForeground called");
		} else {
			Lg.i("onJoin: recovering calling startLinphone");
			startLinphone();
		}
	}

	public void verifyAuthenticationTokenOfCurrentCall(final boolean verified)
	{
		if (mLinphoneThread == null) {
			Lg.e("ERROR: verifyAuthenticationToken called but no linphone thread");
			return;
		}

		if (Util.isNullOrEmpty(mSimlarCallState.getAuthenticationToken())) {
			Lg.e("ERROR: verifyAuthenticationToken called but no token available");
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

	private Volumes getVolumes()
	{
		if (mLinphoneThread == null) {
			return new Volumes();
		}

		return mLinphoneThread.getVolumes();
	}

	public int getMicrophoneVolume()
	{
		return getVolumes().getProgressMicrophone();
	}

	public int getSpeakerVolume()
	{
		return getVolumes().getProgressSpeaker();
	}

	public boolean getEchoLimiter()
	{
		return getVolumes().getEchoLimiter();
	}

	public boolean getExternalSpeaker()
	{
		return ((AudioManager) Util.getSystemService(this, Context.AUDIO_SERVICE)).isSpeakerphoneOn();
	}

	private void muteExternalSpeaker()
	{
		Lg.i("muteExternalSpeaker");
		((AudioManager) Util.getSystemService(this, Context.AUDIO_SERVICE)).setSpeakerphoneOn(false);
	}

	public void toggleExternalSpeaker()
	{
		Lg.i("toggleExternalSpeaker");
		final AudioManager audioManager = Util.getSystemService(this, Context.AUDIO_SERVICE);
		audioManager.setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
	}

	public MicrophoneStatus getMicrophoneStatus()
	{
		return getVolumes().getMicrophoneStatus();
	}

	private void setVolumes(final Volumes volumes)
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.setVolumes(volumes);
	}

	public void setMicrophoneVolume(final int progress)
	{
		setVolumes(getVolumes().setProgressMicrophone(progress));
	}

	public void setSpeakerVolume(final int progress)
	{
		setVolumes(getVolumes().setProgressSpeaker(progress));
	}

	public void setEchoLimiter(final boolean enabled)
	{
		final Volumes volumes = getVolumes();

		if (volumes.getEchoLimiter() != enabled) {
			setVolumes(volumes.toggleEchoLimiter());
		}
	}

	public void toggleMicrophoneMuted()
	{
		setVolumes(getVolumes().toggleMicrophoneMuted());
	}

	public void requestVideoUpdate(final boolean enable)
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.requestVideoUpdate(enable);
	}

	public void acceptVideoUpdate(final boolean accept)
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.acceptVideoUpdate(accept);
	}

	public void setVideoWindows(final SurfaceView videoView, final SurfaceView captureView)
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.setVideoWindows(videoView, captureView);
	}

	public void enableVideoWindow(final boolean enable)
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.enableVideoWindow(enable);
	}

	public void destroyVideoWindows()
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.destroyVideoWindows();
	}

	public void toggleCamera()
	{
		if (mLinphoneThread == null) {
			return;
		}

		mLinphoneThread.toggleCamera();
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

	public static void initActivities(final ServiceActivities activities)
	{
		if (ACTIVITIES == null) {
			ACTIVITIES = activities;
		}
	}
}

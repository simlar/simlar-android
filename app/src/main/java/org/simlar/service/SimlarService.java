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
import android.app.AlarmManager;
import android.app.KeyguardManager;
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
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.TextureView;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.util.Set;

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
import org.simlar.service.liblinphone.LinphoneManager;
import org.simlar.service.liblinphone.LinphoneManagerListener;
import org.simlar.utils.Util;

public final class SimlarService extends Service implements LinphoneManagerListener
{
	private static final int NOTIFICATION_ID = 1;
	private static final int NOTIFICATION_RINGING_ID = 2;
	private static final String INTENT_ACTION_NOTIFICATION_CALL_ACCEPT = "SimlarServiceCallAccept";
	private static final String INTENT_ACTION_NOTIFICATION_CALL_TERMINATE = "SimlarServiceCallTerminate";

	private static final long TERMINATE_CHECKER_INTERVAL = 20 * 1000; // milliseconds
	private static ServiceActivities ACTIVITIES = null;
	public static final String INTENT_EXTRA_SIMLAR_ID = "SimlarServiceSimlarId";
	@SuppressWarnings("WeakerAccess") // is only used in flavour push
	public static final String INTENT_EXTRA_GCM = "SimlarServiceGCM";

	private LinphoneManager mLinphoneManager = null;
	private final Handler mHandler = new Handler(Looper.getMainLooper());
	private final IBinder mBinder = new SimlarServiceBinder();
	private SimlarStatus mSimlarStatus = SimlarStatus.OFFLINE;
	private final SimlarCallState mSimlarCallState = new SimlarCallState();
	private CallConnectionDetails mCallConnectionDetails = new CallConnectionDetails();
	private WakeLock mWakeLock = null;
	private WakeLock mDisplayWakeLock = null;
	private WifiLock mWifiLock = null;
	private boolean mGoingDown = false;
	private boolean mTerminatePrivateAlreadyCalled = false;
	private static volatile Class<? extends AppCompatActivity> mNotificationActivity = null;
	private VibratorManager mVibratorManager = null;
	private SoundEffectManager mSoundEffectManager = null;
	private final NetworkChangeReceiver mNetworkChangeReceiver = new NetworkChangeReceiver();
	private String mSimlarIdToCall = null;
	private static volatile boolean mRunning = false;
	private final TelephonyCallStateListener mTelephonyCallStateListener = new TelephonyCallStateListener();
	private boolean mStreamRingNeedsUnMute = false;
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
		restoreAudioStreamRing();
		if (mSimlarStatus != SimlarStatus.ONGOING_CALL) {
			return;
		}

		if (mLinphoneManager == null) {
			return;
		}

		mSoundEffectManager.stop(SoundEffectType.CALL_INTERRUPTION);

		mLinphoneManager.pauseAllCalls();
	}

	private void onTelephonyCallStateIdle()
	{
		restoreAudioStreamRing();
		if (mSimlarStatus != SimlarStatus.ONGOING_CALL) {
			return;
		}

		if (mLinphoneManager == null) {
			return;
		}

		mSoundEffectManager.stop(SoundEffectType.CALL_INTERRUPTION);

		mLinphoneManager.resumeCall();
	}

	private void onTelephonyCallStateRinging()
	{
		if (mSimlarStatus != SimlarStatus.ONGOING_CALL) {
			return;
		}

		silenceAudioStreamRing();

		mSoundEffectManager.start(SoundEffectType.CALL_INTERRUPTION);
	}

	@SuppressWarnings("SameParameterValue")
	private static void muteAudioStream(final AudioManager audioManager, final int streamType, final boolean mute)
	{
		final int adJustMute = mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
		audioManager.adjustStreamVolume(streamType, adJustMute, 0);
	}

	private void silenceAudioStreamRing()
	{
		if (!PermissionsHelper.isNotificationPolicyAccessGranted(this)) {
			Lg.i("permission not granted to Do Not Disturb state");
			return;
		}

		final AudioManager audioManager = Util.getSystemService(this, Context.AUDIO_SERVICE);
		if (audioManager.isVolumeFixed()) {
			Lg.i("device does not support muting");
			return;
		}

		if (!audioManager.isStreamMute(AudioManager.STREAM_RING)) {
			mStreamRingNeedsUnMute = true;
			muteAudioStream(audioManager, AudioManager.STREAM_RING, true);
		}
	}

	private void restoreAudioStreamRing()
	{
		if (!mStreamRingNeedsUnMute) {
			return;
		}

		final AudioManager audioManager = Util.getSystemService(this, Context.AUDIO_SERVICE);
		mStreamRingNeedsUnMute = false;
		muteAudioStream(audioManager, AudioManager.STREAM_RING, false);
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		final String action = intent == null ? null : intent.getAction();
		Lg.i("onStartCommand action: ", action, " startId: ", startId, " flags: ", flags, " intent: ", intent);

		if (INTENT_ACTION_NOTIFICATION_CALL_ACCEPT.equals(action)) {
			pickUp();
			startActivity(new Intent(this, ACTIVITIES.getCallActivity()).addFlags(
					Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
		} else if (INTENT_ACTION_NOTIFICATION_CALL_TERMINATE.equals(action)) {
			terminateCall();
		} else {
			handleStartup(intent);
		}

		// We want this service to continue running until it is explicitly stopped, so return sticky.
		return START_STICKY;
	}

	private void handleStartup(final Intent intent)
	{
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
			if (mLinphoneManager == null) {
				startLinphone();
			}
		}

		startForeground(NOTIFICATION_ID, createNotification());
	}

	@Override
	public void onCreate()
	{
		Lg.i("onCreate");

		mRunning = true;

		mVibratorManager = new VibratorManager(getApplicationContext());
		mSoundEffectManager = new SoundEffectManager(getApplicationContext());

		mWakeLock = ((PowerManager) Util.getSystemService(this, Context.POWER_SERVICE))
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "simlar:WakeLock");
		mDisplayWakeLock = createDisplayWakeLock();
		mWifiLock = createWifiWakeLock();

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mNetworkChangeReceiver, intentFilter);

		if (PermissionsHelper.isNotificationPolicyAccessGranted(this)) {
			((TelephonyManager) Util.getSystemService(this, Context.TELEPHONY_SERVICE))
					.listen(mTelephonyCallStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		}

		ContactsProvider.preLoadContacts(this);

		startLinphone();
	}

	private void startLinphone()
	{
		Lg.i("startLinphone");
		if (!PreferencesHelper.readPreferencesFromFile(this)) {
			Lg.e("failed to initialize credentials");
			return;
		}

		mLinphoneManager = new LinphoneManager(this, this);
		mTerminatePrivateAlreadyCalled = false;
		notifySimlarStatusChanged(SimlarStatus.OFFLINE);
		connect();

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
				.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "simlar:DisplayWakeLock");
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

	public void registerActivityToNotification(final Class<? extends AppCompatActivity> activity)
	{
		if (activity == null) {
			Lg.e("registerActivityToNotification with empty activity");
			return;
		}

		if (activity.equals(mNotificationActivity)) {
			Lg.i("registerActivityToNotification already registered: ", activity.getSimpleName());
			return;
		}

		Lg.i("registerActivityToNotification: ", activity.getSimpleName());
		mNotificationActivity = activity;

		updateNotification();
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
				new Intent(context, ACTIVITIES.getMainActivity()).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), PendingIntent.FLAG_IMMUTABLE);

		final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, SimlarNotificationChannel.MISSED_CALL.name());
		notificationBuilder.setSmallIcon(R.drawable.ic_notification_missed_calls);
		notificationBuilder.setLargeIcon(ContactsProvider.getContactPhotoBitmap(context, R.drawable.contact_picture, photoId));
		notificationBuilder.setContentTitle(context.getString(R.string.missed_call_notification));
		notificationBuilder.setContentText(name);
		notificationBuilder.setContentIntent(activity);
		notificationBuilder.setAutoCancel(true);
		notificationBuilder.setWhen(System.currentTimeMillis());

		((NotificationManager) Util.getSystemService(context, Context.NOTIFICATION_SERVICE))
				.notify(PreferencesHelper.getNextMissedCallNotificationId(context), notificationBuilder.build());
	}

	private void updateNotification()
	{
		final NotificationManager nm = Util.getSystemService(this, Context.NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification());
	}

	private Notification createNotification()
	{
		final String text = createNotificationText();
		Lg.i("createNotification: ", text);

		if (mNotificationActivity == null) {
			if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
				mNotificationActivity = mSimlarCallState.isRinging() ? ACTIVITIES.getRingingActivity() : ACTIVITIES.getCallActivity();
			} else {
				mNotificationActivity = ACTIVITIES.getMainActivity();
			}
			Lg.i("no activity registered based on mSimlarStatus=", mSimlarStatus, " we now take: ", mNotificationActivity.getSimpleName());
		}

		final PendingIntent activity = PendingIntent.getActivity(this, 0,
				new Intent(this, mNotificationActivity).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), PendingIntent.FLAG_IMMUTABLE);

		final NotificationCompat.Builder notificationBuilder =
				new NotificationCompat.Builder(this, SimlarNotificationChannel.CALL.name())
						.setSmallIcon(FlavourHelper.isGcmEnabled() ? R.drawable.ic_notification_ongoing_call : mSimlarStatus.getNotificationIcon())
						.setLargeIcon(mSimlarCallState.getContactPhotoBitmap(this, R.drawable.contact_picture))
						.setContentTitle(getString(R.string.app_name))
						.setContentText(text)
						.setOngoing(true)
						.setContentIntent(activity)
						.setWhen(System.currentTimeMillis());

		return notificationBuilder.build();
	}

	private Notification createNotificationRinging()
	{
		Lg.i("createNotification ringing");

		mNotificationActivity = ACTIVITIES.getRingingActivity();
		final PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, ACTIVITIES.getRingingActivity()),
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

		final PendingIntent declineIntent = PendingIntent.getService(this, 0,
				new Intent(this, SimlarService.class).setAction(INTENT_ACTION_NOTIFICATION_CALL_TERMINATE),
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

		final PendingIntent acceptIntent = PendingIntent.getService(this, 0,
				new Intent(this, SimlarService.class).setAction(INTENT_ACTION_NOTIFICATION_CALL_ACCEPT),
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

		final RemoteViews notificationHeadsUp = new RemoteViews(getPackageName(), R.layout.notification_ringing_heads_up);
		notificationHeadsUp.setTextViewText(R.id.contactName, mSimlarCallState.getContactName());
		notificationHeadsUp.setOnClickPendingIntent(R.id.buttonDecline, declineIntent);
		notificationHeadsUp.setOnClickPendingIntent(R.id.buttonAccept, acceptIntent);
		notificationHeadsUp.setImageViewBitmap(R.id.contactImage, mSimlarCallState.getContactPhotoBitmap(this, R.drawable.contact_picture));

		final NotificationCompat.Builder notificationBuilder =
				new NotificationCompat.Builder(this, SimlarNotificationChannel.INCOMING_CALL.name())
						.setSmallIcon(R.drawable.ic_notification_ongoing_call)
						.setLargeIcon(mSimlarCallState.getContactPhotoBitmap(this, R.drawable.contact_picture))
						.setContentTitle(getString(R.string.app_name))
						.setContentText(String.format(getString(R.string.ringing_notification_text), mSimlarCallState.getContactName()))
						.setOngoing(true)
						.setWhen(System.currentTimeMillis())
						.setPriority(NotificationCompat.PRIORITY_HIGH)
						.setCategory(NotificationCompat.CATEGORY_CALL)
						.setFullScreenIntent(fullScreenPendingIntent, true)
						.setCustomHeadsUpContentView(notificationHeadsUp)
						.addAction(R.drawable.button_ringing_hang_up, getString(R.string.ringing_notification_decline), declineIntent)
						.addAction(R.drawable.button_ringing_pick_up, getString(R.string.ringing_notification_accept), acceptIntent);

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
		if (mLinphoneManager == null) {
			Lg.e("no LinphoneManager on connect");
			return;
		}

		notifySimlarStatusChanged(SimlarStatus.CONNECTING);

		try {
			mLinphoneManager.register(PreferencesHelper.getMySimlarId(), PreferencesHelper.getPassword());
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
		if (mLinphoneManager == null) {
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
			mLinphoneManager.refreshRegisters();
		}
	}

	@SuppressLint("UnspecifiedImmutableFlag")
	private static PendingIntent getPendingIntent(final Context context, final Intent startIntent) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return PendingIntent.getBroadcast(context, 0, startIntent, PendingIntent.FLAG_MUTABLE);
		} else {
			return PendingIntent.getBroadcast(context, 0, startIntent, 0);
		}
	}

	private void startKeepAwake()
	{
		if (mKeepAwakeReceiver == null) {
			return;
		}

		final Intent startIntent = new Intent("org.simlar.keepAwake");
		mKeepAwakePendingIntent = getPendingIntent(this, startIntent);

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
			updateNotification();
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
	                               final int upload, final int download, final int jitter, final int packetLoss, final long latePackets,
	                               final int roundTripDelay, final String encryptionDescription)
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

		if (!mCallConnectionDetails.updateCallStats(quality, codec, iceState, upload, download, jitter, packetLoss, latePackets, roundTripDelay, encryptionDescription)) {
			Lg.v("CallConnectionDetails staying the same: ", mCallConnectionDetails);
			return;
		}

		Lg.d("CallConnectionDetails updated: ", mCallConnectionDetails);
		SimlarServiceBroadcast.sendCallConnectionDetailsChanged(this);
	}

	@Override
	public void onCallStateChanged(final String number, final State callState, final CallEndReason callEndReason)
	{
		final boolean oldCallStateRinging = mSimlarCallState.isRinging();
		if (!mSimlarCallState.updateCallStateChanged(number, LinphoneCallState.fromLinphoneCallState(callState), callEndReason)) {
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
			mLinphoneManager.setMicrophoneStatus(MicrophoneStatus.DISABLED);
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
		}

		if (!mSimlarCallState.isRinging()) {
			final NotificationManager nm = Util.getSystemService(this, Context.NOTIFICATION_SERVICE);
			nm.cancel(NOTIFICATION_RINGING_ID);
		}

		if (mSimlarCallState.isEndedCall()) {
			notifySimlarStatusChanged(SimlarStatus.ONLINE);
			mSoundEffectManager.stopAll();
			mSoundEffectManager.setInCallMode(false);

			restoreAudioStreamRing();

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

			if (mSimlarCallState.isRinging()) {
				Lg.i("notify incoming call with full screen notification");
				final NotificationManager nm = Util.getSystemService(this, Context.NOTIFICATION_SERVICE);
				nm.notify(NOTIFICATION_RINGING_ID, createNotificationRinging());
			}

			updateNotification();

			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && isScreenLocked() && mSimlarCallState.isRinging()) {
				Lg.i("starting RingingActivity");
				mNotificationActivity = ACTIVITIES.getRingingActivity();
				startActivity(new Intent(this, ACTIVITIES.getRingingActivity()).addFlags(
						Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
			}

			SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
		});
	}

	private boolean isScreenLocked() {
		final KeyguardManager keyguardManager = Util.getSystemService(this, Context.KEYGUARD_SERVICE);
		return keyguardManager.inKeyguardRestrictedInputMode();
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

		mLinphoneManager.setMicrophoneStatus(MicrophoneStatus.ON);
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

	@Override
	public void onAudioOutputChanged(final AudioOutputType currentAudioOutputType, final Set<AudioOutputType> availableAudioOutputTypes)
	{
		Lg.i("onAudioOutputChanged: currentAudioOutputType=", currentAudioOutputType, " availableAudioOutputTypes=", TextUtils.join(",", availableAudioOutputTypes));
		SimlarServiceBroadcast.sendAudioOutputChanged(this, currentAudioOutputType, availableAudioOutputTypes);
	}

	private void call(final String simlarId)
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.call(simlarId);
	}

	public void pickUp()
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.pickUp();
	}

	public void terminateCall()
	{
		if (mLinphoneManager == null) {
			return;
		}

		mNotificationActivity = ACTIVITIES.getMainActivity();
		updateNotification();

		if (mSimlarStatus == SimlarStatus.ONGOING_CALL) {
			mLinphoneManager.terminateAllCalls();
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

		if (mLinphoneManager != null && mSimlarStatus.isConnectedToSipServer()) {
			mLinphoneManager.unregister();

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
		if (mLinphoneManager != null) {
			mLinphoneManager.finish();
			mLinphoneManager = null;
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
		if (mLinphoneManager == null) {
			Lg.e("ERROR: verifyAuthenticationToken called but no linphone thread");
			return;
		}

		if (Util.isNullOrEmpty(mSimlarCallState.getAuthenticationToken())) {
			Lg.e("ERROR: verifyAuthenticationToken called but no token available");
			return;
		}

		mLinphoneManager.verifyAuthenticationToken(mSimlarCallState.getAuthenticationToken(), verified);
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
		if (mLinphoneManager == null) {
			return new Volumes();
		}

		return mLinphoneManager.getVolumes();
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

	private void muteExternalSpeaker()
	{
		Lg.i("toggleExternalSpeaker");
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.setCurrentAudioOutputType(AudioOutputType.PHONE);
	}

	public MicrophoneStatus getMicrophoneStatus()
	{
		return getVolumes().getMicrophoneStatus();
	}

	private void setVolumes(final Volumes volumes)
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.setVolumes(volumes);
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

	public void setCurrentAudioOutputType(final AudioOutputType type)
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.setCurrentAudioOutputType(type);
	}

	public void requestVideoUpdate(final boolean enable)
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.requestVideoUpdate(enable);
	}

	public void acceptVideoUpdate(final boolean accept)
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.acceptVideoUpdate(accept);
	}

	public void setVideoWindows(final TextureView videoView, final TextureView captureView)
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.setVideoWindows(videoView, captureView);
	}

	public void destroyVideoWindows()
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.destroyVideoWindows();
	}

	public void toggleCamera()
	{
		if (mLinphoneManager == null) {
			return;
		}

		mLinphoneManager.toggleCamera();
	}

	public CallConnectionDetails getCallConnectionDetails()
	{
		return mCallConnectionDetails;
	}

	public static boolean isRunning()
	{
		return mRunning;
	}

	public static Class<? extends AppCompatActivity> getActivity()
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

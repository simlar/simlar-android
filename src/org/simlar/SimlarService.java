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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.simlar.PreferencesHelper.NotInitedException;
import org.simlar.SoundEffectManager.SoundEffectType;
import org.simlar.Volumes.MicrophoneStatus;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public final class SimlarService extends Service implements LinphoneThreadListener
{
	static final String LOGTAG = SimlarService.class.getSimpleName();
	private static final int NOTIFICATION_ID = 1;

	LinphoneThread mLinphoneThread = null;
	final Handler mHandler = new Handler();
	private final IBinder mBinder = new SimlarServiceBinder();
	Map<String, ContactData> mContacts = new HashMap<String, ContactData>();
	private SimlarStatus mSimlarStatus = SimlarStatus.OFFLINE;
	private final SimlarCallState mSimlarCallState = new SimlarCallState();
	private CallConnectionDetails mCallConnectionDetails = new CallConnectionDetails();
	private WakeLock mWakeLock = null;
	private WifiLock mWifiLock = null;
	private boolean mGoingDown = false;
	private boolean mTerminatePrivateAlreadyCalled = false;
	private boolean mCreatingAccount = false;
	private Class<?> mNotificationActivity = null;
	private VibratorManager mVibratorManager = null;
	private SoundEffectManager mSoundEffectManager = null;
	private boolean mHasAudioFocus = false;
	private final NetworkChangeReceiver mNetworkChangeReceiver = new NetworkChangeReceiver();
	private PendingIntent mkeepAwakePendingIntent = null;
	private final KeepAwakeReceiver mKeepAwakeReceiver = new KeepAwakeReceiver();

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

	private final class KeepAwakeReceiver extends BroadcastReceiver
	{
		public KeepAwakeReceiver()
		{
		}

		@Override
		public void onReceive(Context context, Intent intent)
		{
			SimlarService.this.keepAwake();
		}
	}

	public class ContactData
	{
		public final String name;
		public final String guiTelephoneNumber;

		public ContactStatus status;
		public final String photoId;

		public ContactData(final String name, final String guiTelephoneNumber, final ContactStatus status, final String photoId)
		{
			this.name = name;
			this.guiTelephoneNumber = guiTelephoneNumber;
			this.status = status;
			this.photoId = photoId;
		}

		public boolean isRegistered()
		{
			return status.isRegistered();
		}
	}

	public final class FullContactData extends ContactData
	{
		public final String simlarId;

		public FullContactData(final String simlarId, final String name, final String guiTelephoneNumber, final ContactStatus status,
				final String photoId)
		{
			super(name, guiTelephoneNumber, status, photoId);
			this.simlarId = simlarId;
		}

		public FullContactData(final String simlarId, final ContactData cd)
		{
			super(cd.name, cd.guiTelephoneNumber, cd.status, cd.photoId);
			this.simlarId = simlarId;
		}

		public String getNameOrNumber()
		{
			if (Util.isNullOrEmpty(name)) {
				return simlarId;
			}

			return name;
		}
	}

	@Override
	public IBinder onBind(final Intent arg0)
	{
		Log.i(LOGTAG, "onBind");
		return mBinder;
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		Log.i(LOGTAG, "onStartCommand intent=" + intent + " startId=" + startId);

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
				.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "SimlarWakeLock");
		mWifiLock = ((WifiManager) this.getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL, "SimlarWifiLock");

		startForeground(NOTIFICATION_ID, createNotification(SimlarStatus.OFFLINE));

		mLinphoneThread = new LinphoneThread(this, this);

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(mNetworkChangeReceiver, intentFilter);

		startKeepAwake();

		mHandler.post(new Runnable() {
			@Override
			public void run()
			{
				initializeCredentials();
			}
		});
	}

	public void registerActivityToNotification(final Class<?> activity)
	{
		if (activity == null) {
			Log.e(LOGTAG, "registerActivityToNotification with empty activity");
			return;
		}

		Log.i(LOGTAG, "registerActivityToNotification: " + activity.getSimpleName());
		mNotificationActivity = activity;

		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification(mSimlarStatus));
	}

	Notification createNotification(final SimlarStatus status)
	{
		String text = null;

		if (mNotificationActivity == null || (status != SimlarStatus.ONGOING_CALL && !mCreatingAccount)) {
			mNotificationActivity = MainActivity.class;
		}

		final PendingIntent activity = PendingIntent.getActivity(this, 0,
				new Intent(this, mNotificationActivity).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), 0);

		if (mCreatingAccount) {
			text = getString(R.string.notification_simlar_status_creating_account);
			if (!Util.isNullOrEmpty(PreferencesHelper.getMySimlarIdOrEmptyString())) {
				text += ": " + String.format(getString(status.getNotificationTextId()), PreferencesHelper.getMySimlarIdOrEmptyString());
			}
		} else {
			text = String.format(getString(status.getNotificationTextId()), PreferencesHelper.getMySimlarIdOrEmptyString());
		}

		final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(status.getNotificationIcon());
		notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.app_logo));
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

		if (PreferencesHelper.readPrefencesFromFile(this)) {
			connect();
		} else {
			mCreatingAccount = true;
			notifySimlarStatusChanged(mSimlarStatus);
			startActivity(new Intent(this, VerifyNumberActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
		}
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

		stopKeepAwake();

		// just in case
		releaseWakeLock();
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

	private void acquireWifiLock()
	{
		if (!mWifiLock.isHeld()) {
			mWifiLock.acquire();
		}
	}

	void releaseWakeLock()
	{
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
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

	private void startKeepAwake()
	{
		final Intent startIntent = new Intent("org.simlar.keepAwake");
		mkeepAwakePendingIntent = PendingIntent.getBroadcast(this, 0, startIntent, 0);

		((AlarmManager) getSystemService(Context.ALARM_SERVICE))
				.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 600000, 600000, mkeepAwakePendingIntent);

		IntentFilter filter = new IntentFilter();
		filter.addAction("org.simlar.keepAwake");
		registerReceiver(mKeepAwakeReceiver, filter);
	}

	private void stopKeepAwake()
	{
		unregisterReceiver(mKeepAwakeReceiver);
		((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(mkeepAwakePendingIntent);
	}

	void keepAwake()
	{
		// idea from linphones KeepAliveHandler

		checkNetworkConnectivityAndRefreshRegisters();

		// make sure iterate will have enough time before device eventually goes to sleep
		acquireWakeLock();
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run()
			{
				if (!getSimlarCallState().isNewCall()) {
					releaseWakeLock();
				}
			}
		}, 4000);
	}

	@Override
	public void onRegistrationStateChanged(final RegistrationState state)
	{
		Log.i(LOGTAG, "onRegistrationStateChanged: " + state);

		SimlarStatus status = SimlarStatus.fromRegistrationState(state);

		if (mCreatingAccount) {
			if (status.isRegistrationAtSipServerFailed()) {
				Log.i(LOGTAG, "creating account: registration failed");
				//mLinphoneThread.unregister();
				SimlarServiceBroadcast.sendTestRegistrationFailed(this);
			}

			if (status.isConnectedToSipServer()) {
				Log.i(LOGTAG, "creating account: registration succes");
				mCreatingAccount = false;

				SimlarServiceBroadcast.sendTestRegistrationSuccess(this);
			}
		}

		if (RegistrationState.RegistrationOk.equals(state)) {
			loadContactsFromTelephonebook();
			status = SimlarStatus.LOADING_CONTACTS;
		}

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

		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, createNotification(status));

		mSimlarStatus = status;

		SimlarServiceBroadcast.sendSimlarStatusChanged(this);
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
		final FullContactData contact = getContact(number);
		if (!mSimlarCallState.updateCallStateChanged(contact.getNameOrNumber(), contact.photoId, callState, message)) {
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

			acquireWakeLock();
			acquireWifiLock();

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
			} else {
				Log.i(LOGTAG, "starting CallActivity");
				startActivity(new Intent(SimlarService.this, CallActivity.class).addFlags(
						Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
			}
		}

		if (mSimlarCallState.isEndedCall()) {
			notifySimlarStatusChanged(SimlarStatus.ONLINE);

			releaseWakeLock();
			releaseWifiLock();

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
		}

		SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
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

	void loadContactsFromTelephonebook()
	{
		new AsyncTask<Void, Void, Map<String, ContactData>>() {
			@Override
			protected Map<String, ContactData> doInBackground(final Void... params)
			{
				Log.i(LOGTAG, "loading contacts from telephone book");
				final Map<String, ContactData> result = new HashMap<String, SimlarService.ContactData>();

				final String[] projection = new String[] {
						ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
						ContactsContract.CommonDataKinds.Phone.NUMBER,
						ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
						ContactsContract.CommonDataKinds.Phone.PHOTO_ID
				};

				final Cursor contacts = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null);
				while (contacts.moveToNext())
				{
					final long contactId = contacts.getLong(0);
					final String number = contacts.getString(1);
					final String name = contacts.getString(2);
					final boolean hasPhotoId = contacts.getLong(3) != 0;
					String photoUri = null;

					if (Util.isNullOrEmpty(number)) {
						continue;
					}

					final SimlarNumber simlarNumber = new SimlarNumber(number);
					if (Util.isNullOrEmpty(simlarNumber.getSimlarId())) {
						continue;
					}

					if (hasPhotoId) {
						photoUri = Uri.withAppendedPath(ContentUris.withAppendedId(
								ContactsContract.Contacts.CONTENT_URI, contactId), ContactsContract.Contacts.Photo.CONTENT_DIRECTORY).toString();
					}

					if (!result.containsKey(simlarNumber.getSimlarId())) {
						result.put(simlarNumber.getSimlarId(), new ContactData(name, simlarNumber.getGuiTelephoneNumber(), ContactStatus.UNKNOWN,
								photoUri));

						/// ATTENTIION this logs the users telephone book
						//Log.d(LOGTAG, "adding contact " + name + " " + number + " => " + simlarNumber.getSimlarId());
					}
				}
				contacts.close();

				return result;
			}

			@Override
			protected void onPostExecute(final Map<String, ContactData> result)
			{
				mContacts = result;
				requestRegisteredContacts();
			}
		}.execute();
	}

	@SuppressWarnings("unchecked")
	public void requestRegisteredContacts()
	{
		new AsyncTask<Set<String>, Void, Map<String, ContactStatus>>() {

			@Override
			protected Map<String, ContactStatus> doInBackground(final Set<String>... params)
			{
				return GetContactsStatus.httpPostGetContactsStatus(params[0]);
			}

			@Override
			protected void onPostExecute(final Map<String, ContactStatus> result)
			{
				if (result == null) {
					Log.i(LOGTAG, "getting contacts status failed");
					notifySimlarStatusChanged(SimlarStatus.ERROR_LOADING_CONTACTS);
					return;
				}

				for (final String simlarId : result.keySet()) {
					updateContactData(simlarId, result.get(simlarId));
				}

				if (RegistrationState.RegistrationOk.equals(mLinphoneThread.getRegistrationState())) {
					notifySimlarStatusChanged(SimlarStatus.ONLINE);
				}
			}
		}.execute(mContacts.keySet());
	}

	void updateContactData(final String simlarId, final ContactStatus status)
	{
		if (Util.isNullOrEmpty(simlarId)) {
			return;
		}

		final ContactData cd = mContacts.get(simlarId);
		if (cd == null) {
			Log.w(LOGTAG, "updateContactData: new simlerId=" + simlarId);
			mContacts.put(simlarId, new ContactData(null, null, status, null));
			return;
		}

		if (!status.isValid()) {
			return;
		}

		cd.status = status;
	}

	public Set<FullContactData> getContacts()
	{
		final Set<FullContactData> contacts = new HashSet<FullContactData>();
		for (final Map.Entry<String, ContactData> entry : mContacts.entrySet()) {
			if (entry.getValue().isRegistered()) {
				contacts.add(new FullContactData(entry.getKey(), entry.getValue()));
			}
		}
		return contacts;
	}

	private FullContactData getContact(final String simlarId)
	{
		if (Util.isNullOrEmpty(simlarId) || !mContacts.containsKey(simlarId)) {
			return new FullContactData(simlarId, "", "", ContactStatus.UNKNOWN, "");
		}

		return new FullContactData(simlarId, mContacts.get(simlarId));
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

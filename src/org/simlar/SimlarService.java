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

public class SimlarService extends Service implements LinphoneHandlerListener
{
	static final String LOGTAG = SimlarService.class.getSimpleName();
	private static final int NOTIFICATION_ID = 1;

	LinphoneThread mLinphoneThread = null;
	Handler mHandler = new Handler();
	private final IBinder mBinder = new SimlarServiceBinder();
	Map<String, ContactData> mContacts = new HashMap<String, ContactData>();
	private SimlarStatus mSimlarStatus = SimlarStatus.OFFLINE;
	private SimlarCallState mSimlarCallState = new SimlarCallState();
	private WakeLock mWakeLock = null;
	private WifiLock mWifiLock = null;
	private boolean mGoingDown = false;
	private boolean mTerminatePrivateAlreadyCalled = false;
	private boolean mCreatingAccount = false;
	private Class<?> mNotificationActivity = null;
	private VibratorThread mVibratorThread = null;
	private RingtoneThread mRingtoneThread = null;
	private boolean mHasAudioFocus = false;
	private NetworkChangeReceiver mNetworkChangeReceiver = new NetworkChangeReceiver();
	private PendingIntent mkeepAwakePendingIntent = null;
	private KeepAwakeReceiver mKeepAwakeReceiver = new KeepAwakeReceiver();

	public class SimlarServiceBinder extends Binder
	{
		SimlarService getService()
		{
			return SimlarService.this;
		}
	}

	class NetworkChangeReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			SimlarService.this.checkNetworkConnectivityAndRefreshRegisters();
		}
	}

	class KeepAwakeReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			SimlarService.this.keepAwake();
		}
	}

	public class ContactData
	{
		public String name;
		public ContactStatus status;
		public String photoId;

		public ContactData(final String name, final ContactStatus status, final String photoId)
		{
			this.name = name;
			this.status = status;
			this.photoId = photoId;
		}

		public boolean isRegistered()
		{
			return status.isRegistered();
		}

		public boolean isOnline()
		{
			return status.isOnline();
		}
	}

	public class FullContactData extends ContactData
	{
		public String number;

		public FullContactData(final String number, final String name, final ContactStatus status, final String photoId)
		{
			super(name, status, photoId);
			this.number = number;
		}

		public FullContactData(final String number, final ContactData cd)
		{
			super(cd.name, cd.status, cd.photoId);
			this.number = number;
		}

		public String getNameOrNumber()
		{
			if (Util.isNullOrEmpty(name)) {
				return number;
			}

			return name;
		}
	}

	@Override
	public IBinder onBind(Intent arg0)
	{
		Log.i(LOGTAG, "onBind");
		return mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.i(LOGTAG, "onStartCommand intent=" + intent + " startId=" + startId);

		// We want this service to continue running until it is explicitly stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onCreate()
	{
		Log.i(LOGTAG, "started on device: " + Build.DEVICE);

		FileHelper.init(this);
		mVibratorThread = new VibratorThread(this.getApplicationContext());
		mRingtoneThread = new RingtoneThread(this.getApplicationContext());

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

		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
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
		notifySimlarStatusChanged(SimlarStatus.CONNECTING);

		try {
			mLinphoneThread.register(PreferencesHelper.getMySimlarId(), PreferencesHelper.getPassword());
		} catch (NotInitedException e) {
			Log.e(LOGTAG, "PreferencesHelper.NotInitedException", e);
		}
	}

	@Override
	public synchronized void onDestroy()
	{
		Log.i(LOGTAG, "onDestroy");

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

		if (state == RegistrationState.RegistrationOk) {
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
	public void onCallStatsChanged(final float upload, final float download, final float quality, final String codec, final String iceState)
	{
		if (!mSimlarCallState.updateCallStats(upload, download, quality, codec, iceState)) {
			Log.d(LOGTAG, "IceStateChanged but SimlarCallState unchanged: " + mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Log.w(LOGTAG, "IceStateChanged but SimlarCallState isEmpty");
			return;
		}

		Log.i(LOGTAG, "SimlarCallState IceStateChanged: " + mSimlarCallState);

		SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
	}

	@Override
	public void onCallStateChanged(final String number, final State callState, final String message)
	{
		if (!mSimlarCallState.updateCallStateChanged(getContact(number).getNameOrNumber(), callState, message)) {
			Log.d(LOGTAG, "SimlarCallState staying the same: " + mSimlarCallState);
			return;
		}

		if (mSimlarCallState.isEmpty()) {
			Log.e(LOGTAG, "SimlarCallState is empty: " + mSimlarCallState);
			return;
		}

		Log.i(LOGTAG, "SimlarCallState updated: " + mSimlarCallState);

		if (mSimlarCallState.isRinging()) {
			mVibratorThread.start();
			mRingtoneThread.start();
		} else {
			mVibratorThread.stop();
			mRingtoneThread.stop();
		}

		// make sure WLAN is not suspended while calling
		if (mSimlarCallState.isNewCall()) {
			notifySimlarStatusChanged(SimlarStatus.ONGOING_CALL);

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

			if (mHasAudioFocus) {
				final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManager.abandonAudioFocus(null) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
					Log.i(LOGTAG, "audio focus released");
				} else {
					Log.e(LOGTAG, "releasing audio focus not granted");
				}
				mHasAudioFocus = false;
			}
		}

		SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
	}

	@Override
	public void onPresenceStateChanged(final String number, final boolean online)
	{
		if (online) {
			Log.i(LOGTAG, "onPresenceStateChanged online " + number);
		}

		// we assume here that we only get the presence state of registered users
		if (updateContactData(number, online ? ContactStatus.ONLINE : ContactStatus.OFFLINE)) {
			if (online) {
				Log.i(LOGTAG, "notifyPresenceStateChanged online " + number);
			} else {
				Log.i(LOGTAG, "notifyPresenceStateChanged offline " + number);
			}

			SimlarServiceBroadcast.sendPresenceStateChanged(this, number, online);
		}
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
		SimlarServiceBroadcast.sendSimlarCallStateChanged(this);
	}

	public void call(final String simlarId)
	{
		mLinphoneThread.call(simlarId);
	}

	public void pickUp()
	{
		mLinphoneThread.pickUp();
	}

	public void terminateCall()
	{
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
		} catch (InterruptedException e) {
			Log.e(LOGTAG, "join interrupted: " + e.getMessage(), e);
		}
		SimlarServiceBroadcast.sendServiceFinishes(this);

		// make sure the notification update is done before destruction by firing destruction event to the handler
		mHandler.post(new Runnable() {
			@Override
			public void run()
			{
				Log.i(LOGTAG, "terminatePrivate: calling stopSelf");
				stopForeground(true);
				stopSelf();
			}
		});
	}

	void loadContactsFromTelephonebook()
	{
		new AsyncTask<Void, Void, Map<String, ContactData>>() {
			@Override
			protected Map<String, ContactData> doInBackground(Void... params)
			{
				Log.i(LOGTAG, "loading contacts from telephone book");
				Map<String, ContactData> result = new HashMap<String, SimlarService.ContactData>();

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
					final String number = SimlarNumber.createSimlarNumber(contacts.getString(1));
					final String name = contacts.getString(2);
					final boolean hasPhotoId = contacts.getLong(3) != 0;
					String photoUri = null;

					if (Util.isNullOrEmpty(number)) {
						continue;
					}

					if (hasPhotoId) {
						Uri u = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
						u = Uri.withAppendedPath(u, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
						photoUri = u.toString();
					}

					if (!result.containsKey(number)) {
						result.put(number, new ContactData(name, ContactStatus.UNKNOWN, photoUri));
						Log.d(LOGTAG, "adding contact " + name + " " + number);
					}
				}
				contacts.close();

				return result;
			}

			@Override
			protected void onPostExecute(Map<String, ContactData> result)
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
			protected Map<String, ContactStatus> doInBackground(Set<String>... params)
			{
				return GetContactsStatus.httpPostGetContactsStatus(params[0]);
			}

			@Override
			protected void onPostExecute(Map<String, ContactStatus> result)
			{
				if (result == null) {
					Log.i(LOGTAG, "getting contacts status failed");
					notifySimlarStatusChanged(SimlarStatus.ERROR_LOADING_CONTACTS);
					return;
				}

				for (final String number : result.keySet()) {
					final ContactStatus status = result.get(number);

					if (!status.isRegistered()) {
						updateContactData(number, status);
					} else {
						onPresenceStateChanged(number, status.isOnline());

						// make sure to add friends in the gui thread
						mHandler.post(new Runnable() {
							@Override
							public void run()
							{
								addLinphoneFriend(number);
							}
						});
					}
				}

				if (mLinphoneThread.getRegistrationState() == RegistrationState.RegistrationOk) {
					notifySimlarStatusChanged(SimlarStatus.ONLINE);
				}
			}
		}.execute(mContacts.keySet());
	}

	boolean updateContactData(final String number, final ContactStatus status)
	{
		if (Util.isNullOrEmpty(number)) {
			return false;
		}

		ContactData cd = mContacts.get(number);
		if (cd == null) {
			mContacts.put(number, new ContactData(null, status, null));
			return true;
		}

		if (cd.status == status) {
			return false;
		}

		cd.status = status;
		return true;
	}

	void addLinphoneFriend(final String number)
	{
		Log.d(LOGTAG, "adding linphone friend for presence watching: " + number);
		mLinphoneThread.addFriend(number);
	}

	public Set<FullContactData> getContacts()
	{
		Set<FullContactData> contacts = new HashSet<FullContactData>();
		for (final Map.Entry<String, ContactData> entry : mContacts.entrySet()) {
			if (entry.getValue().isRegistered()) {
				contacts.add(new FullContactData(entry.getKey(), entry.getValue()));
			}
		}
		return contacts;
	}

	public FullContactData getContact(final String number)
	{
		if (Util.isNullOrEmpty(number) || !mContacts.containsKey(number)) {
			return new FullContactData(number, "", ContactStatus.UNKNOWN, "");
		}

		return new FullContactData(number, mContacts.get(number));
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
		return mLinphoneThread.getVolumes();
	}

	public void setVolumes(final Volumes volumes)
	{
		mLinphoneThread.setVolumes(volumes);
	}
}

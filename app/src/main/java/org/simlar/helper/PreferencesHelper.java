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

package org.simlar.helper;

import android.content.Context;
import android.content.SharedPreferences;

import org.simlar.utils.Util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public final class PreferencesHelper
{
	private static final String PREFERENCES_FILE = "settings";
	private static final String PREFERENCES_USER = "user";
	private static final String PREFERENCES_PASSWORD = "password";
	private static final String PREFERENCES_REGION = "region";
	private static final String PREFERENCES_CREATE_ACCOUNT_STATUS = "create_account_status";
	private static final String PREFERENCES_CREATE_ACCOUNT_REQUEST_TIMESTAMP = "create_account_request_timestamp";
	private static final String PREFERENCES_GCM_REGISTRATION_ID = "gcm_registration_id";
	private static final String PREFERENCES_SIMLAR_VERSION_CODE = "simlar_version_code";
	private static final String PREFERENCES_DEBUG_MODE = "debug_mode";
	private static final boolean PREFERENCES_DEBUG_MODE_DEFAULT = Version.showDeveloperMenu();
	private static final String PREFERENCES_VERIFIED_TELEPHONE_NUMBER = "verified_telephone_number";
	private static final String PREFERENCES_MISSED_CALL_NOTIFICATION_ID = "missed_call_notification_id";
	private static final int MISSED_CALL_NOTIFICATION_ID_MIN = 2;
	private static final int MISSED_CALL_NOTIFICATION_ID_MAX = 256;

	private static String mMySimlarId = null;
	private static String mPassword = null;
	private static String mPasswordHash = null;
	private static Date mCreateAccountRequestTimestamp = null;
	private static CreateAccountStatus mCreateAccountStatus = CreateAccountStatus.NONE;
	private static String mGcmRegistrationId = null;
	private static int mSimlarVersionCode = -1;
	private static String mVerifiedTelephoneNumber = null;
	private static int mMissedCallNotificationId = MISSED_CALL_NOTIFICATION_ID_MIN;

	private PreferencesHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static void init(final String mySimlarId, final String password, final Date createAccountRequestTimestamp )
	{
		mMySimlarId = mySimlarId;
		mPassword = password;
		mCreateAccountRequestTimestamp = createAccountRequestTimestamp;

		createPasswordHash();
	}

	private static void createPasswordHash()
	{
		// kamailio password hash md5(username:realm:password)
		mPasswordHash = md5(mMySimlarId + ':' + ServerSettings.DOMAIN + ':' + mPassword);
	}

	private static String md5(final String str)
	{
		try {
			final MessageDigest digest = MessageDigest.getInstance("md5");
			digest.update(str.getBytes());
			final StringBuilder sb = new StringBuilder();
			for (final byte b : digest.digest()) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (final NoSuchAlgorithmException e) {
			return "";
		}
	}

	public static final class NotInitedException extends IllegalStateException
	{
		private static final long serialVersionUID = 1;
	}

	public static String getMySimlarId() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mMySimlarId)) {
			throw new NotInitedException();
		}
		return mMySimlarId;
	}

	public static String getMySimlarIdOrEmptyString()
	{
		if (Util.isNullOrEmpty(mMySimlarId)) {
			return "";
		}
		return mMySimlarId;
	}

	public static String getPasswordHash() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mPasswordHash)) {
			throw new NotInitedException();
		}
		return mPasswordHash;
	}

	public static String getPassword() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mPassword)) {
			throw new NotInitedException();
		}
		return mPassword;
	}

	public static Date getCreateAccountRequestTimestamp() throws NotInitedException
	{
		return mCreateAccountRequestTimestamp;
	}

	public static CreateAccountStatus getCreateAccountStatus()
	{
		return mCreateAccountStatus;
	}

	public static String getVerifiedTelephoneNumber()
	{
		return mVerifiedTelephoneNumber;
	}

	@SuppressWarnings("unused") // is only used in flavour push
	public static String getGcmRegistrationId()
	{
		return mGcmRegistrationId;
	}

	@SuppressWarnings("unused") // is only used in flavour push
	public static int getSimlarVersionCode()
	{
		return mSimlarVersionCode;
	}

	public static boolean readPreferencesFromFile(final Context context)
	{
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);

		mMySimlarId = settings.getString(PREFERENCES_USER, null);
		mPassword = settings.getString(PREFERENCES_PASSWORD, null);
		final int region = settings.getInt(PREFERENCES_REGION, -1);
		mCreateAccountRequestTimestamp = new Date(settings.getLong(PREFERENCES_CREATE_ACCOUNT_REQUEST_TIMESTAMP, 0));
		mCreateAccountStatus = CreateAccountStatus.fromInt(settings.getInt(PREFERENCES_CREATE_ACCOUNT_STATUS, 0));
		mGcmRegistrationId = settings.getString(PREFERENCES_GCM_REGISTRATION_ID, null);
		mSimlarVersionCode = settings.getInt(PREFERENCES_SIMLAR_VERSION_CODE, -1);
		mVerifiedTelephoneNumber = settings.getString(PREFERENCES_VERIFIED_TELEPHONE_NUMBER, null);
		mMissedCallNotificationId = settings.getInt(PREFERENCES_MISSED_CALL_NOTIFICATION_ID, MISSED_CALL_NOTIFICATION_ID_MIN);

		if (Util.isNullOrEmpty(mMySimlarId)) {
			return false;
		}

		if (Util.isNullOrEmpty(mPassword)) {
			return false;
		}

		if (region < 1) {
			return false;
		}

		if (mCreateAccountStatus != CreateAccountStatus.SUCCESS) {
			return false;
		}

		SimlarNumber.setDefaultRegion(region);
		createPasswordHash();

		return true;
	}

	public static void saveToFilePreferences(final Context context)
	{
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFERENCES_USER, mMySimlarId);
		editor.putString(PREFERENCES_PASSWORD, mPassword);
		editor.putInt(PREFERENCES_REGION, SimlarNumber.getDefaultRegion());
		editor.putLong(PREFERENCES_CREATE_ACCOUNT_REQUEST_TIMESTAMP, mCreateAccountRequestTimestamp.getTime());
		editor.apply();
	}

	public static void saveToFileCreateAccountStatus(final Context context, final CreateAccountStatus status)
	{
		saveToFileCreateAccountStatus(context, status, null);
	}

	public static void saveToFileCreateAccountStatus(final Context context, final CreateAccountStatus status, final String verifiedTelephoneNumber)
	{
		mCreateAccountStatus = status;
		mVerifiedTelephoneNumber = verifiedTelephoneNumber;
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putInt(PREFERENCES_CREATE_ACCOUNT_STATUS, status.toInt());
		editor.putString(PREFERENCES_VERIFIED_TELEPHONE_NUMBER, verifiedTelephoneNumber);
		editor.apply();
	}

	@SuppressWarnings("unused") // is only used in flavour push
	public static void saveToFileGcmRegistrationId(final Context context, final String gcmRegistrationId, final int simlarVersionCode)
	{
		mGcmRegistrationId = gcmRegistrationId;
		mSimlarVersionCode = simlarVersionCode;
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFERENCES_GCM_REGISTRATION_ID, gcmRegistrationId);
		editor.putInt(PREFERENCES_SIMLAR_VERSION_CODE, simlarVersionCode);
		editor.apply();
	}

	public static boolean readFromFileDebugMode(final Context context)
	{
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		return settings.getBoolean(PREFERENCES_DEBUG_MODE, PREFERENCES_DEBUG_MODE_DEFAULT);
	}

	public static void saveToFileDebugMode(final Context context, final boolean enabled)
	{
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(PREFERENCES_DEBUG_MODE, enabled);
		editor.apply();
	}

	public static void resetPreferencesFile(final Context context)
	{
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFERENCES_USER, null);
		editor.putString(PREFERENCES_PASSWORD, null);
		editor.putInt(PREFERENCES_REGION, -1);
		editor.putInt(PREFERENCES_CREATE_ACCOUNT_STATUS, -1);
		editor.putString(PREFERENCES_GCM_REGISTRATION_ID, null);
		editor.putInt(PREFERENCES_SIMLAR_VERSION_CODE, -1);
		editor.putBoolean(PREFERENCES_DEBUG_MODE, PREFERENCES_DEBUG_MODE_DEFAULT);
		editor.apply();
	}

	public static int getNextMissedCallNotificationId(final Context context)
	{
		final int nextId = mMissedCallNotificationId++;
		if (mMissedCallNotificationId > MISSED_CALL_NOTIFICATION_ID_MAX) {
			mMissedCallNotificationId = MISSED_CALL_NOTIFICATION_ID_MIN;
		}
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putInt(PREFERENCES_MISSED_CALL_NOTIFICATION_ID, mMissedCallNotificationId);
		editor.apply();
		return nextId;
	}
}

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

import java.security.MessageDigest;

import android.content.Context;
import android.content.SharedPreferences;

public final class PreferencesHelper
{
	private static final String PREFERENCES_FILE = "settings";
	private static final String PREFERENCES_USER = "user";
	private static final String PREFERENCES_PASSWORD = "password";
	private static final String PREFERENCES_REGION = "region";
	private static final String PREFERENCES_CREATE_ACCOUNT_STATUS = "create_account_status";
	private static final String PREFERENCES_GCM_REGRISTRATION_ID = "gcm_registration_id";
	private static final String PREFERENCES_SIMLAR_VERSION_CODE = "simlar_version_code";
	private static final String PREFERENCES_DEBUG_MODE = "debug_mode";
	private static final boolean PREFERENCES_DEBUG_MODE_DEFAULT = Version.hasDebugTag();
	private static final String PREFERENCES_VERIFIED_TELEPHONE_NUMBER = "verified_telephone_number";

	private static String mMySimlarId = null;
	private static String mPassword = null;
	private static String mPasswordHash = null;
	private static CreateAccountStatus mCreateAccountStatus = CreateAccountStatus.NONE;
	private static String mGcmRegistrationId = null;
	private static int mSimlarVersionCode = -1;
	private static String mVerifiedTelephoneNumber = null;

	private PreferencesHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static void init(final String mySimlarId, final String password)
	{
		mMySimlarId = mySimlarId;
		mPassword = password;

		createPasswordHash();
	}

	private static void createPasswordHash()
	{
		// kamailio password hash md5(username:realm:password)
		mPasswordHash = md5(mMySimlarId + ":sip.simlar.org:" + mPassword);
	}

	private static String md5(final String str)
	{
		try {
			final MessageDigest digest = MessageDigest.getInstance("md5");
			digest.update(str.getBytes());
			final StringBuilder sb = new StringBuilder();
			for (final byte b : digest.digest()) {
				sb.append(String.format("%02x", new Byte(b)));
			}
			return sb.toString();
		} catch (final Exception e) {
			return "";
		}
	}

	public static final class NotInitedException extends Exception
	{
		private static final long serialVersionUID = -1402138076439560953L;
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
		if (Util.isNullOrEmpty(mPasswordHash)) {
			throw new NotInitedException();
		}
		return mPassword;
	}

	public static CreateAccountStatus getCreateAccountStatus()
	{
		return mCreateAccountStatus;
	}

	public static String getVerifiedTelephoneNumber()
	{
		return mVerifiedTelephoneNumber;
	}

	public static String getGcmRegistrationId()
	{
		return mGcmRegistrationId;
	}

	public static int getSimlarVersionCode()
	{
		return mSimlarVersionCode;
	}

	public static boolean readPrefencesFromFile(final Context context)
	{
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);

		mMySimlarId = settings.getString(PREFERENCES_USER, null);
		mPassword = settings.getString(PREFERENCES_PASSWORD, null);
		final int region = settings.getInt(PREFERENCES_REGION, -1);
		mCreateAccountStatus = CreateAccountStatus.fromInt(settings.getInt(PREFERENCES_CREATE_ACCOUNT_STATUS, 0));
		mGcmRegistrationId = settings.getString(PREFERENCES_GCM_REGRISTRATION_ID, null);
		mSimlarVersionCode = settings.getInt(PREFERENCES_SIMLAR_VERSION_CODE, -1);
		mVerifiedTelephoneNumber = settings.getString(PREFERENCES_VERIFIED_TELEPHONE_NUMBER, null);

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
		editor.commit();
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
		editor.commit();
	}

	public static void saveToFileGcmRegistrationId(final Context context, final String gcmRegistrationId, final int simlarVersionCode)
	{
		mGcmRegistrationId = gcmRegistrationId;
		mSimlarVersionCode = simlarVersionCode;
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFERENCES_GCM_REGRISTRATION_ID, gcmRegistrationId);
		editor.putInt(PREFERENCES_SIMLAR_VERSION_CODE, simlarVersionCode);
		editor.commit();
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
		editor.commit();
	}

	public static void resetPreferencesFile(final Context context)
	{
		final SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		final SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFERENCES_USER, null);
		editor.putString(PREFERENCES_PASSWORD, null);
		editor.putInt(PREFERENCES_REGION, -1);
		editor.putInt(PREFERENCES_CREATE_ACCOUNT_STATUS, -1);
		editor.putString(PREFERENCES_GCM_REGRISTRATION_ID, null);
		editor.putInt(PREFERENCES_SIMLAR_VERSION_CODE, -1);
		editor.putBoolean(PREFERENCES_DEBUG_MODE, PREFERENCES_DEBUG_MODE_DEFAULT);
		editor.commit();
	}
}

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

	private static String mMySimlarId = null;
	private static String mPassword = null;
	private static String mPasswordHash = null;
	private static CreateAccountStatus mCreateAccountStatus = CreateAccountStatus.NONE;

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

	public static String md5(final String str)
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

	public static boolean readPrefencesFromFile(final Context context)
	{
		SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);

		mMySimlarId = settings.getString(PREFERENCES_USER, null);
		mPassword = settings.getString(PREFERENCES_PASSWORD, null);
		final int region = settings.getInt(PREFERENCES_REGION, -1);
		mCreateAccountStatus = CreateAccountStatus.fromInt(settings.getInt(PREFERENCES_CREATE_ACCOUNT_STATUS, 0));

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
		SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFERENCES_USER, mMySimlarId);
		editor.putString(PREFERENCES_PASSWORD, mPassword);
		editor.putInt(PREFERENCES_REGION, SimlarNumber.getDefaultRegion());
		editor.commit();
	}

	public static void saveToFileCreateAccountStatus(final Context context, final CreateAccountStatus status)
	{
		mCreateAccountStatus = status;
		SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt(PREFERENCES_CREATE_ACCOUNT_STATUS, status.toInt());
		editor.commit();
	}

	public static void resetPreferencesFile(final Context context)
	{
		SharedPreferences settings = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREFERENCES_USER, null);
		editor.putString(PREFERENCES_PASSWORD, null);
		editor.putInt(PREFERENCES_REGION, -1);
		editor.putInt(PREFERENCES_CREATE_ACCOUNT_STATUS, -1);
		editor.commit();
	}
}

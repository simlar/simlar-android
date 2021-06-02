/*
 * Copyright (C) 2013 - 2015 The Simlar Authors.
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
 *
 */

package org.simlar.helper;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public final class PermissionsHelper
{
	private static final int REQUEST_CODE_DEFAULT = 23;

	private PermissionsHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public enum Type
	{
		CAMERA(Manifest.permission.CAMERA, false, R.string.permission_explain_text_camera),
		CONTACTS(Manifest.permission.READ_CONTACTS, false, R.string.permission_explain_text_contacts),
		MICROPHONE(Manifest.permission.RECORD_AUDIO, true, R.string.permission_explain_text_record_audio),
		PHONE_NUMBERS(
				hasPhoneNumbersPermission() ? Manifest.permission.READ_PHONE_NUMBERS : Manifest.permission.READ_PHONE_STATE,
				false,
				hasPhoneNumbersPermission() ? R.string.permission_explain_text_phone_number : R.string.permission_explain_text_phone_state_with_phone_number),
		PHONE_STATE(
				Manifest.permission.READ_PHONE_STATE,
				true,
				hasPhoneNumbersPermission() ? R.string.permission_explain_text_phone_state : R.string.permission_explain_text_phone_state_with_phone_number),
		STORAGE(Manifest.permission.READ_EXTERNAL_STORAGE, false, R.string.permission_explain_text_storage);

		private final String mPermission;
		private final boolean mMajor;
		private final int mRationalMessageId;

		Type(final String permission, final boolean major, final int rationalMessageId)
		{
			mPermission = permission;
			mMajor = major;
			mRationalMessageId = rationalMessageId;
		}

		public String getPermission()
		{
			return mPermission;
		}

		public int getRationalMessageId()
		{
			return mRationalMessageId;
		}

		static Set<Type> getMajorPermissions(final boolean needsExternalStorage)
		{
			final Set<Type> majorTypes = EnumSet.noneOf(Type.class);
			for (final Type type : values()) {
				if (type.mMajor) {
					majorTypes.add(type);
				}
			}

			if (needsExternalStorage) {
				majorTypes.add(STORAGE);
			}

			return majorTypes;
		}

		private static boolean hasPhoneNumbersPermission()
		{
			return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean hasPermission(final Context context, final Type type)
	{
		return ContextCompat.checkSelfPermission(context, type.getPermission()) == PackageManager.PERMISSION_GRANTED;
	}

	public static boolean shouldShowRationale(final FragmentActivity activity, final Type type)
	{
		return ActivityCompat.shouldShowRequestPermissionRationale(activity, type.getPermission());
	}


	@FunctionalInterface
	public interface RequestPermissionsListener
	{
		void requestPermissions(final Set<String> types);
	}

	public static void showRationalForMissingMajorPermissions(final FragmentActivity activity, final boolean needsExternalStorage, final RequestPermissionsListener listener)
	{
		final Set<String> requestPermissions = new HashSet<>();
		final Set<String> rationalMessages = new HashSet<>();
		for (final Type type : Type.getMajorPermissions(needsExternalStorage)) {
			if (!hasPermission(activity, type)) {
				requestPermissions.add(type.getPermission());
				if (shouldShowRationale(activity, type)) {
					rationalMessages.add(activity.getString(type.getRationalMessageId()));
				}
			}
		}

		if (requestPermissions.isEmpty()) {
			// all permissions granted
			return;
		}

		if (rationalMessages.isEmpty()) {
			listener.requestPermissions(requestPermissions);
		} else {
			new AlertDialog.Builder(activity)
					.setMessage(TextUtils.join("\n\n", rationalMessages))
					.setOnDismissListener(dialog -> listener.requestPermissions(requestPermissions))
					.create().show();
		}
	}

	public static void requestContactPermission(final Fragment fragment)
	{
		Lg.i("requesting contact permission");
		fragment.requestPermissions(new String[] { Type.CONTACTS.getPermission() }, REQUEST_CODE_DEFAULT);
	}

	@SuppressWarnings("SameParameterValue")
	public static boolean isGranted(final Type type, @NonNull final String[] permissions, @NonNull final int[] grantResults)
	{
		if (permissions.length != 1) {
			Lg.w("expected exactly one permission but got: ", TextUtils.join(", ", permissions));
		}

		final String permission = permissions[0];
		if (!Util.equalString(permission, type.getPermission())) {
			Lg.w("expected permission: ", type.getPermission(), " but got: ", permission);
			return false;
		}

		if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			Lg.i("permission granted: ", permission);
			return true;
		} else {
			Lg.i("permission denied: ", permission);
			return false;
		}
	}

	public static boolean needsExternalStoragePermission(final Context context, final Uri uri)
	{
		if (context == null) {
			return false;
		}

		if (uri == null) {
			return false;
		}

		final String path = uri.getPath();
		if (Util.isNullOrEmpty(path)) {
			return false;
		}

		//noinspection OverlyBroadCatchBlock
		try {
			final FileInputStream stream = new FileInputStream(path);
			stream.close();
			return false;
		} catch (final IOException e) {
			return true;
		}
	}

	public static boolean isNotificationPolicyAccessGranted(final Context context)
	{
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
				((NotificationManager) Util.getSystemService(context, Context.NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted();
	}

	public static void checkAndRequestNotificationPolicyAccess(final FragmentActivity activity)
	{
		if (isNotificationPolicyAccessGranted(activity)) {
			return;
		}

		new AlertDialog.Builder(activity)
				.setMessage(activity.getString(R.string.permission_explain_text_notification_policy_access))
				.setPositiveButton(R.string.permission_explain_text_notification_policy_access_button, (dialogInterface, i) -> openNotificationPolicyAccessSettings(activity))
				.create().show();
	}

	public static void openNotificationPolicyAccessSettings(final Context context)
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			context.startActivity(
					new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
							.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
		}
	}

	public static void openAppSettings(final Context context)
	{
		context.startActivity(
				new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.getPackageName(), null))
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
	}
}

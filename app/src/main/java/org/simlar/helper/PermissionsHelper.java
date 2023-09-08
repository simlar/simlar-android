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

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.simlar.R;
import org.simlar.utils.Util;

public final class PermissionsHelper
{
	private PermissionsHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public enum Type
	{
		CAMERA(Manifest.permission.CAMERA, false),
		CONTACTS(Manifest.permission.READ_CONTACTS, false),
		MICROPHONE(Manifest.permission.RECORD_AUDIO, true),
		PHONE_NUMBERS(
				hasPhoneNumbersPermission()
						? Manifest.permission.READ_PHONE_NUMBERS
						: Manifest.permission.READ_PHONE_STATE,
				false),
		PHONE_STATE(Manifest.permission.READ_PHONE_STATE, true);

		private final String mPermission;
		private final boolean mMajor;

		Type(final String permission, final boolean major)
		{
			mPermission = permission;
			mMajor = major;
		}

		public String getPermission()
		{
			return mPermission;
		}

		public int getRationalMessageId()
		{
			return switch (this) {
				case CAMERA -> R.string.permission_explain_text_camera;
				case CONTACTS -> R.string.permission_explain_text_contacts;
				case MICROPHONE -> R.string.permission_explain_text_record_audio;
				case PHONE_NUMBERS -> hasPhoneNumbersPermission()
						? R.string.permission_explain_text_phone_number
						: R.string.permission_explain_text_phone_state_with_phone_number;
				case PHONE_STATE -> hasPhoneNumbersPermission()
						? R.string.permission_explain_text_phone_state
						: R.string.permission_explain_text_phone_state_with_phone_number;
			};
		}

		static Set<Type> getMajorPermissions()
		{
			final Set<Type> majorTypes = EnumSet.noneOf(Type.class);
			for (final Type type : values()) {
				if (type.mMajor) {
					majorTypes.add(type);
				}
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
	public interface RequestPermissionListener
	{
		@SuppressWarnings("unused")
		void requestPermission(final String permission);
	}

	public static void showRationalIfNeeded(final FragmentActivity activity, final Type type, final RequestPermissionListener listener)
	{
		if (shouldShowRationale(activity, type)) {
			new AlertDialog.Builder(activity)
					.setMessage(PermissionsHelper.Type.PHONE_NUMBERS.getRationalMessageId())
					.setOnDismissListener(dialog -> listener.requestPermission(type.getPermission()))
					.create().show();
		} else {
			listener.requestPermission(type.getPermission());
		}
	}

	@FunctionalInterface
	public interface RequestPermissionsListener
	{
		void requestPermissions(final Set<String> types);
	}

	public static void showRationalForMissingMajorPermissions(final FragmentActivity activity, final RequestPermissionsListener listener)
	{
		final Set<String> requestPermissions = new HashSet<>();
		final Set<String> rationalMessages = new HashSet<>();
		for (final Type type : Type.getMajorPermissions()) {
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

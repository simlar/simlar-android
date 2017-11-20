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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.AsyncTask;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.simlar.R;
import org.simlar.https.StorePushId;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.io.IOException;

@SuppressWarnings("deprecation")
public final class GooglePlayServicesHelper
{
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
	private static final String GOOGLE_PUSH_SENDER_ID = "772399062899";

	private GooglePlayServicesHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static void registerGcmIfNeeded(final Context context)
	{
		// Why do we check the version code here?
		// See: http://developer.android.com/google/gcm/adv.html
		//        Keeping the Registration State in Sync

		final int versionCode = Version.getVersionCode(context);
		if (versionCode < 1) {
			Lg.e("unable to read simlar version code");
			return;
		}

		if (PreferencesHelper.getSimlarVersionCode() > 0
				&& PreferencesHelper.getSimlarVersionCode() == versionCode
				&& !Util.isNullOrEmpty(PreferencesHelper.getGcmRegistrationId()))
		{
			Lg.i("already registered for google push notifications");
			return;
		}

		registerGcm(context, versionCode);
	}

	@SuppressLint("StaticFieldLeak")
	private static void registerGcm(final Context context, final int simlarVersionCode)
	{
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(final Void... params)
			{
				try {
					final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
					@SuppressWarnings("ResourceType") /// included in AndroidManifest for push
					final String gcmRegistrationId = gcm.register(GOOGLE_PUSH_SENDER_ID);

					if (Util.isNullOrEmpty(gcmRegistrationId)) {
						Lg.e("got empty gcm registration id from google server");
						return "";
					}

					if (!StorePushId.httpPostStorePushId(gcmRegistrationId)) {
						Lg.e("ERROR: failed to store gcm push notification registration id=", gcmRegistrationId, " on simlar server");
						return "";
					}

					Lg.i("gcm push notification registration id=", gcmRegistrationId, " stored on simlar server");
					return gcmRegistrationId;
				} catch (final IOException e) {
					Lg.ex(e, "gcm registration IOException");
					return "";
				}
			}

			@Override
			protected void onPostExecute(final String gcmRegistrationId)
			{
				if (!Util.isNullOrEmpty(gcmRegistrationId)) {
					PreferencesHelper.saveToFileGcmRegistrationId(context, gcmRegistrationId, simlarVersionCode);
					Lg.i("gcm push notification registration id=", gcmRegistrationId, " cached on device");
				}
			}
		}.execute();
	}

	private static void showDialogAndFinishParent(final Activity activity, final Dialog dialog)
	{
		dialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(final DialogInterface dialogInterface)
			{
				dialog.dismiss();
				activity.finish();
			}
		});
		dialog.show();
	}

	public static boolean checkPlayServices(final Activity activity)
	{
		final int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
		if (resultCode == ConnectionResult.SUCCESS) {
			Lg.i("google play services check ok");
			return true;
		}

		Lg.w("google play services not available: ", GooglePlayServicesUtil.getErrorString(resultCode));

		if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
			Lg.w("This device has no or too old google play services installed. Asking user");
			showDialogAndFinishParent(activity, GooglePlayServicesUtil.getErrorDialog(resultCode, activity, PLAY_SERVICES_RESOLUTION_REQUEST));
		} else {
			Lg.e("This device is not supported.");
			showDialogAndFinishParent(activity,
					new AlertDialog.Builder(activity)
							.setTitle(R.string.google_play_services_helper_alert_unavailable_title)
							.setMessage(R.string.google_play_services_helper_alert_unavailable_text)
							.setNeutralButton(R.string.google_play_services_helper_alert_unavailable_button_close_simlar, null)
							.create());
		}

		return false;
	}
}

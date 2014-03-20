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

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public final class GooglePlayServicesHelper
{
	static final String LOGTAG = GooglePlayServicesHelper.class.getSimpleName();
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
	static final String GOOGLE_PUSH_SENDER_ID = "772399062899";

	public static void registerGCM(final Context context)
	{
		AsyncTask.execute(new Runnable() {
			@Override
			public void run()
			{
				try {
					final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
					final String regid = gcm.register(GOOGLE_PUSH_SENDER_ID);
					if (StorePushId.httpPostStorePushId(regid)) {
						Log.i(LOGTAG, "GCM push notification registration id=" + regid + " stored on simlar server");
					} else {
						Log.e(LOGTAG, "ERROR: failed to store GCM push notification registration id=" + regid + " on simlar server");
					}
				} catch (final IOException e) {
					Log.w(LOGTAG, "GCM Registration IOException", e);
				}
			}
		});
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
			Log.i(LOGTAG, "google play services check ok");
			return true;
		}

		if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
			Log.w(LOGTAG, "This device has no or too old google-play-services installed. Asking user");
			showDialogAndFinishParent(activity, GooglePlayServicesUtil.getErrorDialog(resultCode, activity, PLAY_SERVICES_RESOLUTION_REQUEST));
		} else {
			Log.e(LOGTAG, "This device is not supported.");
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

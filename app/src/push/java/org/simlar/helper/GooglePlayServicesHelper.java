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

import android.app.Dialog;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.service.PushNotificationService;

public final class GooglePlayServicesHelper
{
	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	private GooglePlayServicesHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static void refreshTokenOnServer(final Context context)
	{
		PushNotificationService.refreshTokenOnServer(context);
	}

	public static void checkPlayServices(final AppCompatActivity activity)
	{
		final GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
		final int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(activity);
		if (resultCode == ConnectionResult.SUCCESS) {
			Lg.i("google play services check ok");
			return;
		}

		if (googleApiAvailability.isUserResolvableError(resultCode)) {
			Lg.i("google play services resolvable error: ", googleApiAvailability.getErrorString(resultCode));

			if (resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED && PreferencesHelper.getGcmClientVersion() == googleApiAvailability.getClientVersion(activity)) {
				Lg.i("user already asked to update play services");
			} else {
				Lg.w("this device has too old google play services installed => asking user");
				final Dialog dialog = googleApiAvailability.getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
				if (dialog != null) {
					dialog.show();
				} else {
					new AlertDialog.Builder(activity)
							.setTitle(R.string.google_play_services_helper_alert_too_old_title)
							.setMessage(R.string.google_play_services_helper_alert_too_old_text)
							.show();
				}
				PreferencesHelper.saveToFileGcmClientVersion(activity, googleApiAvailability.getClientVersion(activity));
			}
		} else {
			Lg.e("this device is not supported: ", googleApiAvailability.getErrorString(resultCode));

			final Dialog dialog = new AlertDialog.Builder(activity)
					.setTitle(R.string.google_play_services_helper_alert_unavailable_title)
					.setMessage(R.string.google_play_services_helper_alert_unavailable_text)
					.setNeutralButton(R.string.google_play_services_helper_alert_unavailable_button_close_simlar, null)
					.create();

			dialog.setOnDismissListener(dialogInterface -> {
				dialog.dismiss();
				activity.finish();
			});
			dialog.show();
		}
	}
}

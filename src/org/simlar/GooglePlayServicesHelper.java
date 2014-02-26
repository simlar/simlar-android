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

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public final class GooglePlayServicesHelper
{
	static final String LOGTAG = GooglePlayServicesHelper.class.getSimpleName();
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
}

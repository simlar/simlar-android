/**
 * Copyright (C) 2013 - 2014 The Simlar Authors.
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

package org.simlar;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public final class GcmBroadcastReceiver extends WakefulBroadcastReceiver
{
	private static final String COLLAPSE_KEY = "collapse_key";
	private static final String COLLAPSE_KEY_CALL = "call";

	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		Lg.init(context);

		final Bundle extras = intent.getExtras();
		if (extras.isEmpty()) {
			Lg.e("received Google Cloud Messaging Event with empty extras");
			return;
		}

		final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
		if (gcm == null) {
			Lg.e("unable to instantiate Google Cloud Messaging");
			return;
		}

		final String messageType = gcm.getMessageType(intent);

		switch (messageType) {
		case GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE:
			if (COLLAPSE_KEY_CALL.equalsIgnoreCase(extras.getString(COLLAPSE_KEY))) {
				Lg.i("received call push notification");
			} else {
				Lg.w("received unknown push notification: ", extras);
			}
			intent.putExtra(SimlarService.INTENT_EXTRA_GCM, SimlarService.INTENT_EXTRA_GCM);
			startWakefulService(context, intent.setComponent(new ComponentName(context.getPackageName(), SimlarService.class.getName())));
			setResultCode(Activity.RESULT_OK);
			break;
		case GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR:
			Lg.e("send error: ", extras);
			break;
		case GoogleCloudMessaging.MESSAGE_TYPE_DELETED:
			Lg.e("deleted messages on server: ", extras);
			break;
		default:
			Lg.e("received Google Cloud Messaging Event with unknown message type: ", messageType);
			break;
		}
	}
}

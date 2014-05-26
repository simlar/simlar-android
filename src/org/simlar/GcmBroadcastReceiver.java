/**
 * Copyright (C) 2013 - 2014 The Simlar Authors.
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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public final class GcmBroadcastReceiver extends WakefulBroadcastReceiver
{
	static final String LOGTAG = GcmBroadcastReceiver.class.getSimpleName();

	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		final Bundle extras = intent.getExtras();
		if (extras.isEmpty()) {
			Log.e(LOGTAG, "received Google Cloud Messaging Event with empty extras");
			return;
		}

		final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
		if (gcm == null) {
			Log.e(LOGTAG, "unable to instantiate Google Cloud Messaging");
			return;
		}

		final String messageType = gcm.getMessageType(intent);

		if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
			Log.i(LOGTAG, "received: " + extras.toString());
			startWakefulService(context, intent.setComponent(new ComponentName(context.getPackageName(), SimlarService.class.getName())));
			setResultCode(Activity.RESULT_OK);
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
			Log.e(LOGTAG, "send error: " + extras.toString());
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
			Log.e(LOGTAG, "deleted messages on server: " + extras.toString());
		} else {
			Log.e(LOGTAG, "received Google Cloud Messaging Event with unknown message type: " + messageType);
		}
	}
}

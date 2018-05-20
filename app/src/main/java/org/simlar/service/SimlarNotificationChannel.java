/*
 * Copyright (C) 2017 The Simlar Authors.
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

package org.simlar.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.v4.app.NotificationManagerCompat;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public enum SimlarNotificationChannel
{
	CALL(NotificationManagerCompat.IMPORTANCE_LOW, R.string.notification_channel_call_name),
	MISSED_CALL(NotificationManagerCompat.IMPORTANCE_DEFAULT, R.string.missed_call_notification);

	private final int importance;
	private final int title;

	SimlarNotificationChannel(final int importance, final int title)
	{
		this.importance = importance;
		this.title = title;
	}

	public static void createNotificationChannels(final Context context)
	{
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			Lg.i("notification channels not supported");
			return;
		}

		Lg.i("creating notification channels");
		final NotificationManager notificationManager = Util.getSystemService(context, Context.NOTIFICATION_SERVICE);
		for (final SimlarNotificationChannel value: values()) {
			final NotificationChannel channel = new NotificationChannel(value.name(), context.getString(value.title), value.importance);
			notificationManager.createNotificationChannel(channel);
		}
	}
}

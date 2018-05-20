/*
 * Copyright (C) 2013 - 2018 The Simlar Authors.
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

import android.content.Intent;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.simlar.logging.Lg;

public final class FirebaseMessageReceiver extends FirebaseMessagingService
{
	@Override
	public void onMessageReceived(final RemoteMessage remoteMessage)
	{
		Lg.i("push notification received from: ", remoteMessage.getFrom(),
				" messageId: ", remoteMessage.getMessageId(),
				" type: ", remoteMessage.getMessageType(),
				" collapseKey: ", remoteMessage.getCollapseKey(),
				" data: ", remoteMessage.getData(),
				" notification: ", remoteMessage.getNotification() == null ? null : remoteMessage.getNotification().getBody());

		final Intent simlarService = new Intent(this, SimlarService.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(simlarService);
		} else {
			startService(simlarService);
		}
	}
}

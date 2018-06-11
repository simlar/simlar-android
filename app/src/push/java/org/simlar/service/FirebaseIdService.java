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

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.simlar.https.StorePushId;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public final class FirebaseIdService extends FirebaseInstanceIdService
{
	private static volatile boolean tokenRefreshed = false;

	@Override
	public void onTokenRefresh()
	{
		final String token = FirebaseInstanceId.getInstance().getToken();
		Lg.i("onTokenRefresh: ", token);
		sendToServer(token);
	}

	private static void sendToServer(final String token)
	{
		if (Util.isNullOrEmpty(token)) {
			Lg.e("empty token");
			return;
		}

		new Thread(() -> {
			if (!StorePushId.httpPostStorePushId(token)) {
				Lg.e("failed to store push notification token=", token, " on simlar server");
				return;
			}

			Lg.i("push notification token=", token, " stored on simlar server");
		}).start();
	}

	public static void refreshTokenOnServer()
	{
		if (tokenRefreshed) {
			return;
		}
		tokenRefreshed = true;

		Lg.i("trigger update of firebase push notification token on simlar server");
		sendToServer(FirebaseInstanceId.getInstance().getToken());
	}
}

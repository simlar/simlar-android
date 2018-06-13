/**
 * Copyright (C) 2015 The Simlar Authors.
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


package org.simlar.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;

import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public class BootCompletedReceiver extends BroadcastReceiver
{
	@Override
	public final void onReceive(final Context context, final Intent intent)
	{
		Lg.i("onReceive");

		if (context == null) {
			Lg.e("context is null");
			return;
		}

		if (intent == null) {
			Lg.e("intent is null");
			return;
		}

		if (!Util.equalString(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
			Lg.e("unexpected intent action: ", intent.getAction());
			return;
		}

		ContextCompat.startForegroundService(context, intent.setComponent(new ComponentName(context, SimlarService.class)));
	}
}

/*
 * Copyright (C) 2013 - 2016 The Simlar Authors.
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

package org.simlar.helper;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import org.simlar.logging.Lg;

public final class RingtoneHelper
{
	private RingtoneHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	private static Uri getActualDefaultRingtoneUri(final Context context)
	{
		// in case we do have permissions to read the ringtone
		try {
			return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE);
		} catch (final SecurityException e) {
			Lg.w("getActualDefaultRingtoneUri failed: ", e);
			return null;
		}
	}

	public static Uri getRingtoneUri(final Context context, final Uri fallbackUri)
	{
		final Uri uri = getActualDefaultRingtoneUri(context);
		if (uri != null) {
			Lg.i("using Uri for ringtone: ", uri);
			return uri;
		}

		Lg.i("using fallback Uri for ringtone: ", fallbackUri);
		return fallbackUri;
	}
}

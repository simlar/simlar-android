/*
 * Copyright (C) 2013 - 2015 The Simlar Authors.
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.telephony.TelephonyManager;

import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.util.Locale;

public final class SimCardReader
{
	private SimCardReader()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static String readRegionCode(final Context context)
	{
		// try to read country code from sim
		final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		final String regionFromSim = tm.getSimCountryIso().toUpperCase(Locale.US);
		if (!Util.isNullOrEmpty(regionFromSim)) {
			return regionFromSim;
		}

		// read countryCode from configuration
		final String regionFromConfig = Locale.getDefault().getCountry().toUpperCase(Locale.US);
		Lg.i("guessed region by android configuration: ", regionFromConfig);
		return regionFromConfig;
	}

	public static String readPhoneNumber(final Context context)
	{
		@SuppressLint("HardwareIds") // Yes, sometimes "getLine1Number" even returns the wrong number but it helps most of the users.
		final String numberFromSim = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
		if (Util.isNullOrEmpty(numberFromSim)) {
			Lg.w("failed to read telephone number from sim card");
		}

		return numberFromSim;
	}
}

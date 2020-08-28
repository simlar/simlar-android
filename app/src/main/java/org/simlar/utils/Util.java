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

package org.simlar.utils;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Util
{
	public static final String[] EMPTY_STRING_ARRAY = {};
	private static final int MAX_BUFFER_SIZE = 1024 * 1024;

	private Util()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static boolean isNullOrEmpty(final String string)
	{
		return string == null || string.isEmpty();
	}

	public static int compareString(final String lhs, final String rhs)
	{
		if (isNullOrEmpty(lhs) && isNullOrEmpty(rhs)) {
			return 0;
		}

		if (isNullOrEmpty(lhs)) {
			return -1;
		}

		if (isNullOrEmpty(rhs)) {
			return 1;
		}

		return lhs.compareToIgnoreCase(rhs);
	}

	public static boolean equalString(final String lhs, final String rhs)
	{
		return compareString(lhs, rhs) == 0;
	}

	public static boolean equals(final Object lhs, final Object rhs)
	{
		//noinspection EqualsReplaceableByObjectsCall,ObjectEquality /// Objects.equals is available in android sdk >= 19
		return lhs == rhs || lhs != null && lhs.equals(rhs);
	}

	public static void copyStream(final InputStream is, final OutputStream os) throws IOException
	{
		final byte[] buffer = new byte[MAX_BUFFER_SIZE];
		int length;
		while ((length = is.read(buffer)) != -1) {
			os.write(buffer, 0, length);
		}
	}

	public static Spanned fromHtml(final String string)
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? Html.fromHtml(string, Html.FROM_HTML_MODE_LEGACY) : Html.fromHtml(string);
	}

	public static String formatMilliSeconds(final long milliSeconds)
	{
		if (milliSeconds >= 0) {
			return formatPositiveMilliSeconds(milliSeconds);
		}

		return '-' + formatPositiveMilliSeconds(-1 * milliSeconds);
	}

	private static String formatPositiveMilliSeconds(final long milliSeconds)
	{
		final SimpleDateFormat sdf = createSimpleDateFormat(milliSeconds);
		sdf.setTimeZone(TimeZone.getTimeZone("GMT+0"));
		return sdf.format(new Date(milliSeconds));
	}

	private static SimpleDateFormat createSimpleDateFormat(final long milliSeconds)
	{
		if (milliSeconds >= 3600000) {
			return new SimpleDateFormat("HH:mm:ss", Locale.US);
		}

		return new SimpleDateFormat("mm:ss", Locale.US);
	}

	@NonNull
	public static <T> T getSystemService(final Context context, final String name)
	{
		if (context == null) {
			throw new IllegalArgumentException("no context");
		}

		@SuppressWarnings("unchecked")
		final T service = (T) context.getSystemService(name);
		if (service == null) {
			throw new IllegalArgumentException("no system service matching name: " + name);
		}
		return service;
	}

	public static <T> T defaultIfNull(final T value, final T defaultValue)
	{
		return value == null ? defaultValue : value;
	}
}

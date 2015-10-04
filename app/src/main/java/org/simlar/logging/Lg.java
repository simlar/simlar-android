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

package org.simlar.logging;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import org.simlar.utils.Util;

public final class Lg
{
	private static final int FILENAME_SIZE_MAX = 34;
	private static final int LOG_LEVEL_NORMAL = Log.WARN;
	private static final int LOG_LEVEL_DEBUG = Log.DEBUG;
	private static volatile int mLevel = LOG_LEVEL_NORMAL;
	private static volatile String mPackageName;

	private Lg()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	@SuppressLint("LogConditional")
	private static void println(final int priority, final Throwable exception, final Object... messageParts)
	{
		if (priority < mLevel) {
			return;
		}

		final StringBuilder message = new StringBuilder();
		if (messageParts != null) {
			for (final Object part : messageParts) {
				message.append(part);
			}
		}

		if (exception != null) {
			message.append('\n')
					.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append('\n')
					.append(Log.getStackTraceString(exception));
		}

		Log.println(priority, createTag(), message.toString());
	}

	private static String createTag()
	{
		final StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[5];
		final String fileName = stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber();

		final StringBuilder tag = new StringBuilder();
		tag.append(mPackageName).append(".(");

		final int n = FILENAME_SIZE_MAX - fileName.length();
		if (n > 0) {
			tag.append(fileName).append(")").append(String.format("%" + n + "s", "."));
		} else {
			tag.append(fileName.substring(-n)).append(")");
		}

		return tag.toString();
	}

	public static class Anonymizer
	{
		private final Object mMessagePart;

		public Anonymizer(final Object messagePart)
		{
			mMessagePart = messagePart;
		}

		@Override
		public String toString()
		{
			if (mMessagePart == null) {
				return "";
			}

			return anonymize(mMessagePart.toString());
		}

		public static String anonymize(final String string)
		{
			if (Util.isNullOrEmpty(string)) {
				return "";
			}

			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < string.length(); ++i) {
				sb.append((i % 2 == 0) ? string.charAt(i) : '*');
			}
			return sb.toString();
		}
	}

	public static void init(final Context context, final boolean debugMode)
	{
		setDebugMode(debugMode);
		mPackageName = context.getPackageName();
	}

	public static void setDebugMode(final boolean enabled)
	{
		mLevel = enabled ? LOG_LEVEL_DEBUG : LOG_LEVEL_NORMAL;
	}

	public static boolean isDebugModeEnabled()
	{
		return mLevel < LOG_LEVEL_NORMAL;
	}

	public static void v(final Object... messageParts)
	{
		println(Log.VERBOSE, null, messageParts);
	}

	public static void d(final Object... messageParts)
	{
		println(Log.DEBUG, null, messageParts);
	}

	public static void i(final Object... messageParts)
	{
		println(Log.INFO, null, messageParts);
	}

	public static void w(final Object... messageParts)
	{
		println(Log.WARN, null, messageParts);
	}

	public static void e(final Object... messageParts)
	{
		println(Log.ERROR, null, messageParts);
	}

	public static void ex(final Throwable exception, final Object... messageParts)
	{
		println(Log.ERROR, exception, messageParts);
	}
}

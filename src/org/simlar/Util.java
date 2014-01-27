/**
 * Copyright (C) 2013 The Simlar Authors.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.ImageView;

public class Util
{
	private static final int MAX_BUFFER_SIZE = 1 * 1024 * 1024;

	public static boolean isNullOrEmpty(final String string)
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return string == null || string.isEmpty();
		}

		return string == null || string.length() == 0;
	}

	public static int compareString(final String lhs, final String rhs)
	{
		if (Util.isNullOrEmpty(lhs) && Util.isNullOrEmpty(rhs)) {
			return 0;
		}

		if (Util.isNullOrEmpty(lhs)) {
			return -1;
		}

		if (Util.isNullOrEmpty(rhs)) {
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
		if (lhs == rhs) {
			return true;
		}

		if (lhs != null) {
			return lhs.equals(rhs);
		}

		return rhs.equals(lhs);
	}

	public static void copyStream(InputStream is, OutputStream os) throws IOException
	{
		final byte[] buffer = new byte[MAX_BUFFER_SIZE];
		int length = 0;
		while ((length = is.read(buffer)) != -1) {
			os.write(buffer, 0, length);
		}
	}

	@SuppressWarnings("deprecation")
	public static void setBackgroundCompatible(final ImageView imageView, final Drawable drawable)
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			imageView.setBackground(drawable);
		} else {
			imageView.setBackgroundDrawable(drawable);
		}
	}
}

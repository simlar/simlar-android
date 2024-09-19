/*
 * Copyright (C) The Simlar Authors.
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

package org.simlar.widgets;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.simlar.R;

public final class UnmaintainedWarningDialog
{
	private UnmaintainedWarningDialog()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static void show(final Context context)
	{
		final AlertDialog alertDialog = new AlertDialog.Builder(context)
				.setTitle(R.string.unmaintained_warning_alert_title)
				.setMessage(R.string.unmaintained_warning_alert_message)
				.create();
		alertDialog.show();

		// open html links in browser
		final TextView textView = alertDialog.findViewById(android.R.id.message);
		if (textView != null) {
			textView.setMovementMethod(LinkMovementMethod.getInstance());
		}
	}
}

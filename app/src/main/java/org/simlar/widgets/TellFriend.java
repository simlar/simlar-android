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

package org.simlar.widgets;

import android.content.Context;
import android.content.Intent;

import org.simlar.R;

final class TellFriend
{
	private TellFriend()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static void sendMessage(final Context context)
	{
		final Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("text/plain");
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.main_activity_tell_a_friend_subject));
		sendIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.main_activity_tell_a_friend_text));
		context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.main_activity_tell_a_friend_chooser_title)));
	}
}

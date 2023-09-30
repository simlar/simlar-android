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

package org.simlar.helper;

import org.linphone.core.Reason;

import org.simlar.R;

public enum CallEndReason
{
	NONE,
	DECLINED,
	OFFLINE,
	UNSUPPORTED_MEDIA,
	BUSY,
	SERVER_CONNECTION_TIMEOUT;

	public static CallEndReason fromReason(final Reason reason)
	{
		if (reason == null) {
			return NONE;
		}

		return switch (reason) {
			case Declined -> DECLINED;
			case NotFound -> OFFLINE;
			case NotImplemented, NotAcceptable, UnsupportedContent -> UNSUPPORTED_MEDIA;
			case Busy -> BUSY;
			case Transferred, BadEvent, SessionIntervalTooSmall, None, NoResponse, Forbidden, NotAnswered, IOError, DoNotDisturb, Unauthorized, NoMatch, MovedPermanently, Gone, TemporarilyUnavailable, AddressIncomplete, BadGateway, ServerTimeout, Unknown ->
					NONE;
		};
	}

	public int getDisplayMessageId()
	{
		return switch (this) {
			case DECLINED -> R.string.call_activity_call_ended_because_declined;
			case OFFLINE -> R.string.call_activity_call_ended_because_user_offline;
			case UNSUPPORTED_MEDIA -> R.string.call_activity_call_ended_because_incompatible_media;
			case BUSY -> R.string.call_activity_call_ended_because_user_busy;
			case SERVER_CONNECTION_TIMEOUT -> R.string.call_activity_connecting_to_server_timed_out;
			case NONE -> R.string.call_activity_call_ended_normally;
		};
	}
}

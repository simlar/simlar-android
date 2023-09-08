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

package org.simlar.service;

import org.linphone.core.RegistrationState;

import org.simlar.R;

public enum SimlarStatus
{
	UNKNOWN,
	OFFLINE,
	CONNECTING,
	ONLINE,
	ONGOING_CALL,
	ERROR;

	public static SimlarStatus fromRegistrationState(final RegistrationState state)
	{
		return switch (state) {
			case None, Cleared -> OFFLINE;
			case Progress -> CONNECTING;
			case Refreshing, Ok -> ONLINE;
			case Failed -> ERROR;
			default -> UNKNOWN;
		};
	}

	public boolean isConnectedToSipServer()
	{
		return switch (this) {
			case ONLINE, ONGOING_CALL -> true;
			case OFFLINE, CONNECTING, ERROR, UNKNOWN -> false;
		};
	}

	public boolean isOffline()
	{
		return switch (this) {
			case ONLINE, ONGOING_CALL, CONNECTING -> false;
			case OFFLINE, ERROR, UNKNOWN -> true;
		};
	}

	public boolean isRegistrationAtSipServerFailed()
	{
		return switch (this) {
			case ERROR -> true;
			case ONLINE, ONGOING_CALL, OFFLINE, CONNECTING, UNKNOWN -> false;
		};
	}

	public int getNotificationIcon()
	{
		return switch (this) {
			case ONLINE, ONGOING_CALL -> R.drawable.ic_notification_ongoing_call;
			case UNKNOWN, OFFLINE, CONNECTING, ERROR -> R.drawable.ic_notification_offline;
		};
	}

	public int getNotificationTextId()
	{
		return switch (this) {
			case OFFLINE -> R.string.notification_simlar_status_offline;
			case CONNECTING -> R.string.notification_simlar_status_connecting;
			case ONLINE, ONGOING_CALL -> R.string.notification_simlar_status_online;
			case ERROR -> R.string.notification_simlar_status_error;
			case UNKNOWN -> R.string.notification_simlar_status_unknown;
		};
	}

}

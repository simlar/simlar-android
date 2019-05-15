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
		switch (state) {
		case None:
		case Cleared:
			return OFFLINE;
		case Progress:
			return CONNECTING;
		case Ok:
			return ONLINE;
		case Failed:
			return ERROR;
		default:
			return UNKNOWN;
		}
	}

	public boolean isConnectedToSipServer()
	{
		switch (this) {
		case ONLINE:
		case ONGOING_CALL:
			return true;
		case OFFLINE:
		case CONNECTING:
		case ERROR:
		case UNKNOWN:
		default:
			return false;
		}
	}

	public boolean isOffline()
	{
		switch (this) {
		case ONLINE:
		case ONGOING_CALL:
		case CONNECTING:
			return false;
		case OFFLINE:
		case ERROR:
		case UNKNOWN:
		default:
			return true;
		}
	}

	public boolean isRegistrationAtSipServerFailed()
	{
		switch (this) {
		case ERROR:
			return true;
		case ONLINE:
		case ONGOING_CALL:
		case OFFLINE:
		case CONNECTING:
		case UNKNOWN:
		default:
			return false;
		}
	}

	public int getNotificationIcon()
	{
		switch (this) {
		case ONLINE:
		case ONGOING_CALL:
			return R.drawable.ic_notification_ongoing_call;
		case UNKNOWN:
		case OFFLINE:
		case CONNECTING:
		case ERROR:
		default:
			return R.drawable.ic_notification_offline;
		}
	}

	public int getNotificationTextId()
	{
		switch (this) {
		case OFFLINE:
			return R.string.notification_simlar_status_offline;
		case CONNECTING:
			return R.string.notification_simlar_status_connecting;
		case ONLINE:
		case ONGOING_CALL:
			return R.string.notification_simlar_status_online;
		case ERROR:
			return R.string.notification_simlar_status_error;
		case UNKNOWN:
		default:
			return R.string.notification_simlar_status_unknown;
		}
	}

}

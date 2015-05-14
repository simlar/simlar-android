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

package org.simlar;

import org.linphone.core.LinphoneCore.RegistrationState;

public enum SimlarStatus
{
	UNKNOWN,
	OFFLINE,
	CONNECTING,
	ONLINE,
	ONGOING_CALL,
	ERROR;

	public static SimlarStatus fromRegistrationState(final org.linphone.core.LinphoneCore.RegistrationState state)
	{
		if (RegistrationState.RegistrationNone.equals(state) || RegistrationState.RegistrationCleared.equals(state)) {
			return OFFLINE;
		} else if (RegistrationState.RegistrationProgress.equals(state)) {
			return CONNECTING;
		} else if (RegistrationState.RegistrationOk.equals(state)) {
			return ONLINE;
		} else if (RegistrationState.RegistrationFailed.equals(state)) {
			return ERROR;
		} else {
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

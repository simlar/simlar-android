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

import org.linphone.core.LinphoneCore.RegistrationState;

public enum SimlarStatus {
	UNKNOWN,
	OFFLINE,
	CONNECTING,
	LOADING_CONTACTS,
	ONLINE,
	ONGOING_CALL,
	ERROR,
	ERROR_LOADING_CONTACTS;

	public static SimlarStatus fromRegistrationState(final org.linphone.core.LinphoneCore.RegistrationState state)
	{
		if (state == RegistrationState.RegistrationNone || state == RegistrationState.RegistrationCleared) {
			return OFFLINE;
		} else if (state == RegistrationState.RegistrationProgress) {
			return CONNECTING;
		} else if (state == RegistrationState.RegistrationOk) {
			return ONLINE;
		} else if (state == RegistrationState.RegistrationFailed) {
			return ERROR;
		} else {
			return UNKNOWN;
		}
	}

	public int getNotificationTextId()
	{
		switch (this) {
		case OFFLINE:
			return R.string.notification_simlar_status_offline;
		case CONNECTING:
			return R.string.notification_simlar_status_connecting;
		case LOADING_CONTACTS:
			return R.string.notification_simlar_status_connecting;
		case ONLINE:
			return R.string.notification_simlar_status_online;
		case ONGOING_CALL:
			return R.string.notification_simlar_status_ongoing_call;
		case ERROR:
			return R.string.notification_simlar_status_error;
		case ERROR_LOADING_CONTACTS:
			return R.string.notification_simlar_status_error_loading_contacts;
		case UNKNOWN:
		default:
			return R.string.notification_simlar_status_unknown;
		}
	}

	public int getNotificationIcon()
	{
		switch (this) {
		case ONLINE:
		case ONGOING_CALL:
		case ERROR_LOADING_CONTACTS:
			return R.drawable.status_online;
		case UNKNOWN:
		case OFFLINE:
		case CONNECTING:
		case LOADING_CONTACTS:
		case ERROR:
		default:
			return R.drawable.status_offline;
		}
	}

	public int getContactTextId(boolean isGoingDown)
	{
		if (isGoingDown) {
			return R.string.contacts_adapter_simlar_status_going_down;
		}

		switch (this) {
		case OFFLINE:
			return R.string.contacts_adapter_simlar_status_offline;
		case CONNECTING:
			return R.string.contacts_adapter_simlar_status_connecting;
		case LOADING_CONTACTS:
			return R.string.contacts_adapter_simlar_status_loading_contacts;
		case ONLINE:
		case ONGOING_CALL:
			return R.string.contacts_adapter_simlar_status_no_contacts_found;
		case ERROR:
			return R.string.contacts_adapter_simlar_status_error;
		case ERROR_LOADING_CONTACTS:
			return R.string.contacts_adapter_simlar_status_error_loading_contacts;
		case UNKNOWN:
		default:
			return R.string.contacts_adapter_simlar_status_error_unknown;
		}
	}

	public boolean isConnectedToSipServer()
	{
		switch (this) {
		case LOADING_CONTACTS:
		case ONLINE:
		case ONGOING_CALL:
		case ERROR_LOADING_CONTACTS:
			return true;
		case OFFLINE:
		case CONNECTING:
		case ERROR:
		case UNKNOWN:
		default:
			return false;
		}
	}

	public boolean isRegistrationAtSipServerFailed()
	{
		switch (this) {
		case ERROR:
			return true;
		case LOADING_CONTACTS:
		case ONLINE:
		case ONGOING_CALL:
		case ERROR_LOADING_CONTACTS:
		case OFFLINE:
		case CONNECTING:
		case UNKNOWN:
		default:
			return false;
		}
	}
}

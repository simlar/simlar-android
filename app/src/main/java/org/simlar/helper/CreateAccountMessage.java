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

package org.simlar.helper;

import org.simlar.R;

public enum CreateAccountMessage
{
	WRONG_TELEPHONE_NUMBER(true, false),
	SMS(true, true),
	SMS_NOT_GRANTED_OR_TIMEOUT(true, true),
	SMS_CALL_SUCCESS(true, true),
	NOT_POSSIBLE(false, false),
	REGISTRATION_CODE(false, true),
	TOO_MANY_CONFIRMS(false, false),
	TOO_MANY_CALLS(false, true),
	SIP_NOT_POSSIBLE(false, false);

	final boolean telephoneNumber;
	final boolean registrationCodeInputVisible;

	CreateAccountMessage(final boolean telephoneNumber, final boolean registrationCodeInputVisible)
	{
		this.telephoneNumber = telephoneNumber;
		this.registrationCodeInputVisible = registrationCodeInputVisible;
	}

	public int getResourceId()
	{
		return switch (this) {
			case WRONG_TELEPHONE_NUMBER -> R.string.create_account_activity_message_wrong_telephone_number;
			case SMS -> R.string.create_account_activity_message_sms;
			case SMS_NOT_GRANTED_OR_TIMEOUT -> R.string.create_account_activity_message_sms_not_granted_or_timeout;
			case SMS_CALL_SUCCESS -> R.string.create_account_activity_message_sms_call_success;
			case NOT_POSSIBLE -> R.string.create_account_activity_message_not_possible;
			case REGISTRATION_CODE -> R.string.create_account_activity_message_registration_code;
			case TOO_MANY_CONFIRMS -> R.string.create_account_activity_message_too_many_confirms;
			case TOO_MANY_CALLS -> R.string.create_account_activity_message_too_many_calls;
			case SIP_NOT_POSSIBLE -> R.string.create_account_activity_message_sip_not_possible;
		};
	}

	public boolean isTelephoneNumber()
	{
		return telephoneNumber;
	}

	public boolean isRegistrationCodeInputVisible()
	{
		return registrationCodeInputVisible;
	}
}

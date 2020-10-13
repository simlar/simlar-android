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
	WRONG_TELEPHONE_NUMBER    (R.string.create_account_activity_message_wrong_telephone_number, true, false),
	SMS                       (R.string.create_account_activity_message_sms, true, true),
	SMS_NOT_GRANTED_OR_TIMEOUT(R.string.create_account_activity_message_sms_not_granted_or_timeout, true, true),
	SMS_CALL_SUCCESS          (R.string.create_account_activity_message_sms_call_success, true, true),
	NOT_POSSIBLE              (R.string.create_account_activity_message_not_possible, false, false),
	REGISTRATION_CODE         (R.string.create_account_activity_message_registration_code, false, true),
	TOO_MANY_CONFIRMS         (R.string.create_account_activity_message_too_many_confirms, false, false),
	TOO_MANY_CALLS            (R.string.create_account_activity_message_too_many_calls, false, true),
	SIP_NOT_POSSIBLE          (R.string.create_account_activity_message_sip_not_possible, false, false);

	final int resourceId;
	final boolean telephoneNumber;
	final boolean registrationCodeInputVisible;

	CreateAccountMessage(final int resourceId, final boolean telephoneNumber, final boolean registrationCodeInputVisible)
	{
		this.resourceId = resourceId;
		this.telephoneNumber = telephoneNumber;
		this.registrationCodeInputVisible = registrationCodeInputVisible;
	}

	public int getResourceId()
	{
		return resourceId;
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

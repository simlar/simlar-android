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

package org.simlar.helper;

import org.simlar.utils.Util;

public final class ContactDataComplete extends ContactData
{
	public final String simlarId;

	public ContactDataComplete(final String simlarId, final ContactData cd)
	{
		super(cd.name, cd.guiTelephoneNumber, cd.status, cd.photoId);
		this.simlarId = simlarId;
	}

	public String getNameOrNumber()
	{
		if (Util.isNullOrEmpty(name)) {
			return simlarId;
		}

		return name;
	}

	public char getFirstChar()
	{
		final String nameOrNumber = getNameOrNumber();
		return Util.isNullOrEmpty(nameOrNumber) ? ' ' : Character.toTitleCase(nameOrNumber.charAt(0));
	}
}

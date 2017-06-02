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

public class ContactData
{
	public final String name;
	public final String guiTelephoneNumber;

	public ContactStatus status;
	public final String photoId;

	public ContactData(final String name, final String guiTelephoneNumber, final ContactStatus status, final String photoId)
	{
		this.name = name;
		this.guiTelephoneNumber = guiTelephoneNumber;
		this.status = status;
		this.photoId = photoId;
	}

	public final boolean isRegistered()
	{
		return status.isRegistered();
	}

	@Override
	public final String toString()
	{
		return "ContactData [name=" + name + ", guiTelephoneNumber=" + guiTelephoneNumber + ", status=" + status + ", photoId=" + photoId + ']';
	}
}

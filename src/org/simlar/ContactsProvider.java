/**
 * Copyright (C) 2013 - 2014 The Simlar Authors.
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

import java.util.HashMap;
import java.util.Map;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

public final class ContactsProvider
{
	private static final String LOGTAG = ContactsProvider.class.getSimpleName();

	public static class ContactData
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

		public boolean isRegistered()
		{
			return status.isRegistered();
		}
	}

	public static final class FullContactData extends ContactData
	{
		public final String simlarId;

		public FullContactData(final String simlarId, final String name, final String guiTelephoneNumber, final ContactStatus status,
				final String photoId)
		{
			super(name, guiTelephoneNumber, status, photoId);
			this.simlarId = simlarId;
		}

		public FullContactData(final String simlarId, final ContactData cd)
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
	}

	public static Map<String, ContactData> loadContacts(final Context context)
	{
		final Map<String, ContactData> contacts = loadContactsFromTelephonebook(context);
		updateContactStatus(contacts);
		return contacts;
	}

	private static Map<String, ContactData> loadContactsFromTelephonebook(final Context context)
	{
		Log.i(LOGTAG, "loading contacts from telephone book");
		final Map<String, ContactData> result = new HashMap<String, ContactData>();

		final String[] projection = new String[] {
				ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
				ContactsContract.CommonDataKinds.Phone.NUMBER,
				ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
				ContactsContract.CommonDataKinds.Phone.PHOTO_ID
		};

		final Cursor contacts = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null);
		while (contacts.moveToNext())
		{
			final long contactId = contacts.getLong(0);
			final String number = contacts.getString(1);
			final String name = contacts.getString(2);
			final boolean hasPhotoId = contacts.getLong(3) != 0;
			String photoUri = null;

			if (Util.isNullOrEmpty(number)) {
				continue;
			}

			final SimlarNumber simlarNumber = new SimlarNumber(number);
			if (Util.isNullOrEmpty(simlarNumber.getSimlarId())) {
				continue;
			}

			if (hasPhotoId) {
				photoUri = Uri.withAppendedPath(ContentUris.withAppendedId(
						ContactsContract.Contacts.CONTENT_URI, contactId), ContactsContract.Contacts.Photo.CONTENT_DIRECTORY).toString();
			}

			if (!result.containsKey(simlarNumber.getSimlarId())) {
				result.put(simlarNumber.getSimlarId(), new ContactData(name, simlarNumber.getGuiTelephoneNumber(), ContactStatus.UNKNOWN,
						photoUri));

				/// ATTENTIION this logs the users telephone book
				//Log.d(LOGTAG, "adding contact " + name + " " + number + " => " + simlarNumber.getSimlarId());
			}
		}
		contacts.close();

		Log.i(LOGTAG, "found " + result.size() + " contacts from telephone book");

		return result;
	}

	private static void updateContactStatus(final Map<String, ContactData> contacts)
	{
		final Map<String, ContactStatus> statusMap = GetContactsStatus.httpPostGetContactsStatus(contacts.keySet());
		Log.i(LOGTAG, "contact status received for " + statusMap.size() + " contacts");

		for (final Map.Entry<String, ContactStatus> entry : statusMap.entrySet()) {
			if (!contacts.containsKey(entry.getKey())) {
				Log.e(LOGTAG, "received contact status " + entry.getValue() + " for unknown contact " + entry.getKey());
				continue;
			}

			if (!entry.getValue().isValid()) {
				Log.e(LOGTAG, "received invalid contact status " + entry.getValue() + " for contact " + entry.getKey());
				continue;
			}

			contacts.get(entry.getKey()).status = entry.getValue();
		}
	}
}

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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;

public final class ContactsProvider
{
	static final String LOGTAG = ContactsProvider.class.getSimpleName();

	private static final ContactsProviderImpl mImpl = new ContactsProviderImpl();

	public static interface FullContactsListener
	{
		void onGetContacts(final Set<FullContactData> contacts);
	}

	public static interface ContactListener
	{
		void onGetNameAndPhotoId(final String name, final String photoId);
	}

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

		@Override
		public String toString()
		{
			return "ContactData [name=" + name + ", guiTelephoneNumber=" + guiTelephoneNumber + ", status=" + status + ", photoId=" + photoId + "]";
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

	private static final class ContactsProviderImpl
	{
		private Map<String, ContactData> mContacts = null;
		private State mState = State.UNINITIALIZED;
		private final Set<FullContactsListener> mFullContactsListeners = new HashSet<FullContactsListener>();
		private final Map<ContactListener, String> mContactListener = new HashMap<ContactsProvider.ContactListener, String>();

		private enum State {
			UNINITIALIZED,
			PARSING_PHONES_ADDRESS_BOOK,
			REQUESTING_CONTACTS_STATUS_FROM_SERVER,
			ERROR,
			INITIALIZED
		}

		public ContactsProviderImpl()
		{
		}

		private void loadContacts(final Context context)
		{
			Log.i(LOGTAG, "start creating contacts cache");
			mState = State.PARSING_PHONES_ADDRESS_BOOK;
			new AsyncTask<Void, Void, Map<String, ContactData>>() {
				@Override
				protected Map<String, ContactData> doInBackground(final Void... params)
				{
					return loadContactsFromTelephonebook(context);
				}

				@Override
				protected void onPostExecute(final Map<String, ContactData> contacts)
				{
					onContactsLoadedFromTelephoneBook(contacts);
				}
			}.execute();
		}

		void onContactsLoadedFromTelephoneBook(final Map<String, ContactData> contacts)
		{
			mContacts = contacts;
			mState = State.REQUESTING_CONTACTS_STATUS_FROM_SERVER;

			for (final Map.Entry<ContactListener, String> entry : mContactListener.entrySet())
			{
				final ContactData cd = createContactData(entry.getValue());
				entry.getKey().onGetNameAndPhotoId(cd.name, cd.photoId);
			}
			mContactListener.clear();

			new AsyncTask<String, Void, Map<String, ContactStatus>>() {
				@Override
				protected Map<String, ContactStatus> doInBackground(final String... params)
				{
					return GetContactsStatus.httpPostGetContactsStatus(new HashSet<String>(Arrays.asList(params)));
				}

				@Override
				protected void onPostExecute(final Map<String, ContactStatus> contactsStatus)
				{
					onContactsStatusRequestedFromServer(contactsStatus);
				}
			}.execute(mContacts.keySet().toArray(new String[mContacts.size()]));
		}

		private ContactData createContactData(final String simlarId)
		{
			final ContactData cd = mContacts.get(simlarId);

			if (cd == null) {
				return new ContactData(simlarId, null, null, null);
			}

			if (Util.isNullOrEmpty(cd.name) && Util.isNullOrEmpty(cd.guiTelephoneNumber)) {
				return new ContactData(simlarId, null, null, cd.photoId);
			}

			if (Util.isNullOrEmpty(cd.name)) {
				return new ContactData(cd.guiTelephoneNumber, null, null, cd.photoId);
			}

			return cd;
		}

		void onContactsStatusRequestedFromServer(final Map<String, ContactStatus> contactsStatus)
		{
			Set<FullContactData> contacts = null;
			if (updateContactStatus(contactsStatus)) {
				contacts = createFullContactDataSet();
				mState = State.INITIALIZED;
			} else {
				mState = State.ERROR;
			}

			for (final FullContactsListener listener : mFullContactsListeners) {
				listener.onGetContacts(contacts);
			}
			mFullContactsListeners.clear();
		}

		private Set<FullContactData> createFullContactDataSet()
		{
			final Set<FullContactData> registeredContacts = new HashSet<FullContactData>();
			for (final Map.Entry<String, ContactData> c : mContacts.entrySet()) {
				if (c.getValue().isRegistered()) {
					registeredContacts.add(new FullContactData(c.getKey(), c.getValue()));
				}
			}

			Log.i(LOGTAG, "found " + registeredContacts.size() + " registered contacts");
			return registeredContacts;
		}

		static Map<String, ContactData> loadContactsFromTelephonebook(final Context context)
		{
			Log.i(LOGTAG, "loading contacts from telephone book");
			final Map<String, ContactData> result = new HashMap<String, ContactData>();

			final String[] projection = new String[] {
					ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
					ContactsContract.CommonDataKinds.Phone.NUMBER,
					ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
					ContactsContract.CommonDataKinds.Phone.PHOTO_ID
			};

			final Cursor contacts = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
					null);
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
					// Log.d(LOGTAG, "adding contact " + name + " " + number + " => " + simlarNumber.getSimlarId());
				}
			}
			contacts.close();

			Log.i(LOGTAG, "found " + result.size() + " contacts from telephone book");

			return result;
		}

		private boolean updateContactStatus(final Map<String, ContactStatus> statusMap)
		{
			if (statusMap == null) {
				return false;
			}

			Log.i(LOGTAG, "contact status received for " + statusMap.size() + " contacts");

			for (final Map.Entry<String, ContactStatus> entry : statusMap.entrySet()) {
				if (!mContacts.containsKey(entry.getKey())) {
					Log.e(LOGTAG, "received contact status " + entry.getValue() + " for unknown contact " + entry.getKey());
					continue;
				}

				if (!entry.getValue().isValid()) {
					Log.e(LOGTAG, "received invalid contact status " + entry.getValue() + " for contact " + entry.getKey());
					continue;
				}

				mContacts.get(entry.getKey()).status = entry.getValue();
			}

			return true;
		}

		void getContacts(final Context context, final FullContactsListener listener)
		{
			if (context == null) {
				Log.e(LOGTAG, "no context");
				return;
			}

			if (listener == null) {
				Log.e(LOGTAG, "no listener");
				return;
			}

			switch (mState) {
			case INITIALIZED:
				Log.i(LOGTAG, "using cached data for all contacts");
				listener.onGetContacts(createFullContactDataSet());
				break;
			case PARSING_PHONES_ADDRESS_BOOK:
			case REQUESTING_CONTACTS_STATUS_FROM_SERVER:
				mFullContactsListeners.add(listener);
				break;
			case UNINITIALIZED:
			case ERROR:
				mFullContactsListeners.add(listener);
				loadContacts(context);
				break;
			default:
				Log.e(LOGTAG, "unknown state=" + mState);
				break;
			}
		}

		void getNameAndPhotoId(final String simlarId, final Context context, final ContactListener listener)
		{
			if (context == null) {
				Log.e(LOGTAG, "no context");
				return;
			}

			if (listener == null) {
				Log.e(LOGTAG, "no listener");
				return;
			}

			if (Util.isNullOrEmpty(simlarId)) {
				Log.i(LOGTAG, "empty simlarId");
				listener.onGetNameAndPhotoId(null, null);
				return;
			}

			switch (mState) {
			case INITIALIZED:
			case REQUESTING_CONTACTS_STATUS_FROM_SERVER:
				Log.i(LOGTAG, "using cached data for name and photoId");
				final ContactData cd = createContactData(simlarId);
				listener.onGetNameAndPhotoId(cd.name, cd.photoId);
				break;
			case PARSING_PHONES_ADDRESS_BOOK:
				mContactListener.put(listener, simlarId);
				break;
			case UNINITIALIZED:
			case ERROR:
				mContactListener.put(listener, simlarId);
				loadContacts(context);
				break;
			default:
				Log.e(LOGTAG, "unknown state=" + mState);
				break;
			}
		}
	}

	public static void getContacts(final Context context, final FullContactsListener listener)
	{
		mImpl.getContacts(context, listener);
	}

	public static void getNameAndPhotoId(final String simlarId, final Context context, final ContactListener listener)
	{
		mImpl.getNameAndPhotoId(simlarId, context, listener);
	}
}

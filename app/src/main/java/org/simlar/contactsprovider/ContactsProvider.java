/**
 * Copyright (C) 2013 - 2014 The Simlar Authors.
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

package org.simlar.contactsprovider;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.MediaStore;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.simlar.helper.ContactData;
import org.simlar.helper.ContactDataComplete;
import org.simlar.helper.ContactStatus;
import org.simlar.helper.FileHelper;
import org.simlar.helper.FileHelper.NotInitedException;
import org.simlar.helper.PermissionsHelper;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.SimlarNumber;
import org.simlar.https.GetContactsStatus;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public final class ContactsProvider
{
	private static final ContactsProviderImpl mImpl = new ContactsProviderImpl();

	private ContactsProvider()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	@FunctionalInterface
	public interface FullContactsListener
	{
		void onGetContacts(final Set<ContactDataComplete> contacts, final Error error);
	}

	@FunctionalInterface
	public interface ContactListener
	{
		void onGetNameAndPhotoId(final String name, final String photoId);
	}

	public interface ContactStatusListener
	{
		void onGetStatus(final boolean registered);
		void onOffline();
	}

	public enum Error
	{
		NONE,
		BUG,
		NO_INTERNET_CONNECTION,
		PERMISSION_DENIED
	}

	private static final class ContactsProviderImpl
	{
		private final Map<String, ContactData> mContacts = new HashMap<>();
		private State mState = State.UNINITIALIZED;
		boolean mFakeData = false;
		private final Set<FullContactsListener> mFullContactsListeners = new HashSet<>();
		private final Map<ContactListener, String> mContactListener = new HashMap<>();
		private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
		private final Handler mMainLoopHandler = new Handler(Looper.getMainLooper());

		private enum State
		{
			UNINITIALIZED,
			PARSING_PHONES_ADDRESS_BOOK,
			REQUESTING_CONTACTS_STATUS_FROM_SERVER,
			ERROR,
			INITIALIZED
		}

		private void loadContacts(final Context context)
		{
			Lg.i("start creating contacts cache");
			final String mySimlarId = PreferencesHelper.getMySimlarIdOrEmptyString();

			if (Util.isNullOrEmpty(mySimlarId)) {
				Lg.e("loadContacts: no simlarId for myself, probably PreferencesHelper not inited => aborting");
				onError(Error.BUG);
				return;
			}

			if (!PermissionsHelper.hasPermission(context, PermissionsHelper.Type.CONTACTS)) {
				Lg.e("loadContacts: we do not have the permission to read contacts => aborting");
				onError(Error.PERMISSION_DENIED);
				return;
			}

			mState = State.PARSING_PHONES_ADDRESS_BOOK;
			mExecutorService.execute(() -> {
				final Map<String, ContactData> contacts = mFakeData
						? createFakeData()
						: loadContactsFromTelephoneBook(context, mySimlarId);

				mMainLoopHandler.post(() -> onContactsLoadedFromTelephoneBook(contacts));
			});
		}

		private void onError(final Error error)
		{
			mState = State.ERROR;
			if (error != Error.PERMISSION_DENIED) {
				mContacts.clear();
			}

			notifyContactListeners();
			notifyFullContactsListeners(null, error);
		}

		private void notifyContactListeners()
		{
			for (final Map.Entry<ContactListener, String> entry : mContactListener.entrySet()) {
				final ContactData cd = createContactData(entry.getValue());
				entry.getKey().onGetNameAndPhotoId(cd.name, cd.photoId);
			}
			mContactListener.clear();
		}

		private void notifyFullContactsListeners(final Set<ContactDataComplete> contacts, final Error error)
		{
			for (final FullContactsListener listener : mFullContactsListeners) {
				listener.onGetContacts(contacts, error);
			}
			mFullContactsListeners.clear();
		}

		void onContactsLoadedFromTelephoneBook(final Map<String, ContactData> contacts)
		{
			if (contacts == null) {
				Lg.e("onContactsLoadedFromTelephoneBook called with empty contacts");
				onError(Error.BUG);
				return;
			}

			mContacts.clear();
			mContacts.putAll(contacts);
			mState = State.REQUESTING_CONTACTS_STATUS_FROM_SERVER;

			notifyContactListeners();

			mExecutorService.execute(() -> {
				final Map<String, ContactStatus> contactsStatus = GetContactsStatus.httpPostGetContactsStatus(mContacts.keySet());

				mMainLoopHandler.post(() -> onContactsStatusRequestedFromServer(contactsStatus));
			});
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
			if (updateContactStatus(contactsStatus)) {
				mState = State.INITIALIZED;
				notifyFullContactsListeners(createFullContactDataSet(), Error.NONE);
			} else {
				onError(Error.NO_INTERNET_CONNECTION);
			}
		}

		private Set<ContactDataComplete> createFullContactDataSet()
		{
			final Set<ContactDataComplete> registeredContacts = new HashSet<>();
			for (final Map.Entry<String, ContactData> c : mContacts.entrySet()) {
				if (c.getValue().isRegistered()) {
					registeredContacts.add(new ContactDataComplete(c.getKey(), c.getValue()));
				}
			}

			Lg.i("found ", registeredContacts.size(), " registered contacts");
			return registeredContacts;
		}

		private static String createFakePhotoString()
		{
			try {
				return "file://" + FileHelper.getFakePhoneBookPicture();
			} catch (final NotInitedException e) {
				Lg.ex(e, "PreferencesHelper.NotInitedException");
				return "";
			}
		}

		static Map<String, ContactData> createFakeData()
		{
			Lg.i("creating fake telephone book");

			final Map<String, ContactData> result = new HashMap<>();
			final String fakePhoto = createFakePhotoString();
			result.put("*0002*", new ContactData("Barney Gumble", "+49 171 111111", ContactStatus.UNKNOWN, ""));
			result.put("*0004*", new ContactData("Bender Rodriguez", "+49 172 222222", ContactStatus.UNKNOWN, ""));
			result.put("*0005*", new ContactData("Eric Cartman", "+49 173 333333", ContactStatus.UNKNOWN, ""));
			result.put("*0006*", new ContactData("Glenn Quagmire", "+49 174 444444", ContactStatus.UNKNOWN, ""));
			result.put("*0007*", new ContactData("H. M. Murdock", "+49 175 555555", ContactStatus.UNKNOWN, ""));
			result.put("*0008*", new ContactData("Leslie Knope", "+49 176 666666", ContactStatus.UNKNOWN, ""));
			result.put("*0001*", new ContactData("Mona Lisa", "+49 177 777777", ContactStatus.UNKNOWN, fakePhoto));
			result.put("*0003*", new ContactData("Rosemarie", "+49 178 888888", ContactStatus.UNKNOWN, fakePhoto));
			result.put("*0009*", new ContactData("Stan Smith", "+49 179 999999", ContactStatus.UNKNOWN, ""));
			return result;
		}

		static Map<String, ContactData> loadContactsFromTelephoneBook(final Context context, final String mySimlarId)
		{
			Lg.i("loading contacts from telephone book");
			final Map<String, ContactData> result = new HashMap<>();

			final String[] projection = {
					ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
					ContactsContract.CommonDataKinds.Phone.NUMBER,
					ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY,
					ContactsContract.CommonDataKinds.Phone.PHOTO_ID
			};

			final Cursor contacts = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
					null);

			if (contacts == null) {
				Lg.e("contacts cursor null");
				return result;
			}

			while (contacts.moveToNext()) {
				final long contactId = contacts.getLong(0);
				final String number = contacts.getString(1);
				final String name = contacts.getString(2);
				final boolean hasPhotoId = contacts.getLong(3) != 0;

				if (Util.isNullOrEmpty(number)) {
					continue;
				}

				final SimlarNumber simlarNumber = new SimlarNumber(number);
				final String simlarId = simlarNumber.getSimlarId();
				if (Util.isNullOrEmpty(simlarId)) {
					continue;
				}

				if (Util.equalString(simlarId, mySimlarId)) {
					continue;
				}

				final String photoUri = hasPhotoId
						? Uri.withAppendedPath(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), ContactsContract.Contacts.Photo.CONTENT_DIRECTORY).toString()
						: null;

				final ContactData contactData = result.get(simlarId);
				if (contactData == null || Util.isNullOrEmpty(contactData.name)) {
					result.put(simlarId, new ContactData(Util.equalString(name, number) ? "" : name,
							simlarNumber.getGuiTelephoneNumber(), ContactStatus.UNKNOWN, photoUri));

					/// ATTENTION this logs the users telephone book
					// Lg.d("adding contact " + name + " " + number + " => " + simlarId);
				}
			}
			contacts.close();

			Lg.i("found ", result.size(), " contacts from telephone book");

			return result;
		}

		private boolean updateContactStatus(final Map<String, ContactStatus> statusMap)
		{
			if (statusMap == null) {
				return false;
			}

			Lg.i("contact status received for ", statusMap.size(), " contacts");

			for (final Map.Entry<String, ContactStatus> entry : statusMap.entrySet()) {
				final ContactData contactData = mContacts.get(entry.getKey());
				if (contactData == null) {
					Lg.e("received contact status ", entry.getValue(), " for unknown contact ", entry.getKey());
					continue;
				}

				if (!entry.getValue().isValid()) {
					Lg.e("received invalid contact status ", entry.getValue(), " for contact ", entry.getKey());
					continue;
				}

				contactData.status = entry.getValue();
			}

			return true;
		}

		void preLoadContacts(final Context context)
		{
			if (context == null) {
				Lg.e("no context");
				return;
			}

			switch (mState) {
				case INITIALIZED:
				case PARSING_PHONES_ADDRESS_BOOK:
				case REQUESTING_CONTACTS_STATUS_FROM_SERVER:
					break;
				case UNINITIALIZED:
				case ERROR:
					loadContacts(context);
					break;
				default:
					Lg.e("unknown state=", mState);
					break;
			}
		}

		void addContact(final String simlarId, final String name, final String telephoneNumber)
		{
			if (Util.isNullOrEmpty(simlarId)) {
				Lg.e("no simlarId");
				return;
			}

			Lg.i("manually add contact with simlarId=", new Lg.Anonymizer(simlarId));
			mContacts.put(simlarId, new ContactData(name, telephoneNumber, ContactStatus.REGISTERED, null));
		}

		void getContacts(final Context context, final FullContactsListener listener)
		{
			if (context == null) {
				Lg.e("no context");
				return;
			}

			if (listener == null) {
				Lg.e("no listener");
				return;
			}

			switch (mState) {
				case INITIALIZED -> {
					Lg.i("using cached data for all contacts");
					listener.onGetContacts(createFullContactDataSet(), Error.NONE);
				}
				case PARSING_PHONES_ADDRESS_BOOK, REQUESTING_CONTACTS_STATUS_FROM_SERVER -> mFullContactsListeners.add(listener);
				case UNINITIALIZED, ERROR -> {
					mFullContactsListeners.add(listener);
					loadContacts(context);
				}
				default -> Lg.e("unknown state=", mState);
			}
		}

		void getNameAndPhotoId(final String simlarId, final Context context, final ContactListener listener)
		{
			if (context == null) {
				Lg.e("no context");
				return;
			}

			if (listener == null) {
				Lg.e("no listener");
				return;
			}

			if (Util.isNullOrEmpty(simlarId)) {
				Lg.i("empty simlarId");
				listener.onGetNameAndPhotoId(null, null);
				return;
			}

			switch (mState) {
				case INITIALIZED, REQUESTING_CONTACTS_STATUS_FROM_SERVER -> {
					Lg.i("using cached data for name and photoId");
					final ContactData cd = createContactData(simlarId);
					listener.onGetNameAndPhotoId(cd.name, cd.photoId);
				}
				case PARSING_PHONES_ADDRESS_BOOK -> mContactListener.put(listener, simlarId);
				case UNINITIALIZED, ERROR -> {
					mContactListener.put(listener, simlarId);
					loadContacts(context);
				}
				default -> Lg.e("unknown state=", mState);
			}
		}

		boolean clearCache()
		{
			switch (mState) {
				case UNINITIALIZED:
					return true;
				case REQUESTING_CONTACTS_STATUS_FROM_SERVER:
					Lg.w("clearCache while requesting contacts from server => aborting");
					return false;
				case PARSING_PHONES_ADDRESS_BOOK:
					Lg.w("clearCache while parsing phone book => aborting");
					return false;
				case INITIALIZED:
				case ERROR:
					break;
				default:
					Lg.e("unknown state=", mState);
					break;
			}

			mState = State.UNINITIALIZED;
			mContacts.clear();
			return true;
		}
	}

	public static void preLoadContacts(final Context context)
	{
		mImpl.preLoadContacts(context);
	}

	public static void addContact(final String simlarId, final String name, final String telephoneNumber)
	{
		mImpl.addContact(simlarId, name, telephoneNumber);
	}

	public static void getContacts(final Context context, final FullContactsListener listener)
	{
		mImpl.getContacts(context, listener);
	}

	public static void getNameAndPhotoId(final String simlarId, final Context context, final ContactListener listener)
	{
		mImpl.getNameAndPhotoId(simlarId, context, listener);
	}

	public static boolean clearCache()
	{
		return mImpl.clearCache();
	}

	public static void toggleFakeMode()
	{
		mImpl.mFakeData = !mImpl.mFakeData;
	}

	public static boolean getFakeMode()
	{
		return mImpl.mFakeData;
	}

	public static Bitmap getContactPhotoBitmap(final Context context, final int defaultResourceId, final String contactPhotoId)
	{
		if (Util.isNullOrEmpty(contactPhotoId)) {
			return BitmapFactory.decodeResource(context.getResources(), defaultResourceId);
		}

		try {
			return MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(contactPhotoId));
		} catch (final FileNotFoundException e) {
			Lg.ex(e, "getContactPhotoBitmap FileNotFoundException");
		} catch (final IOException e) {
			Lg.ex(e, "getContactPhotoBitmap IOException");
		}

		return BitmapFactory.decodeResource(context.getResources(), defaultResourceId);
	}

	public static void getContactStatus(final String simlarId, final ContactStatusListener listener)
	{
		if (Util.isNullOrEmpty(simlarId)) {
			Lg.e("no simlarId");
			return;
		}

		if (listener == null) {
			Lg.e("no listener");
			return;
		}

		Executors.newSingleThreadExecutor().execute(() -> {
			final Map<String, ContactStatus> contactsStatus = GetContactsStatus.httpPostGetContactsStatus(Collections.singleton(simlarId));

			new Handler(Looper.getMainLooper()).post(() -> {
				if (contactsStatus == null) {
					listener.onOffline();
				} else {
					listener.onGetStatus(contactsStatus.get(simlarId) == ContactStatus.REGISTERED);
				}
			});
		});
	}
}

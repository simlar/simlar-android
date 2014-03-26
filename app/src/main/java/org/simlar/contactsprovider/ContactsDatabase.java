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

package org.simlar.contactsprovider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import org.simlar.helper.ContactData;
import org.simlar.helper.ContactStatus;
import org.simlar.logging.Lg;

import java.util.HashMap;
import java.util.Map;

final class ContactsDatabase
{
	private ContactsDatabase()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static abstract class ContactEntry implements BaseColumns
	{
		public static final String TABLE_NAME = "contacts";
		public static final String COLUMN_NAME_SIMLAR_ID = "simlarId";
		public static final String COLUMN_NAME_NAME = "name";
		public static final String COLUMN_NAME_GUI_TELEPHONE_NUMBER = "guiTelephoneNumber";
		public static final String COLUMN_NAME_STATUS = "status";
		public static final String COLUMN_NAME_PHOTO_ID = "photoId";
	}

	public static final class ContactsDbHelper extends SQLiteOpenHelper
	{
		// If you change the database schema, you must increment the database version.
		private static final int DATABASE_VERSION = 1;
		private static final String DATABASE_NAME = "contacts.db";
		private static final String SQL_CREATE_ENTRIES =
				"CREATE TABLE " + ContactEntry.TABLE_NAME + " (" +
						ContactEntry.COLUMN_NAME_SIMLAR_ID + " TEXT PRIMARY KEY," +
						ContactEntry.COLUMN_NAME_NAME + " TEXT," +
						ContactEntry.COLUMN_NAME_GUI_TELEPHONE_NUMBER + " TEXT, " +
						ContactEntry.COLUMN_NAME_STATUS + " INT," +
						ContactEntry.COLUMN_NAME_PHOTO_ID + " TEXT" +
						" )";
		private static final String SQL_DELETE_ENTRIES =
				"DROP TABLE IF EXISTS " + ContactEntry.TABLE_NAME;

		public ContactsDbHelper(final Context context)
		{
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(final SQLiteDatabase db)
		{
			db.execSQL(SQL_CREATE_ENTRIES);
		}

		@Override
		public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion)
		{
			recreateTable(db);
		}

		public void recreateTable(final SQLiteDatabase db)
		{
			db.execSQL(SQL_DELETE_ENTRIES);
			onCreate(db);
		}

		@Override
		public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion)
		{
			onUpgrade(db, oldVersion, newVersion);
		}
	}

	@SuppressWarnings({ "unused" })
	public static void save(final Context context, final Map<String, ContactData> contacts)
	{
		Lg.i("saving ", contacts.size(), " contacts");
		final ContactsDbHelper dbHelper = new ContactsDbHelper(context);
		final SQLiteDatabase db = dbHelper.getWritableDatabase();

		db.beginTransaction();
		dbHelper.recreateTable(db);
		for (final Map.Entry<String, ContactData> entry : contacts.entrySet()) {
			final ContentValues values = new ContentValues();
			values.put(ContactEntry.COLUMN_NAME_SIMLAR_ID, entry.getKey());
			values.put(ContactEntry.COLUMN_NAME_NAME, entry.getValue().name);
			values.put(ContactEntry.COLUMN_NAME_GUI_TELEPHONE_NUMBER, entry.getValue().guiTelephoneNumber);
			values.put(ContactEntry.COLUMN_NAME_STATUS, entry.getValue().status.toInteger());
			values.put(ContactEntry.COLUMN_NAME_PHOTO_ID, entry.getValue().photoId);

			db.insert(ContactEntry.TABLE_NAME, null, values);
		}
		db.setTransactionSuccessful();
		db.endTransaction();

		db.close();
		Lg.i("saving ", contacts.size(), " contacts done");
	}

	@SuppressWarnings({ "unused" })
	public static Map<String, ContactData> read(final Context context)
	{
		Lg.i("reading contacts from db");
		final ContactsDbHelper dbHelper = new ContactsDbHelper(context);
		final SQLiteDatabase db = dbHelper.getWritableDatabase();

		final String[] projection = {
				ContactEntry.COLUMN_NAME_SIMLAR_ID,
				ContactEntry.COLUMN_NAME_NAME,
				ContactEntry.COLUMN_NAME_GUI_TELEPHONE_NUMBER,
				ContactEntry.COLUMN_NAME_STATUS,
				ContactEntry.COLUMN_NAME_PHOTO_ID
		};

		final Cursor c = db.query(
				ContactEntry.TABLE_NAME,  // The table to query
				projection,               // The columns to return
				null,                     // The columns for the WHERE clause
				null,                     // The values for the WHERE clause
				null,                     // don't group the rows
				null,                     // don't filter by row groups
				null);                    // The sort order

		final Map<String, ContactData> contacts = new HashMap<>();
		while (c.moveToNext()) {
			contacts.put(c.getString(0), new ContactData(c.getString(1), c.getString(2), ContactStatus.fromInt(c.getInt(3)), c.getString(4)));
		}

		c.close();
		db.close();

		Lg.i("read ", contacts.size(), " contacts");

		return contacts;
	}
}

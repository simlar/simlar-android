/**
 * Copyright (C) 2015 The Simlar Authors.
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

import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;

import java.util.Set;


public class AddToCallActivity extends ActionBarActivity
{
	private static final String LOGTAG = AddToCallActivity.class.getSimpleName();

	private ContactsAdapter mAdapter = null;
	private ContactsListFragment mContactList = null;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Lg.i(LOGTAG, "onCreate ");

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_to_call);

		mAdapter = ContactsAdapter.createContactsAdapter(this);

		final FragmentManager fm = getSupportFragmentManager();
		mContactList = (ContactsListFragment) fm.findFragmentById(android.R.id.content);
		if (mContactList == null) {
			mContactList = new ContactsListFragment();
			fm.beginTransaction().add(android.R.id.content, mContactList).commit();
		}
		mContactList.setListAdapter(mAdapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		return true;
	}

	@Override
	protected void onResume()
	{
		Lg.i(LOGTAG, "onResume");
		super.onResume();

		if (mAdapter.isEmpty()) {
			ContactsProvider.getContacts(this, new ContactsProvider.FullContactsListener()
			{
				@Override
				public void onGetContacts(Set<ContactsProvider.FullContactData> contacts)
				{
					mAdapter.clear();
					mAdapter.addAll(contacts);
				}
			});
		}
	}
}

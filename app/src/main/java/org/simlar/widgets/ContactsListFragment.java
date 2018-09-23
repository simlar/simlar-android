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

package org.simlar.widgets;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ListView;

import org.simlar.R;
import org.simlar.helper.ContactDataComplete;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public final class ContactsListFragment extends ListFragment
{
	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		setEmptyText(getString(R.string.main_activity_contact_list_no_contacts_found));
	}

	@Override
	public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState)
	{
		final ListView listView = getListView();

		if (listView == null) {
			Lg.e("no list view");
			return;
		}

		listView.setDivider(null);
	}

	private String getSimlarId(final int position)
	{
		final ContactsAdapter contactsAdapter = (ContactsAdapter) getListAdapter();
		if (contactsAdapter == null) {
			return null;
		}

		final ContactDataComplete contact = contactsAdapter.getItem(position);
		return contact == null ? null : contact.simlarId;
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		final String simlarId = getSimlarId(position);
		if (Util.isNullOrEmpty(simlarId)) {
			Lg.e("onListItemClick: no simlarId found");
			return;
		}

		CallActivity.createCallView(requireContext(), simlarId);
	}
}

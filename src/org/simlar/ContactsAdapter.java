/**
 * Copyright (C) 2013 The Simlar Authors.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.simlar.SimlarService.FullContactData;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ContactsAdapter extends ArrayAdapter<FullContactData>
{
	private static final String LOGTAG = ContactsAdapter.class.getSimpleName();

	private final int mLayout;
	private EmptyTextListener mEmptyTextListener = null;
	private SimlarServiceCommunicator mCommunicator = null;

	public class SortByName implements Comparator<FullContactData>
	{
		@Override
		public int compare(FullContactData lhs, FullContactData rhs)
		{
			if (lhs == null && rhs == null) {
				return 0;
			}

			if (lhs == null) {
				return -1;
			}

			if (rhs == null) {
				return 1;
			}

			final int retVal = Util.compareString(lhs.name, rhs.name);
			if (retVal == 0) {
				// if name is the same compare by by number
				return Util.compareString(lhs.number, rhs.number);
			}

			return retVal;
		}
	}

	public static ContactsAdapter createContactsAdapter(Context context, final SimlarServiceCommunicator communicator)
	{
		Log.i(LOGTAG, "creating ContactsAdapter");
		return new ContactsAdapter(context, R.layout.contacts, new ArrayList<FullContactData>(), communicator);
	}

	private ContactsAdapter(Context context, int layout, List<FullContactData> values, final SimlarServiceCommunicator communicator)
	{
		super(context, layout, values);
		mLayout = layout;
		mCommunicator = communicator;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		final View rowView = inflater.inflate(mLayout, parent, false);
		final FullContactData contact = getItem(position);

		if (contact == null) {
			return rowView;
		}

		final TextView textView = (TextView) rowView.findViewById(R.id.name);
		textView.setText(contact.getNameOrNumber());

		if (!Util.isNullOrEmpty(contact.photoId)) {
			final ImageView imageView = (ImageView) rowView.findViewById(R.id.picture);
			imageView.setImageURI(Uri.parse(contact.photoId));
		}

		final ImageView statusView = (ImageView) rowView.findViewById(R.id.online_status);
		if (contact.isOnline()) {
			statusView.setImageResource(R.drawable.contact_online);
		} else {
			statusView.setImageResource(R.drawable.contact_offline);
		}
		return rowView;
	}

	@Override
	public void addAll(Collection<? extends FullContactData> contacts)
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			super.addAll(contacts);
		} else {
			for (final FullContactData contact : contacts) {
				super.add(contact);
			}
		}
		sort(new SortByName());
	}

	@Override
	public void addAll(FullContactData... contacts)
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			super.addAll(contacts);
		} else {
			for (final FullContactData contact : contacts) {
				super.add(contact);
			}
		}
		sort(new SortByName());
	}

	@Override
	public void add(FullContactData contact)
	{
		super.add(contact);
		sort(new SortByName());
	}

	void onSimlarStatusChanged()
	{
		if (mCommunicator.getService() == null) {
			Log.e(LOGTAG, "ERROR onSimlarStatusChanged: no service bound");
			return;
		}

		clear();
		final SimlarStatus status = mCommunicator.getService().getSimlarStatus();
		Log.i(LOGTAG, "onSimlarStatusChanged " + status + " (isGoingDown=" + mCommunicator.getService().isGoingDown() + ")");

		if (status == SimlarStatus.ONLINE && !mCommunicator.getService().isGoingDown()) {
			addAll(mCommunicator.getService().getContacts());
		}

		if (mEmptyTextListener == null) {
			Log.e(LOGTAG, "no empty text listener");
			return;
		}

		mEmptyTextListener.onEmptyTextNeeded(status.getContactTextId(mCommunicator.getService().isGoingDown()));
		this.notifyDataSetChanged();
	}

	private FullContactData getContactByNumber(final String number)
	{
		for (int i = 0; i < getCount(); ++i) {
			final FullContactData contact = getItem(i);
			if (contact.number.equals(number)) {
				return contact;
			}
		}
		return null;
	}

	void onPresenceStateChanged(String number, boolean online)
	{
		final FullContactData contact = getContactByNumber(number);
		if (contact == null) {
			if (mCommunicator.getService().getSimlarStatus() == SimlarStatus.ONLINE && !mCommunicator.getService().isGoingDown()) {
				add(mCommunicator.getService().getContact(number));
			}
		} else {
			contact.status = online ? ContactStatus.ONLINE : ContactStatus.OFFLINE;
			notifyDataSetChanged();
		}
	}

	public void call(int position)
	{
		mCommunicator.getService().call(getItem(position).number);
	}

	public void setEmptyTextListener(final EmptyTextListener listener)
	{
		Log.i(LOGTAG, "setEmptyTextListener " + listener.getClass().getSimpleName());
		mEmptyTextListener = listener;
	}
}

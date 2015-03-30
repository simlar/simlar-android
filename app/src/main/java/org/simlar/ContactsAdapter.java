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

package org.simlar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import org.simlar.ContactsProvider.FullContactData;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public final class ContactsAdapter extends ArrayAdapter<FullContactData>
{
	private static final String LOGTAG = ContactsAdapter.class.getSimpleName();

	private final int mLayout;
	private final LayoutInflater mInflater;

	private final class SortByName implements Comparator<FullContactData>
	{
		@Override
		public int compare(final FullContactData lhs, final FullContactData rhs)
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
				// if name is the same compare by simlarId
				return Util.compareString(lhs.simlarId, rhs.simlarId);
			}

			return retVal;
		}
	}

	private static class RowViewHolder
	{
		public RowViewHolder(final View rowView)
		{
			this.letterView = (TextView) rowView.findViewById(R.id.letter);
			this.nameView = (TextView) rowView.findViewById(R.id.name);
			this.numberView = (TextView) rowView.findViewById(R.id.number);
		}

		public final TextView letterView;
		public final TextView nameView;
		public final TextView numberView;
	}

	public ContactsAdapter(final Context context)
	{
		super(context, R.layout.contacts, new ArrayList<FullContactData>());
		mLayout = R.layout.contacts;
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent)
	{
		final View rowView;
		final RowViewHolder holder;
		if (convertView == null) {
			rowView = mInflater.inflate(mLayout, parent, false);
			if (rowView == null) {
				Lg.e(LOGTAG, "no row view found");
				return null;
			}
			holder = new RowViewHolder(rowView);
			rowView.setTag(holder);
		} else {
			rowView = convertView;
			holder = (RowViewHolder) rowView.getTag();
		}

		final FullContactData contact = getItem(position);
		if (contact == null) {
			return rowView;
		}

		if (position > 0) {
			final FullContactData prevContact = getItem(position - 1);
			if (contact.getNameOrNumber().charAt(0) != prevContact.getNameOrNumber().charAt(0)) {
				holder.letterView.setVisibility(View.VISIBLE);
				holder.letterView.setText(Character.toString(contact.getNameOrNumber().charAt(0)));
			} else {
				holder.letterView.setVisibility(View.GONE);
			}
		} else {
			holder.letterView.setVisibility(View.VISIBLE);
			holder.letterView.setText(Character.toString(contact.getNameOrNumber().charAt(0)));
		}

		holder.nameView.setText(contact.getNameOrNumber());
		holder.numberView.setText(contact.guiTelephoneNumber);

		return rowView;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void addAll(final Collection<? extends FullContactData> contacts)
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

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	public void addAll(final FullContactData... contacts)
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
	public void add(final FullContactData contact)
	{
		super.add(contact);
		sort(new SortByName());
	}
}

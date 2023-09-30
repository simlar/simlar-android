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

package org.simlar.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

import org.simlar.R;
import org.simlar.helper.ContactDataComplete;
import org.simlar.utils.Util;

final class ContactsAdapter extends ArrayAdapter<ContactDataComplete>
{
	private final int mLayout;
	private final LayoutInflater mInflater;

	private static final class SortByName implements Comparator<ContactDataComplete>, Serializable
	{
		@Serial
		private static final long serialVersionUID = 1;

		@Override
		public int compare(final ContactDataComplete lhs, final ContactDataComplete rhs)
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
		RowViewHolder(final View rowView)
		{
			letterView = rowView.findViewById(R.id.letter);
			dividerLineView = rowView.findViewById(R.id.dividerLine);
			nameView = rowView.findViewById(R.id.name);
			numberView = rowView.findViewById(R.id.number);
		}

		final TextView letterView;
		final View dividerLineView;
		final TextView nameView;
		final TextView numberView;
	}

	ContactsAdapter(final Context context)
	{
		super(context, R.layout.fragment_contacts_list_element, new ArrayList<>());
		mLayout = R.layout.fragment_contacts_list_element;
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	@NonNull
	public View getView(final int position, @Nullable final View convertView, @NonNull final ViewGroup parent)
	{
		final View rowView;
		final RowViewHolder holder;
		if (convertView == null) {
			rowView = mInflater.inflate(mLayout, parent, false);
			holder = new RowViewHolder(rowView);
			rowView.setTag(holder);
		} else {
			rowView = convertView;
			holder = (RowViewHolder) rowView.getTag();
		}

		final ContactDataComplete contact = getItem(position);
		if (contact == null) {
			return rowView;
		}

		if (position > 0) {
			final ContactDataComplete prevContact = getItem(position - 1);
			if (prevContact == null || contact.getFirstChar() != prevContact.getFirstChar()) {
				holder.letterView.setVisibility(View.VISIBLE);
				holder.letterView.setText(Character.toString(contact.getFirstChar()));
				holder.dividerLineView.setVisibility(View.GONE);
			} else {
				holder.letterView.setVisibility(View.GONE);
				holder.dividerLineView.setVisibility(View.VISIBLE);
			}
		} else {
			holder.letterView.setVisibility(View.VISIBLE);
			holder.letterView.setText(Character.toString(contact.getFirstChar()));
			holder.dividerLineView.setVisibility(View.GONE);
		}

		holder.nameView.setText(contact.getNameOrNumber());
		holder.numberView.setText(contact.guiTelephoneNumber);

		return rowView;
	}

	public void setContacts(final Set<ContactDataComplete> contacts)
	{
		setNotifyOnChange(false);
		clear();
		addAll(contacts);
		sort(new SortByName());
		notifyDataSetChanged();
	}
}

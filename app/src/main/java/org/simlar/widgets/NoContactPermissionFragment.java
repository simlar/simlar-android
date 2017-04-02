/*
 * Copyright (C) 2013 - 2017 The Simlar Authors.
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

package org.simlar.widgets;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.simlar.R;
import org.simlar.contactsprovider.ContactsProvider;
import org.simlar.helper.PermissionsHelper;
import org.simlar.helper.SimlarNumber;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import static android.app.Activity.RESULT_OK;


public final class NoContactPermissionFragment extends Fragment
{
	private static final int PICK_CONTACT = 4711;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.fragment_no_contact_permission, container, false);

		view.findViewById(R.id.buttonSettings).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				requestContactPermissionsClicked();
			}
		});
		view.findViewById(R.id.buttonCallContact).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				callContactClicked();
			}
		});

		return view;
	}

	private void requestContactPermissionsClicked()
	{
		Lg.i("requestContactPermissionsClicked");
		PermissionsHelper.requestContactPermission(getActivity());
	}

	private void callContactClicked()
	{
		Lg.i("callContactClicked");

		final Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
		startActivityForResult(intent, PICK_CONTACT);
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		Lg.i("onActivityResult");

		if (requestCode == PICK_CONTACT && resultCode == RESULT_OK) {
			final Uri contactUri = data.getData();
			final Cursor cursor = getActivity().getContentResolver().query(contactUri, null, null, null, null);
			if (cursor != null) {
				cursor.moveToFirst();
				final String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY));
				final String telephoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
				cursor.close();

				callContact(name, telephoneNumber);
			}
		}
	}

	private void callContact(final String name, final String telephoneNumber)
	{
		final String simlarId = SimlarNumber.createSimlarId(telephoneNumber);

		if (Util.isNullOrEmpty(simlarId)) {
			(new AlertDialog.Builder(getActivity()))
					.setTitle(R.string.no_contact_permission_fragment_alert_no_simlarId_title)
					.setMessage(Util.fromHtml(String.format(getString(R.string.no_contact_permission_fragment_alert_no_simlarId_message), telephoneNumber)))
					.create().show();
			return;
		}

		final AlertDialog dialog = (new AlertDialog.Builder(getActivity()))
				.setTitle(R.string.no_contact_permission_fragment_alert_checking_status_title)
				.setMessage(name + " " + telephoneNumber)
				.setCancelable(false)
				.create();
		dialog.show();

		ContactsProvider.getContactStatus(simlarId, new ContactsProvider.ContactStatusListener()
		{
			@Override
			public void onOffline()
			{
				dialog.dismiss();
				(new AlertDialog.Builder(getActivity()))
						.setTitle(R.string.no_contact_permission_fragment_alert_offline_title)
						.setMessage(getString(R.string.no_contact_permission_fragment_alert_offline_message))
						.create().show();
			}

			@Override
			public void onGetStatus(final boolean registered)
			{
				dialog.dismiss();
				if (!registered) {
					(new AlertDialog.Builder(getActivity()))
							.setTitle(String.format(getString(R.string.no_contact_permission_fragment_alert_contact_not_registered_title), telephoneNumber))
							.setMessage(Util.fromHtml(String.format(getString(R.string.no_contact_permission_fragment_alert_contact_not_registered_message), name)))
							.create().show();
					return;
				}

				ContactsProvider.addContact(simlarId, name, telephoneNumber);

				CallActivity.createCallView(getActivity(), simlarId);
			}
		});
	}
}

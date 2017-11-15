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

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
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

	private Listener mListener = null;
	private boolean mShouldShowRationalBeforeRequest = false;

	@FunctionalInterface
	public interface Listener
	{
		@SuppressWarnings("UnusedParameters")
		void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults);
	}

	@Override
	public void onAttach(final Context context)
	{
		super.onAttach(context);
		Lg.i("onAttach");

		if (!(context instanceof Listener)) {
			Lg.e("not attached to listener object");
			return;
		}

		mListener = (Listener) context;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.fragment_no_contact_permission, container, false);

		view.findViewById(R.id.buttonRequestContactsPermissions).setOnClickListener(new View.OnClickListener()
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
		mShouldShowRationalBeforeRequest = PermissionsHelper.shouldShowRationale(getActivity(), PermissionsHelper.Type.CONTACTS);
		PermissionsHelper.requestContactPermission(this);
	}

	@Override
	public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults)
	{
		// if shouldShowRationale returns false before and after requesting contacts permission,
		// we assume the user has checked "Never Ask again" before and no dialog has been shown.
		// In this case Simlar opens the settings app.
		if (!PermissionsHelper.isGranted(PermissionsHelper.Type.CONTACTS, permissions, grantResults)
				&& !mShouldShowRationalBeforeRequest && !PermissionsHelper.shouldShowRationale(getActivity(), PermissionsHelper.Type.CONTACTS)) {
			PermissionsHelper.openAppSettings(getActivity());
		}

		if (mListener != null) {
			mListener.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	private void callContactClicked()
	{
		Lg.i("callContactClicked");

		final Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
		startActivityForResult(intent, PICK_CONTACT);
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data)
	{
		if (requestCode != PICK_CONTACT) {
			Lg.e("onActivityResult with unknown requestCode=", requestCode);
			return;
		}

		if (resultCode != RESULT_OK) {
			Lg.i("onActivityResult with resultCode=", resultCode);
			return;
		}

		if (data == null) {
			Lg.e("onActivityResult without intent data");
			return;
		}

		final Uri contactUri = data.getData();
		if (contactUri == null) {
			Lg.e("onActivityResult without contactUri data=", data);
			return;
		}


		final Cursor cursor = getActivity().getContentResolver().query(contactUri, null, null, null, null);
		if (cursor == null) {
			Lg.e("onActivityResult failed to create cursor for contactUri=", contactUri);
			return;
		}

		cursor.moveToFirst();
		final String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY));
		final String telephoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
		cursor.close();

		callContact(name, telephoneNumber);
	}

	private void callContact(final String name, final String telephoneNumber)
	{
		final String simlarId = SimlarNumber.createSimlarId(telephoneNumber);

		if (Util.isNullOrEmpty(simlarId)) {
			Lg.i("not calling contact because of invalid telephoneNumber=", new Lg.Anonymizer(telephoneNumber));
			new AlertDialog.Builder(getActivity())
					.setTitle(R.string.no_contact_permission_fragment_alert_no_simlarId_title)
					.setMessage(Util.fromHtml(String.format(getString(R.string.no_contact_permission_fragment_alert_no_simlarId_message), telephoneNumber)))
					.create().show();
			return;
		}

		Lg.i("checking status of simlarId=", new Lg.Anonymizer(simlarId));
		//noinspection deprecation
		final ProgressDialog dialog = new ProgressDialog(getActivity());
		dialog.setTitle(R.string.no_contact_permission_fragment_alert_checking_status_title);
		dialog.setMessage(name + '\n' + telephoneNumber);
		dialog.show();

		ContactsProvider.getContactStatus(simlarId, new ContactsProvider.ContactStatusListener()
		{
			@Override
			public void onOffline()
			{
				dialog.dismiss();
				Lg.i("no connection to the server");
				new AlertDialog.Builder(getActivity())
						.setTitle(R.string.no_contact_permission_fragment_alert_offline_title)
						.setMessage(getString(R.string.no_contact_permission_fragment_alert_offline_message))
						.create().show();
			}

			@Override
			public void onGetStatus(final boolean registered)
			{
				dialog.dismiss();
				if (!registered) {
					Lg.i("simlarId=", new Lg.Anonymizer(simlarId), " not registered");
					new AlertDialog.Builder(getActivity())
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

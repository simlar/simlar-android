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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.simlar.R;
import org.simlar.contactsprovider.ContactsProvider;
import org.simlar.helper.PermissionsHelper;
import org.simlar.helper.SimlarNumber;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

@SuppressWarnings("WeakerAccess")
public final class NoContactPermissionFragment extends Fragment
{
	private Listener mListener = null;
	private boolean mShouldShowRationalBeforeRequest = false;
	private final ActivityResultLauncher<Intent> mStartForContactResult = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(), result -> {
				final int resultCode = result.getResultCode();
				if (resultCode != Activity.RESULT_OK) {
					Lg.i("start activity for contact result ended with resultCode=", resultCode);
					return;
				}

				callContact(fromData(result.getData()));
			});

	private final ActivityResultLauncher<String> mRequestPermissionLauncher = registerForActivityResult(
			new ActivityResultContracts.RequestPermission(), isGranted -> {
				// if shouldShowRationale returns false before and after requesting contacts permission,
				// we assume the user has checked "Never Ask again" before and no dialog has been shown.
				// In this case Simlar opens the settings app.
				if (!isGranted && !mShouldShowRationalBeforeRequest && !PermissionsHelper.shouldShowRationale(getActivity(), PermissionsHelper.Type.CONTACTS)) {
					PermissionsHelper.openAppSettings(getActivity());
				}

				if (isGranted) {
					mListener.onContactPermissionGranted();
				}
			});

	@FunctionalInterface
	public interface Listener
	{
		void onContactPermissionGranted();
	}

	@Override
	public void onAttach(@NonNull final Context context)
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

		view.findViewById(R.id.buttonRequestContactsPermissions).setOnClickListener(v -> requestContactPermissionsClicked());
		view.findViewById(R.id.buttonCallContact).setOnClickListener(v -> callContactClicked());

		return view;
	}

	private void requestContactPermissionsClicked()
	{
		Lg.i("requestContactPermissionsClicked");
		mShouldShowRationalBeforeRequest = PermissionsHelper.shouldShowRationale(getActivity(), PermissionsHelper.Type.CONTACTS);
		mRequestPermissionLauncher.launch(PermissionsHelper.Type.CONTACTS.getPermission());
	}

	private void callContactClicked()
	{
		Lg.i("callContactClicked");

		final Intent intent = new Intent(Intent.ACTION_PICK);
		intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
		mStartForContactResult.launch(intent);
	}

	private static class ActivityResultContact
	{
		final String name;
		final String telephoneNumber;

		ActivityResultContact(final String name, final String telephoneNumber)
		{
			this.name = name;
			this.telephoneNumber = telephoneNumber;
		}
	}

	private ActivityResultContact fromData(final Intent data)
	{
		if (data == null) {
			Lg.e("onActivityResult without intent data");
			return null;
		}

		final Uri contactUri = data.getData();
		if (contactUri == null) {
			Lg.e("onActivityResult without contactUri data=", data);
			return null;
		}

		final String[] projection = { ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY, ContactsContract.CommonDataKinds.Phone.NUMBER };
		final Cursor cursor = requireContext().getContentResolver().query(contactUri, projection, null, null, null);
		if (cursor == null) {
			Lg.e("onActivityResult failed to create cursor for contactUri=", contactUri);
			return null;
		}

		if (!cursor.moveToFirst()) {
			Lg.e("onActivityResult failed to move cursor to first result for contactUri=", contactUri);
			cursor.close();
			return null;
		}

		final String name = getColumnString(cursor, ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY);
		final String telephoneNumber = getColumnString(cursor, ContactsContract.CommonDataKinds.Phone.NUMBER);
		cursor.close();

		return new ActivityResultContact(name, telephoneNumber);
	}

	private static String getColumnString(final Cursor cursor, final String columnName)
	{
		final int columnIndex = cursor.getColumnIndex(columnName);
		if (columnIndex == -1) {
			Lg.e("unknown columnName: ", columnName);
			return null;
		}

		return cursor.getString(columnIndex);
	}

	private void callContact(final ActivityResultContact contact)
	{
		if (contact == null) {
			new AlertDialog.Builder(requireContext())
					.setMessage(R.string.no_contact_permission_fragment_alert_contact_error)
					.create().show();
			return;
		}

		callContact(contact.name, contact.telephoneNumber);
	}

	private void callContact(final String name, final String telephoneNumber)
	{
		final String simlarId = SimlarNumber.createSimlarId(telephoneNumber);

		if (Util.isNullOrEmpty(simlarId)) {
			Lg.i("not calling contact because of invalid telephoneNumber=", new Lg.Anonymizer(telephoneNumber));
			new AlertDialog.Builder(requireContext())
					.setTitle(R.string.no_contact_permission_fragment_alert_no_simlarId_title)
					.setMessage(Util.fromHtml(String.format(getString(R.string.no_contact_permission_fragment_alert_no_simlarId_message), telephoneNumber)))
					.create().show();
			return;
		}

		Lg.i("checking status of simlarId=", new Lg.Anonymizer(simlarId));
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
				new AlertDialog.Builder(requireContext())
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
					new AlertDialog.Builder(requireContext())
							.setTitle(String.format(getString(R.string.no_contact_permission_fragment_alert_contact_not_registered_title), telephoneNumber))
							.setMessage(Util.fromHtml(String.format(getString(R.string.no_contact_permission_fragment_alert_contact_not_registered_message), name)))
							.create().show();
					return;
				}

				ContactsProvider.addContact(simlarId, name, telephoneNumber);

				CallActivity.createCallView(requireContext(), simlarId);
			}
		});
	}
}

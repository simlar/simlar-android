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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import org.simlar.ContactsProvider.FullContactData;
import org.simlar.ContactsProvider.FullContactsListener;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

public final class MainActivity extends android.support.v4.app.FragmentActivity
{
	static final String LOGTAG = MainActivity.class.getSimpleName();

	ContactsAdapter mAdapter = null;
	ContactsListFragment mContactList = null;

	final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorContacts();

	private final class SimlarServiceCommunicatorContacts extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorContacts()
		{
			super(LOGTAG);
		}

		@Override
		void onServiceFinishes()
		{
			MainActivity.this.finish();
		}
	}

	public final class ContactsListFragment extends android.support.v4.app.ListFragment
	{
		@Override
		public void onActivityCreated(final Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			setEmptyText(getString(R.string.main_activity_contactlist_no_contacts_found));
		}

		@Override
		public void onViewCreated(final View view, final Bundle savedInstanceState)
		{
			final ListView listView = getListView();

			if (listView == null) {
				Log.e(LOGTAG, "no list view");
				return;
			}

			listView.setDivider(null);
		}

		@Override
		public void onListItemClick(final ListView l, final View v, final int position, final long id)
		{
			final String simlarId = ((ContactsAdapter) getListAdapter()).getItem(position).simlarId;
			if (Util.isNullOrEmpty(simlarId)) {
				Log.e(LOGTAG, "onListItemClick: no simlarId found");
				return;
			}

			mCommunicator.getService().call(simlarId);
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		Log.i(LOGTAG, "onCreate " + savedInstanceState);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		FileHelper.init(this);

		mAdapter = ContactsAdapter.createContactsAdapter(this);

		final FragmentManager fm = getSupportFragmentManager();
		mContactList = (ContactsListFragment) fm.findFragmentById(android.R.id.content);
		if (mContactList == null) {
			mContactList = new ContactsListFragment();
			fm.beginTransaction().add(android.R.id.content, mContactList).commit();
		}
		mContactList.setListAdapter(mAdapter);

		Log.i(LOGTAG, "onCreate ended");
	}

	void loadContacts()
	{
		mContactList.setEmptyText(getString(R.string.main_activity_contactlist_loading_contacts));
		ContactsProvider.getContacts(this, new FullContactsListener() {
			@Override
			public void onGetContacts(Set<FullContactData> contacts)
			{
				mAdapter.clear();
				if (contacts == null) {
					mContactList.setEmptyText(getString(R.string.main_activity_contactlist_error_loading_contacts));
				} else {
					mAdapter.addAll(contacts);
					mContactList.setEmptyText(getString(R.string.main_activity_contactlist_no_contacts_found));
				}
			}
		});
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();

		mCommunicator.startServiceAndRegister(this, MainActivity.class);

		if (!PreferencesHelper.readPrefencesFromFile(this)) {
			Log.i(LOGTAG, "we are not registered yet");
			return;
		}

		if (mAdapter.isEmpty()) {
			loadContacts();
		}
	}

	@Override
	protected void onPause()
	{
		Log.i(LOGTAG, "onPause");

		mCommunicator.unregister(this);

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.action_upload_logfile:
			uploadLogFile();
			return true;
		case R.id.action_enable_linphone_debug_mode:
			enableLinphoneDebugMode();
			return true;
		case R.id.action_delete_account:
			deleteAccountAndQuit();
			return true;
		case R.id.action_show_about:
			show_about();
			return true;
		case R.id.action_quit:
			quit();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void uploadLogFile()
	{
		final String logFileName = "simlar_" + PreferencesHelper.getMySimlarIdOrEmptyString() + "_"
				+ (new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)).format(new Date()) + ".log";
		(new AlertDialog.Builder(this))
				.setTitle(R.string.main_activity_alert_upload_log_file_title)
				.setMessage(R.string.main_activity_alert_upload_log_file_text)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id)
					{
						(new UploadLogFile(MainActivity.this)).upload(logFileName);
					}
				})
				.create().show();
	}

	private void enableLinphoneDebugMode()
	{
		(new AlertDialog.Builder(this))
				.setTitle(R.string.main_activity_alert_enable_linphone_debug_mode_title)
				.setMessage(R.string.main_activity_alert_enable_linphone_debug_mode_text)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id)
					{
						LinphoneHandler.enableDebugMode(true);
					}
				})
				.create().show();
	}

	private void deleteAccountAndQuit()
	{
		PreferencesHelper.resetPreferencesFile(this);
		mCommunicator.getService().terminate();
	}

	private void show_about()
	{
		startActivity(new Intent(this, AboutActivity.class));
	}

	private void quit()
	{
		Log.i(LOGTAG, "quit");
		mCommunicator.getService().terminate();
		Log.i(LOGTAG, "quit ended");
	}
}

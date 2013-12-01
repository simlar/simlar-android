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

public class MainActivity extends android.support.v4.app.FragmentActivity
{
	static final String LOGTAG = MainActivity.class.getSimpleName();

	ContactsAdapter mAdapter = null;

	private SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorContacts();

	private class SimlarServiceCommunicatorContacts extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorContacts()
		{
			super(LOGTAG);
		}

		@Override
		void onBoundToSimlarService()
		{
			if (mAdapter == null) {
				Log.w(LOGTAG, "no contact adapter");
				return;
			}

			mAdapter.onSimlarStatusChanged();
		}

		@Override
		void onSimlarStatusChanged()
		{
			if (mAdapter == null) {
				Log.w(LOGTAG, "no contact adapter");
				return;
			}

			mAdapter.onSimlarStatusChanged();
		}

		@Override
		void onPresenceStateChanged(final String number, final boolean online)
		{
			if (mAdapter == null) {
				Log.w(LOGTAG, "no contact adapter");
				return;
			}

			mAdapter.onPresenceStateChanged(number, online);
		}

		@Override
		void onServiceFinishes()
		{
			MainActivity.this.finish();
		}
	}

	private class ContactsListFragment extends android.support.v4.app.ListFragment implements EmptyTextListener
	{
		public ContactsListFragment()
		{
		}

		public void setAdapter(ContactsAdapter adapter)
		{
			setListAdapter(adapter);
			adapter.setEmptyTextListener(this);
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			setEmptyText(getString(R.string.no_simlar_contacts_online));
		}

		@Override
		public void onListItemClick(ListView l, View v, int position, long id)
		{
			final String number = ((ContactsAdapter) getListAdapter()).getNumber(position);

			if (Util.isNullOrEmpty(number)) {
				Log.e(LOGTAG, "ContactsListFragment no number at position: " + number);
				return;
			}

			Log.i(LOGTAG, "starting CallActivity to call: " + number);
			MainActivity.this.startActivity(new Intent(MainActivity.this, CallActivity.class).putExtra(CallActivity.INTENT_EXTRA_SIMLAR_ID, number));
		}

		@Override
		public void onEmptyTextNeeded(final int textId)
		{
			setEmptyText(getString(textId));
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.i(LOGTAG, "onCreate " + savedInstanceState);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mAdapter = ContactsAdapter.createContactsAdapter(this, mCommunicator);

		final FragmentManager fm = getSupportFragmentManager();
		ContactsListFragment list = (ContactsListFragment) fm.findFragmentById(android.R.id.content);
		if (list == null) {
			list = new ContactsListFragment();
			fm.beginTransaction().add(android.R.id.content, list).commit();
		}
		list.setAdapter(mAdapter);

		Log.i(LOGTAG, "onCreate ended");
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();

		mCommunicator.startServiceAndRegister(this, MainActivity.class);

		Log.i(LOGTAG, "onResume ended");
	}

	@Override
	protected void onPause()
	{
		Log.i(LOGTAG, "onPause");

		mCommunicator.unregister(this);

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.action_settings:
			showSettings();
			return true;
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
				.setTitle(R.string.alert_title_upload_log_file)
				.setMessage(R.string.alert_text_upload_log_file)
				.setNegativeButton(R.string.alert_button_cancel, null)
				.setPositiveButton(R.string.alert_button_continue, new DialogInterface.OnClickListener() {
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
				.setTitle(R.string.alert_title_enable_linphone_debug_mode)
				.setMessage(R.string.alert_text_enable_linphone_debug_mode)
				.setNegativeButton(R.string.alert_button_cancel, null)
				.setPositiveButton(R.string.alert_button_continue, new DialogInterface.OnClickListener() {
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

	private void showSettings()
	{
		// TODO implement Settings
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

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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import org.simlar.ContactsProvider.FullContactData;
import org.simlar.ContactsProvider.FullContactsListener;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.MenuItem;

public final class MainActivity extends android.support.v4.app.FragmentActivity
{
	static final String LOGTAG = MainActivity.class.getSimpleName();

	ContactsAdapter mAdapter = null;
	ContactsListFragment mContactList = null;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		Lg.init(this);

		Lg.i(LOGTAG, "onCreate ", savedInstanceState);
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

		Lg.i(LOGTAG, "onCreate ended");
	}

	void loadContacts()
	{
		mContactList.setEmptyText(getString(R.string.main_activity_contactlist_loading_contacts));
		ContactsProvider.getContacts(this, new FullContactsListener() {
			@TargetApi(Build.VERSION_CODES.HONEYCOMB)
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

				GooglePlayServicesHelper.registerGcmIfNeeded(MainActivity.this);
			}
		});
	}

	private void startAccountCreation()
	{
		startActivity(new Intent(this, AgreeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
		finish();
	}

	@Override
	protected void onResume()
	{
		Lg.i(LOGTAG, "onResume");
		super.onResume();

		if (SimlarService.isRunning()) {
			final Class<? extends Activity> activity = SimlarService.getActivity();
			if (activity != this.getClass()) {
				Lg.i(LOGTAG, "as service is running => starting: ", activity.getSimpleName());
				startActivity(new Intent(this, activity));
				finish();
				return;
			}
		}

		if (!GooglePlayServicesHelper.checkPlayServices(this)) {
			return;
		}

		if (!PreferencesHelper.readPrefencesFromFile(this)) {
			Lg.i(LOGTAG, "as we are not registered yet => creating account");
			startAccountCreation();
			return;
		}

		if (mAdapter.isEmpty()) {
			loadContacts();
		}
	}

	@Override
	protected void onPause()
	{
		Lg.i(LOGTAG, "onPause");
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		updateMenu(Version.hasDebugTag(), R.id.action_delete_account, R.string.main_activity_menu_delete_account, Menu.NONE, menu);
		updateMenu(Version.hasDebugTag(), R.id.action_fake_telephone_book, R.string.main_activity_menu_fake_telephone_book, Menu.NONE, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.action_reload_contacts:
			reloadContacts();
			return true;
		case R.id.action_upload_logfile:
			uploadLogFile();
			return true;
		case R.id.action_enable_debug_mode:
			toggleDebugMode();
			return true;
		case R.id.action_delete_account:
			deleteAccountAndQuit();
			return true;
		case R.id.action_fake_telephone_book:
			fakeTelephoneBook();
			return true;
		case R.id.action_tell_a_friend:
			tellAFriend();
			return true;
		case R.id.action_show_about:
			showAbout();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private static void updateMenu(final boolean visible, final int itemResource, final int stringResource, final int order, final Menu menu)
	{
		if (visible) {
			if (menu.findItem(itemResource) == null) {
				menu.add(Menu.NONE, itemResource, order, stringResource);
			}
		} else {
			while (menu.findItem(itemResource) != null) {
				menu.removeItem(itemResource);
			}
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		menu.findItem(R.id.action_enable_debug_mode).setTitle(Lg.isDebugModeEnabled()
				? R.string.main_activity_menu_disable_debug_mode
				: R.string.main_activity_menu_enable_debug_mode);

		if (Version.hasDebugTag()) {
			menu.findItem(R.id.action_fake_telephone_book).setTitle(ContactsProvider.getFakeMode()
					? R.string.main_activity_menu_fake_telephone_book_disable
					: R.string.main_activity_menu_fake_telephone_book);
		}

		updateMenu(Lg.isDebugModeEnabled(), R.id.action_upload_logfile, R.string.main_activity_menu_upload_logfile, 3, menu);
		return true;
	}

	private void reloadContacts()
	{
		Lg.i(LOGTAG, "reloadContacts");
		ContactsProvider.clearCache();
		mAdapter.clear();
		loadContacts();
	}

	private void fakeTelephoneBook()
	{
		ContactsProvider.toggleFakeMode();
		reloadContacts();
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

	private void toggleDebugMode()
	{
		if (Lg.isDebugModeEnabled()) {
			Lg.saveDebugMode(this, false);
			return;
		}

		(new AlertDialog.Builder(this))
				.setTitle(R.string.main_activity_alert_enable_linphone_debug_mode_title)
				.setMessage(R.string.main_activity_alert_enable_linphone_debug_mode_text)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id)
					{
						Lg.saveDebugMode(MainActivity.this, true);
					}
				})
				.create().show();
	}

	private void deleteAccountAndQuit()
	{
		PreferencesHelper.resetPreferencesFile(this);
		finish();
	}

	private void tellAFriend()
	{
		final Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("text/plain");
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.main_activity_tell_a_friend_subject));
		sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.main_activity_tell_a_friend_text));
		startActivity(Intent.createChooser(sendIntent, getString(R.string.main_activity_tell_a_friend_chooser_title)));
	}

	private void showAbout()
	{
		startActivity(new Intent(this, AboutActivity.class));
	}
}

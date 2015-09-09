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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import org.simlar.R;
import org.simlar.contactsprovider.ContactsProvider;
import org.simlar.contactsprovider.ContactsProvider.ContactDataComplete;
import org.simlar.contactsprovider.ContactsProvider.FullContactsListener;
import org.simlar.helper.ContactsAdapter;
import org.simlar.helper.FileHelper;
import org.simlar.helper.FlavourHelper;
import org.simlar.helper.GooglePlayServicesHelper;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.Version;
import org.simlar.https.UploadLogFile;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarService;
import org.simlar.service.SimlarServiceCommunicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends AppCompatActivity
{
	private ContactsAdapter mAdapter = null;
	private ContactsListFragment mContactList = null;

	private final SimlarServiceCommunicator mCommunicator = FlavourHelper.isGcmEnabled() ? null : new SimlarServiceCommunicatorContacts();

	private final class SimlarServiceCommunicatorContacts extends SimlarServiceCommunicator
	{
		@Override
		public void onServiceFinishes()
		{
			MainActivity.this.finish();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		Lg.init(this, PreferencesHelper.readFromFileDebugMode(this));

		Lg.i("onCreate ", savedInstanceState);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		FileHelper.init(this);

		mAdapter = new ContactsAdapter(this);

		final FragmentManager fm = getSupportFragmentManager();
		mContactList = (ContactsListFragment) fm.findFragmentById(android.R.id.content);
		if (mContactList == null) {
			mContactList = new ContactsListFragment();
			fm.beginTransaction().add(android.R.id.content, mContactList).commit();
		}
		mContactList.setListAdapter(mAdapter);

		Lg.i("onCreate ended");
	}

	private void loadContacts()
	{
		mContactList.setEmptyText(getString(R.string.main_activity_contact_list_loading_contacts));
		ContactsProvider.getContacts(this, new FullContactsListener() {
			@Override
			public void onGetContacts(Set<ContactDataComplete> contacts)
			{
				mAdapter.clear();
				if (contacts == null) {
					mContactList.setEmptyText(getString(R.string.main_activity_contact_list_error_loading_contacts));
				} else {
					mAdapter.addAllContacts(contacts);
					mContactList.setEmptyText(getString(R.string.main_activity_contact_list_no_contacts_found));
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
		Lg.i("onResume");
		super.onResume();

		if (FlavourHelper.isGcmEnabled() && SimlarService.isRunning()) {
			final Class<? extends Activity> activity = SimlarService.getActivity();
			if (activity != this.getClass()) {
				Lg.i("as service is running => starting: ", activity.getSimpleName());
				startActivity(new Intent(this, activity));
				finish();
				return;
			}
		}

		//noinspection ConstantConditions /// needed in alwaysOnline flavour
		if (!GooglePlayServicesHelper.checkPlayServices(this)) {
			return;
		}

		if (!PreferencesHelper.readPreferencesFromFile(this)) {
			Lg.i("as we are not registered yet => creating account");
			startAccountCreation();
			return;
		}

		if (mCommunicator != null) {
			mCommunicator.startServiceAndRegister(this, MainActivity.class, null);
		}

		if (mAdapter.isEmpty()) {
			loadContacts();
		}
	}

	@Override
	protected void onPause()
	{
		Lg.i("onPause");

		if (mCommunicator != null) {
			mCommunicator.unregister();
		}

		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		updateMenu(Version.showDeveloperMenu(), R.id.action_delete_account, R.string.main_activity_menu_delete_account, Menu.NONE, menu);
		updateMenu(Version.showDeveloperMenu(), R.id.action_fake_telephone_book, R.string.main_activity_menu_fake_telephone_book, Menu.NONE, menu);
		updateMenu(!FlavourHelper.isGcmEnabled(), R.id.action_quit, R.string.main_activity_menu_quit, Menu.NONE, menu);
		return super.onCreateOptionsMenu(menu);
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
		case R.id.action_quit:
			quit();
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

		if (Version.showDeveloperMenu()) {
			menu.findItem(R.id.action_fake_telephone_book).setTitle(ContactsProvider.getFakeMode()
					? R.string.main_activity_menu_fake_telephone_book_disable
					: R.string.main_activity_menu_fake_telephone_book);
		}

		updateMenu(Lg.isDebugModeEnabled(), R.id.action_upload_logfile, R.string.main_activity_menu_upload_logfile, 3, menu);
		return true;
	}

	private void reloadContacts()
	{
		Lg.i("reloadContacts");
		if (ContactsProvider.clearCache()) {
			mAdapter.clear();
			loadContacts();
		}
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
					public void onClick(final DialogInterface dialog, final int id)
					{
						(new UploadLogFile(MainActivity.this)).upload(logFileName);
					}
				})
				.create().show();
	}

	private void toggleDebugMode()
	{
		if (Lg.isDebugModeEnabled()) {
			Lg.setDebugMode(false);
			PreferencesHelper.saveToFileDebugMode(this, false);
			return;
		}

		(new AlertDialog.Builder(this))
				.setTitle(R.string.main_activity_alert_enable_linphone_debug_mode_title)
				.setMessage(R.string.main_activity_alert_enable_linphone_debug_mode_text)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int id)
					{
						Lg.setDebugMode(true);
						PreferencesHelper.saveToFileDebugMode(MainActivity.this, true);
					}
				})
				.create().show();
	}

	private void deleteAccountAndQuit()
	{
		PreferencesHelper.resetPreferencesFile(this);
		if (mCommunicator == null) {
			finish();
		} else {
			mCommunicator.getService().terminate();
		}
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

	private void quit()
	{
		Lg.i("quit");
		if (mCommunicator == null) {
			finish();
		} else {
			(new AlertDialog.Builder(this))
					.setTitle(R.string.main_activity_alert_quit_simlar_title)
					.setMessage(R.string.main_activity_alert_quit_simlar_text)
					.setNegativeButton(R.string.button_cancel, null)
					.setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog, final int id)
						{
							Lg.i("user decided to terminate simlar");
							mCommunicator.getService().terminate();
						}
					})
					.create().show();
		}
	}
}

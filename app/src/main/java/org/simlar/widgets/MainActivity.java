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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.simlar.R;
import org.simlar.contactsprovider.ContactsProvider;
import org.simlar.helper.CreateAccountStatus;
import org.simlar.helper.FlavourHelper;
import org.simlar.helper.GooglePlayServicesHelper;
import org.simlar.helper.PermissionsHelper;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.RingtoneHelper;
import org.simlar.helper.Version;
import org.simlar.https.UploadLogFile;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarService;
import org.simlar.service.SimlarServiceCommunicator;
import org.simlar.utils.Util;

public final class MainActivity extends AppCompatActivity implements NoContactPermissionFragment.Listener
{
	private ContactsAdapter mAdapter = null;
	private ContactsListFragment mContactList = null;
	private NoContactPermissionFragment mNoContactPermissionFragment = null;

	private final SimlarServiceCommunicator mCommunicator = FlavourHelper.isGcmEnabled() ? null : new SimlarServiceCommunicatorContacts();

	private final ActivityResultLauncher<String[]> mRequestPermissionsLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), p -> {});

	private final class SimlarServiceCommunicatorContacts extends SimlarServiceCommunicator
	{
		@Override
		public void onServiceFinishes()
		{
			finish();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Lg.i("onCreate ", savedInstanceState);

		setContentView(R.layout.activity_main);

		mAdapter = new ContactsAdapter(this);

		final FragmentManager fm = getSupportFragmentManager();
		mContactList = (ContactsListFragment) fm.findFragmentById(R.id.contactsListFragment);
		if (mContactList == null) {
			mContactList = new ContactsListFragment();
			fm.beginTransaction().add(R.id.contactsListFragment, mContactList).commit();
		}
		mContactList.setListAdapter(mAdapter);

		mNoContactPermissionFragment = (NoContactPermissionFragment) fm.findFragmentById(R.id.noContactPermissionFragment);
		if (mNoContactPermissionFragment == null) {
			mNoContactPermissionFragment = new NoContactPermissionFragment();
			fm.beginTransaction().add(R.id.noContactPermissionFragment, mNoContactPermissionFragment).commit();
		}

		showNoContactPermissionFragment(false);

		if (!PreferencesHelper.readPreferencesFromFile(this)) {
			Lg.i("as we are not registered yet => creating account");
			startAccountCreation();
			return;
		}

		GooglePlayServicesHelper.checkPlayServices(this);

		PermissionsHelper.checkAndRequestNotificationPolicyAccess(this);

		Lg.i("onCreate ended");
	}

	private void showNoContactPermissionFragment(final boolean visible)
	{
		Lg.i("showNoContactPermissionFragment visible=", visible);
		setFragmentVisible(mNoContactPermissionFragment, visible);
		setFragmentVisible(mContactList, !visible);
	}

	private static void setFragmentVisible(final Fragment fragment, final boolean visible)
	{
		if (fragment == null) {
			return;
		}

		final View view = fragment.getView();
		if (view == null) {
			return;
		}

		view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
	}

	@Override
	public void onContactPermissionGranted()
	{
		loadContacts();
	}

	private void loadContacts()
	{
		showNoContactPermissionFragment(false);
		mContactList.setEmptyText(getString(R.string.main_activity_contact_list_loading_contacts));
		ContactsProvider.getContacts(this, (contacts, error) -> {
			Lg.i("onGetContacts error=", error);

			if (isFinishing()) {
				Lg.i("onGetContacts MainActivity is finishing");
				return;
			}

			switch (error) {
			case NONE:
				mAdapter.setContacts(contacts);
				mContactList.setEmptyText(getString(R.string.main_activity_contact_list_no_contacts_found));
				break;
			case BUG:
				mAdapter.clear();
				mContactList.setEmptyText(getString(R.string.main_activity_contact_list_error_loading_contacts));
				break;
			case NO_INTERNET_CONNECTION:
				mAdapter.clear();
				mContactList.setEmptyText(getString(R.string.main_activity_contact_list_error_loading_contacts_no_internet));
				break;
			case PERMISSION_DENIED:
				showNoContactPermissionFragment(true);
				break;
			}

			GooglePlayServicesHelper.refreshTokenOnServer();
		});
	}

	private void startAccountCreation()
	{
		final Class<?> activity =
				PreferencesHelper.getCreateAccountStatus() == CreateAccountStatus.WAITING_FOR_SMS
						? VerifyNumberActivity.class
						: AgreeActivity.class;

		startActivity(new Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
		finish();
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Lg.i("onStart");

		if (FlavourHelper.isGcmEnabled() && SimlarService.isRunning()) {
			final Class<? extends AppCompatActivity> activity = SimlarService.getActivity();
			if (!getClass().equals(activity)) {
				Lg.i("as service is running => starting: ", activity.getSimpleName());
				startActivity(new Intent(this, activity));
				finish();
				return;
			}
		}

		if (mCommunicator != null) {
			mCommunicator.startServiceAndRegister(this, MainActivity.class, null);
		}

		PermissionsHelper.showRationalForMissingMajorPermissions(
				this,
				PermissionsHelper.needsExternalStoragePermission(this, RingtoneHelper.getDefaultRingtone()),
				types -> mRequestPermissionsLauncher.launch(types.toArray(Util.EMPTY_STRING_ARRAY)));

		if (mAdapter.isEmpty()) {
			loadContacts();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Lg.i("onResume");
	}

	@Override
	protected void onPause()
	{
		Lg.i("onPause");
		super.onPause();
	}

	@Override
	protected void onStop()
	{
		Lg.i("onStop");

		if (mCommunicator != null) {
			mCommunicator.unregister();
		}

		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		updateMenu(Version.showDeveloperMenu(), R.id.action_delete_account, R.string.main_activity_menu_delete_account, Menu.NONE, menu);
		updateMenu(Version.showDeveloperMenu(), R.id.action_fake_telephone_book, R.string.main_activity_menu_fake_telephone_book, Menu.NONE, menu);
		updateMenu(Version.showDeveloperMenu() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N, R.id.action_notification_settings, R.string.main_activity_menu_notification_settings, Menu.NONE, menu);
		updateMenu(Version.showDeveloperMenu(), R.id.action_app_settings, R.string.main_activity_menu_app_settings, Menu.NONE, menu);
		updateMenu(!FlavourHelper.isGcmEnabled(), R.id.action_quit, R.string.main_activity_menu_quit, Menu.NONE, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		// using switch is not possible as R.* is not final since android gradle plugin 5.0
		final int itemId = item.getItemId();
		if (itemId == R.id.action_reload_contacts) {
			reloadContacts();
			return true;
		}

		if (itemId == R.id.action_upload_logfile) {
			uploadLogFile();
			return true;
		}

		if (itemId == R.id.action_enable_debug_mode) {
			toggleDebugMode();
			return true;
		}

		if (itemId == R.id.action_delete_account) {
			deleteAccountAndQuit();
			return true;
		}

		if (itemId == R.id.action_fake_telephone_book) {
			fakeTelephoneBook();
			return true;
		}

		if (itemId == R.id.action_notification_settings) {
			PermissionsHelper.openNotificationPolicyAccessSettings(this);
			return true;
		}

		if (itemId == R.id.action_app_settings) {
			PermissionsHelper.openAppSettings(this);
			return true;
		}

		if (itemId == R.id.action_tell_a_friend) {
			tellAFriend();
			return true;
		}

		if (itemId == R.id.action_show_about) {
			showAbout();
			return true;
		}

		if (itemId == R.id.action_quit) {
			quit();
			return true;
		}

		return super.onOptionsItemSelected(item);
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
		final String logFileName = "simlar_" + PreferencesHelper.getMySimlarIdOrEmptyString() + '_'
				+ new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(new Date()) + ".log";
		new AlertDialog.Builder(this)
				.setTitle(R.string.main_activity_alert_upload_log_file_title)
				.setMessage(R.string.main_activity_alert_upload_log_file_text)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_continue, (dialog, id) -> new UploadLogFile(this).upload(logFileName))
				.create().show();
	}

	private void toggleDebugMode()
	{
		if (Lg.isDebugModeEnabled()) {
			Lg.setDebugMode(false);
			PreferencesHelper.saveToFileDebugMode(this, false);
			return;
		}

		new AlertDialog.Builder(this)
				.setTitle(R.string.main_activity_alert_enable_linphone_debug_mode_title)
				.setMessage(R.string.main_activity_alert_enable_linphone_debug_mode_text)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_continue, (dialog, id) -> {
					Lg.setDebugMode(true);
					PreferencesHelper.saveToFileDebugMode(this, true);
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
			new AlertDialog.Builder(this)
					.setTitle(R.string.main_activity_alert_quit_simlar_title)
					.setMessage(R.string.main_activity_alert_quit_simlar_text)
					.setNegativeButton(R.string.button_cancel, null)
					.setPositiveButton(R.string.button_continue, (dialog, id) -> {
						Lg.i("user decided to terminate simlar");
						mCommunicator.getService().terminate();
					})
					.create().show();
		}
	}
}

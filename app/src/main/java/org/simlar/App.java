/*
 * Copyright (C) 2013 - 2015 The Simlar Authors.
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

package org.simlar;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Build;

import org.simlar.helper.FileHelper;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.Version;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarNotificationChannel;
import org.simlar.service.ServiceActivities;
import org.simlar.service.SimlarService;
import org.simlar.widgets.CallActivity;
import org.simlar.widgets.MainActivity;
import org.simlar.widgets.RingingActivity;

@SuppressWarnings("WeakerAccess") // class needs to be public
public final class App extends Application
{
	@Override
	public void onCreate()
	{
		super.onCreate();

		PreferencesHelper.readPreferencesFromFile(this);
		Lg.init(PreferencesHelper.readFromFileDebugMode(this));
		FileHelper.init(this);
		SimlarService.initActivities(new ServiceActivities(MainActivity.class, RingingActivity.class, CallActivity.class));
		SimlarNotificationChannel.createNotificationChannels(this);

		Lg.i("simlar started with version=", Version.getVersionName(this),
				" on device: ", Build.MANUFACTURER, " ", Build.MODEL, " (", Build.DEVICE, ") with android version=", Build.VERSION.RELEASE);
	}

	@Override
	public void onTerminate()
	{
		Lg.i("onTerminate");
		super.onTerminate();
	}

	@Override
	public void onConfigurationChanged(final Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		Lg.i("onConfigurationChanged: ", newConfig);
	}

	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		Lg.w("onLowMemory");
	}
}

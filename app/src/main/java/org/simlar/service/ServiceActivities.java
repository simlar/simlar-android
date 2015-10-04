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

package org.simlar.service;

import android.app.Activity;

public final class ServiceActivities
{
	private final Class<? extends Activity> mMainActivity;
	private final Class<? extends Activity> mRingingActivity;
	private final Class<? extends Activity> mCallActivity;

	public ServiceActivities(final Class<? extends Activity> mainActivity, final Class<? extends Activity> ringingActivity, final Class<? extends Activity> callActivity)
	{
		mMainActivity = mainActivity;
		mRingingActivity = ringingActivity;
		mCallActivity = callActivity;
	}

	public Class<? extends Activity> getMainActivity()
	{
		return mMainActivity;
	}

	public Class<? extends Activity> getRingingActivity()
	{
		return mRingingActivity;
	}

	public Class<? extends Activity> getCallActivity()
	{
		return mCallActivity;
	}
}

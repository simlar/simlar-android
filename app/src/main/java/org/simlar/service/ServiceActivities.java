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

import androidx.appcompat.app.AppCompatActivity;

public final class ServiceActivities
{
	private final Class<? extends AppCompatActivity> mMainActivity;
	private final Class<? extends AppCompatActivity> mRingingActivity;
	private final Class<? extends AppCompatActivity> mCallActivity;

	public ServiceActivities(final Class<? extends AppCompatActivity> mainActivity, final Class<? extends AppCompatActivity> ringingActivity, final Class<? extends AppCompatActivity> callActivity)
	{
		mMainActivity = mainActivity;
		mRingingActivity = ringingActivity;
		mCallActivity = callActivity;
	}

	public Class<? extends AppCompatActivity> getMainActivity()
	{
		return mMainActivity;
	}

	public Class<? extends AppCompatActivity> getRingingActivity()
	{
		return mRingingActivity;
	}

	public Class<? extends AppCompatActivity> getCallActivity()
	{
		return mCallActivity;
	}
}

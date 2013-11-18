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

import org.simlar.SimlarService.SimlarServiceBinder;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class SimlarServiceCommunicator
{
	private static final String DEFAULT_LOGTAG = "SimlarServiceCommunicator";
	final String mLogtag;
	SimlarService mService = null;
	Class<?> mActivity = null;

	public SimlarServiceCommunicator(final String logtag)
	{
		if (Util.isNullOrEmpty(logtag)) {
			mLogtag = DEFAULT_LOGTAG;
		} else {
			mLogtag = logtag;
		}
	}

	private ServiceConnection mConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName className, IBinder binder)
		{
			Log.i(mLogtag, "onServiceConnected");
			mService = ((SimlarServiceBinder) binder).getService();
			if (mService == null) {
				Log.e(mLogtag, "failed to bind to service");
				return;
			}
			if (mActivity == null) {
				Log.e(mLogtag, "no activity set");
				return;
			}
			mService.registerActivityToNotification(mActivity);
			onBoundToSimlarService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0)
		{
			Log.i(mLogtag, "onServiceDisconnected");
			mService = null;
		}
	};

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent == null) {
				Log.e(mLogtag, "Error in onReceive: no intent");
				return;
			}

			SimlarServiceBroadcast fsb = (SimlarServiceBroadcast) intent.getSerializableExtra(SimlarServiceBroadcast.INTENT_EXTRA);
			if (fsb == null) {
				Log.e(mLogtag, "Error in onReceive: no SimlarServiceBroadcast");
				return;
			}

			// NOTE: the app crashes if the cast fails but I want it this way
			switch (fsb.type) {
			case SIMLAR_STATUS: {
				onSimlarStatusChanged();
				return;
			}
			case PRESENCE_STATE: {
				SimlarServiceBroadcast.PresenceStateChanged c = (SimlarServiceBroadcast.PresenceStateChanged) fsb.parameters;
				onPresenceStateChanged(c.number, c.online);
				return;
			}
			case SIMLAR_CALL_STATE: {
				onSimlarCallStateChanged();
				return;
			}
			case SERVICE_FINISHES: {
				onServiceFinishes();
				return;
			}
			case TEST_REGISTRATION_FAILED: {
				onTestRegistrationFailed();
				return;
			}
			case TEST_REGISTRATION_SUCCESS: {
				onTestRegistrationSuccess();
				return;
			}
			default:
				Log.e(mLogtag, "Error in onReceive: unkown type");
				return;
			}
		}
	};

	public void register(final Context context, final Class<?> activity)
	{
		startServiceAndRegister(context, activity, true);
	}

	public void startServiceAndRegister(final Context context, final Class<?> activity)
	{
		startServiceAndRegister(context, activity, false);
	}

	private void startServiceAndRegister(final Context context, final Class<?> activity, boolean onlyRegister)
	{
		mActivity = activity;
		final Intent intent = new Intent(context, SimlarService.class);
		if (!onlyRegister) {
			context.startService(intent);
		}
		context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver, new IntentFilter(SimlarServiceBroadcast.BROADCAST_NAME));
	}

	public void unregister(final Context context)
	{
		LocalBroadcastManager.getInstance(context).unregisterReceiver(mReceiver);
		if (mService != null) {
			context.unbindService(mConnection);
		}
	}

	void onBoundToSimlarService()
	{
	}

	void onSimlarStatusChanged()
	{
	}

	@SuppressWarnings("unused")
	void onPresenceStateChanged(final String number, final boolean online)
	{
	}

	void onSimlarCallStateChanged()
	{
	}

	void onServiceFinishes()
	{
	}

	void onTestRegistrationFailed()
	{
	}

	void onTestRegistrationSuccess()
	{
	}

	public SimlarService getService()
	{
		return mService;
	}
}

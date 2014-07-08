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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

public class SimlarServiceCommunicator
{
	private static final String DEFAULT_LOGTAG = "SimlarServiceCommunicator";
	final String mLogtag;
	SimlarService mService = null;
	Class<? extends Activity> mActivity = null;
	private final ServiceConnection mConnection = new SimlarServiceConnection();
	private final BroadcastReceiver mReceiver = new SimlarServiceReceiver();

	private final class SimlarServiceConnection implements ServiceConnection
	{
		public SimlarServiceConnection()
		{
			super();
		}

		@Override
		public void onServiceConnected(final ComponentName className, final IBinder binder)
		{
			Lg.i(mLogtag, "onServiceConnected");
			mService = ((SimlarServiceBinder) binder).getService();
			if (mService == null) {
				Lg.e(mLogtag, "failed to bind to service");
				return;
			}
			if (mActivity == null) {
				Lg.e(mLogtag, "no activity set");
				return;
			}
			mService.registerActivityToNotification(mActivity);
			onBoundToSimlarService();
		}

		@Override
		public void onServiceDisconnected(final ComponentName arg0)
		{
			Lg.i(mLogtag, "onServiceDisconnected");
			mService = null;
		}
	}

	private final class SimlarServiceReceiver extends BroadcastReceiver
	{
		public SimlarServiceReceiver()
		{
			super();
		}

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			if (intent == null) {
				Lg.e(mLogtag, "Error in onReceive: no intent");
				return;
			}

			final SimlarServiceBroadcast fsb = (SimlarServiceBroadcast) intent.getSerializableExtra(SimlarServiceBroadcast.INTENT_EXTRA);
			if (fsb == null) {
				Lg.e(mLogtag, "Error in onReceive: no SimlarServiceBroadcast");
				return;
			}

			if (mService == null) {
				Lg.d(mLogtag, "skip sending SimlarServiceBroadcast with type=", fsb.type, " because no service bound");
				return;
			}

			// NOTE: the app crashes if the cast fails but I want it this way
			switch (fsb.type) {
			case SIMLAR_STATUS: {
				onSimlarStatusChanged();
				return;
			}
			case SIMLAR_CALL_STATE: {
				onSimlarCallStateChanged();
				return;
			}
			case CALL_CONNECTION_DETAILS: {
				onCallConnectionDetailsChanged();
				return;
			}
			case SERVICE_FINISHES: {
				onServiceFinishes();
				return;
			}
			default:
				Lg.e(mLogtag, "Error in onReceive: unknown type");
				return;
			}
		}
	}

	public SimlarServiceCommunicator(final String logtag)
	{
		if (Util.isNullOrEmpty(logtag)) {
			mLogtag = DEFAULT_LOGTAG;
		} else {
			mLogtag = logtag;
		}
	}

	public void register(final Context context, final Class<? extends Activity> activity)
	{
		startServiceAndRegister(context, activity, true, null);
	}

	public void startServiceAndRegister(final Context context, final Class<? extends Activity> activity, final String simlarId)
	{
		startServiceAndRegister(context, activity, false, simlarId);
	}

	private void startServiceAndRegister(final Context context, final Class<? extends Activity> activity, final boolean onlyRegister,
			final String simlarId)
	{
		mActivity = activity;
		final Intent intent = new Intent(context, SimlarService.class);
		if (!onlyRegister) {
			if (!Util.isNullOrEmpty(simlarId)) {
				intent.putExtra(SimlarService.INTENT_EXTRA_SIMLAR_ID, simlarId);
			}
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

	void onSimlarCallStateChanged()
	{
	}

	void onCallConnectionDetailsChanged()
	{
	}

	void onServiceFinishes()
	{
	}

	public SimlarService getService()
	{
		return mService;
	}
}

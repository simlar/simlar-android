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

package org.simlar.service;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import org.simlar.helper.VideoState;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarService.SimlarServiceBinder;
import org.simlar.utils.Util;

public class SimlarServiceCommunicator
{
	private SimlarService mService = null;
	private Class<? extends Activity> mActivity = null;
	private final ServiceConnection mConnection = new SimlarServiceConnection();
	private final BroadcastReceiver mReceiver = new SimlarServiceReceiver();
	private Context mContext = null;

	private final class SimlarServiceConnection implements ServiceConnection
	{
		@Override
		public void onServiceConnected(final ComponentName className, final IBinder binder)
		{
			Lg.i("onServiceConnected");
			mService = ((SimlarServiceBinder) binder).getService();
			if (mActivity == null) {
				Lg.e("no activity set");
				return;
			}
			mService.registerActivityToNotification(mActivity);
			onBoundToSimlarService();
		}

		@Override
		public void onServiceDisconnected(final ComponentName arg0)
		{
			Lg.i("onServiceDisconnected");
			mService = null;
		}
	}

	private final class SimlarServiceReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			if (intent == null) {
				Lg.e("Error in onReceive: no intent");
				return;
			}

			final SimlarServiceBroadcast fsb = (SimlarServiceBroadcast) intent.getSerializableExtra(SimlarServiceBroadcast.INTENT_EXTRA);
			if (fsb == null) {
				Lg.e("Error in onReceive: no SimlarServiceBroadcast");
				return;
			}

			if (mService == null) {
				Lg.v("skip sending SimlarServiceBroadcast with type=", fsb.getType(), " because no service bound");
				return;
			}

			// NOTE: the app crashes if the cast fails but I want it this way
			switch (fsb.getType()) {
			case SIMLAR_STATUS:
				onSimlarStatusChanged();
				return;
			case SIMLAR_CALL_STATE:
				onSimlarCallStateChanged();
				return;
			case CALL_CONNECTION_DETAILS:
				onCallConnectionDetailsChanged();
				return;
			case VIDEO_STATE:
				onVideoStateChanged(((SimlarServiceBroadcast.VideoStateChanged) fsb.getParameters()).videoState);
				return;
			case SERVICE_FINISHES:
				onServiceFinishes();
				unregister();
				return;
			default:
				Lg.e("Error in onReceive: unknown type");
			}
		}
	}

	public final boolean register(final Context context, final Class<? extends Activity> activity)
	{
		if (!SimlarService.isRunning()) {
			return false;
		}

		startServiceAndRegister(context, activity, true, null);
		return true;
	}

	public final void startServiceAndRegister(final Context context, final Class<? extends Activity> activity, final String simlarId)
	{
		startServiceAndRegister(context, activity, false, simlarId);
	}

	private void startServiceAndRegister(final Context context, final Class<? extends Activity> activity, final boolean onlyRegister,
	                                     final String simlarId)
	{
		mContext = context;
		mActivity = activity;
		final Intent intent = new Intent(context, SimlarService.class);
		if (!onlyRegister) {
			if (!Util.isNullOrEmpty(simlarId)) {
				intent.putExtra(SimlarService.INTENT_EXTRA_SIMLAR_ID, simlarId);
			}

			SimlarService.startService(context, intent);
		}
		context.bindService(intent, mConnection, 0);
		LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver, new IntentFilter(SimlarServiceBroadcast.BROADCAST_NAME));
	}

	public final void unregister()
	{
		if (mContext == null) {
			Lg.i("unregister skipped: no context");
			return;
		}

		LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
		if (mService != null && SimlarService.isRunning()) {
			mContext.unbindService(mConnection);
		}

		mContext = null;
	}

	protected void onBoundToSimlarService()
	{
	}

	protected void onSimlarStatusChanged()
	{
	}

	protected void onSimlarCallStateChanged()
	{
	}

	protected void onCallConnectionDetailsChanged()
	{
	}

	protected void onVideoStateChanged(final VideoState videoState)
	{
	}

	protected void onServiceFinishes()
	{
	}

	public final SimlarService getService()
	{
		return mService;
	}
}

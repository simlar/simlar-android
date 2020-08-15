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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;

import org.simlar.logging.Lg;
import org.simlar.utils.Util;

final class VibratorManager
{
	public static final long VIBRATE_LENGTH = 1000; // ms
	public static final long VIBRATE_PAUSE = 1000; // ms

	private final Context mContext;
	private boolean mHasOnGoingAlarm = false;
	private VibratorManagerImpl mImpl = null;
	private final RingerModeReceiver mRingerModeReceiver = new RingerModeReceiver();

	private final class RingerModeReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			onRingerModeChanged();
		}
	}

	private final class VibratorManagerImpl
	{
		private final Handler mHandler;
		private final Vibrator mVibrator;

		VibratorManagerImpl()
		{
			mHandler = new Handler(Looper.getMainLooper());
			mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
		}

		void startVibration()
		{
			Lg.i("vibrate");
			mVibrator.vibrate(VIBRATE_LENGTH);

			mHandler.postDelayed(this::startVibration, VIBRATE_LENGTH + VIBRATE_PAUSE);
		}

		void stopVibration()
		{
			mHandler.removeCallbacksAndMessages(null);
			mVibrator.cancel();
		}

		boolean hasVibrator()
		{
			return mVibrator != null;
		}
	}

	VibratorManager(final Context context)
	{
		mContext = context;
	}

	private boolean shouldVibrate()
	{
		return ((AudioManager) Util.getSystemService(mContext, Context.AUDIO_SERVICE)).getRingerMode() != AudioManager.RINGER_MODE_SILENT;
	}

	public void start()
	{
		if (mHasOnGoingAlarm) {
			return;
		}

		mHasOnGoingAlarm = true;

		final IntentFilter filter = new IntentFilter();
		filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
		mContext.registerReceiver(mRingerModeReceiver, filter);

		if (!shouldVibrate()) {
			Lg.i("VibratorManager: vibration disabled at the moment");
			return;
		}

		startVibrate();
	}

	private void startVibrate()
	{
		if (mImpl != null) {
			Lg.i("already vibrating");
			return;
		}

		mImpl = new VibratorManagerImpl();

		if (!mImpl.hasVibrator()) {
			Lg.i("VibratorManager: no vibrator");
			mImpl = null;
			return;
		}

		mImpl.startVibration();
	}

	public void stop()
	{
		if (!mHasOnGoingAlarm) {
			return;
		}

		mHasOnGoingAlarm = false;

		mContext.unregisterReceiver(mRingerModeReceiver);

		stopVibrate();
	}

	private void stopVibrate()
	{
		if (mImpl == null) {
			Lg.i("not vibrating");
			return;
		}

		mImpl.stopVibration();
		mImpl = null;

		Lg.i("stopped");
	}

	private void onRingerModeChanged()
	{
		Lg.i("onRingerModeChanged");

		if (!mHasOnGoingAlarm) {
			return;
		}

		if (shouldVibrate()) {
			startVibrate();
		} else {
			stopVibrate();
		}
	}
}

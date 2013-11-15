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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;

class VibratorThread
{
	static final String LOGTAG = VibratorThread.class.getSimpleName();
	public static final long VIBRATE_LENGTH = 1000; // ms
	public static final long VIBRATE_PAUSE = 1000; // ms

	Context mContext = null;
	private boolean mHasOnGoingAlarm = false;
	private VibratorThreadImpl mThread = null;
	private RingerModeReceiver mRingerModeReceiver = new RingerModeReceiver();

	private class RingerModeReceiver extends BroadcastReceiver
	{
		public RingerModeReceiver()
		{
			super();
		}

		@Override
		public void onReceive(Context context, Intent intent)
		{
			VibratorThread.this.onRingerModeChanged();
		}
	}

	private class VibratorThreadImpl extends Thread
	{
		private Handler mHandler = null;

		// should only be accessed within thread
		Vibrator mVibrator = null;

		public VibratorThreadImpl()
		{
			super();
			mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
		}

		@Override
		public void run()
		{
			Log.i(LOGTAG, "started");
			Looper.prepare();
			mHandler = new Handler();
			startVibration(0);
			Looper.loop();
		}

		void startVibration(final long delayMillis)
		{
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run()
				{
					Log.i(LOGTAG, "vibrate");
					mVibrator.vibrate(VIBRATE_LENGTH);
					startVibration(VIBRATE_LENGTH + VIBRATE_PAUSE);
				}
			}, delayMillis);
		}

		public void stopVibration()
		{
			mHandler.removeCallbacksAndMessages(null);

			mHandler.post(new Runnable() {
				@Override
				public void run()
				{
					mVibrator.cancel();
					Looper.myLooper().quit();
					Log.i(LOGTAG, "vibration stopped");
				}
			});
		}

		public boolean hasVibrator()
		{
			return mVibrator != null && mVibrator.hasVibrator();
		}
	}

	public VibratorThread(final Context context)
	{
		mContext = context;
	}

	private boolean shouldVibrate()
	{
		return ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).getRingerMode() != AudioManager.RINGER_MODE_SILENT;
	}

	public void start()
	{
		if (mHasOnGoingAlarm) {
			return;
		}

		mHasOnGoingAlarm = true;

		IntentFilter filter = new IntentFilter();
		filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
		mContext.registerReceiver(mRingerModeReceiver, filter);

		if (!shouldVibrate()) {
			Log.i(LOGTAG, "VibratorThread: vibration disabled at the moment");
			return;
		}

		startVibrate();
	}

	private void startVibrate()
	{
		if (mThread != null) {
			Log.i(LOGTAG, "already vibrating");
			return;
		}

		mThread = new VibratorThreadImpl();

		if (!mThread.hasVibrator()) {
			Log.i(LOGTAG, "VibratorThread: no vibrator");
			mThread = null;
			return;
		}

		mThread.start();
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
		if (mThread == null) {
			Log.i(LOGTAG, "not vibrating");
			return;
		}

		mThread.stopVibration();

		try {
			mThread.join(300);
		} catch (InterruptedException e) {
			Log.e(LOGTAG, "join interrupted: " + e.getMessage());
			e.printStackTrace();
		} finally {
			Log.i(LOGTAG, "thread joined");
			mThread = null;
		}
	}

	public void onRingerModeChanged()
	{
		Log.i(LOGTAG, "onRingerModeChanged");

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

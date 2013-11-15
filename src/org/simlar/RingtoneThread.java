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

import java.io.IOException;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

class RingtoneThread
{
	static final String LOGTAG = RingtoneThread.class.getSimpleName();
	Context mContext = null;
	private RingtoneThreadImpl mThread = null;

	private class RingtoneThreadImpl extends Thread
	{
		private Handler mHandler = null;

		// should only be accessed within thread
		MediaPlayer mMediaPlayer = initializeMediaPlayer();

		public RingtoneThreadImpl()
		{
			super();
		}

		@Override
		public void run()
		{
			Log.i(LOGTAG, "started");
			Looper.prepare();
			mHandler = new Handler();
			startMediaPlayer();
			Looper.loop();
		}

		MediaPlayer initializeMediaPlayer()
		{
			try {
				MediaPlayer mediaPlayer = new MediaPlayer();
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
				mediaPlayer.setDataSource(mContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
				mediaPlayer.prepare();
				mediaPlayer.setLooping(false);
				return mediaPlayer;
			} catch (IllegalStateException e) {
				Log.e(LOGTAG, "Media Player illegal state: " + e.getMessage());
				e.printStackTrace();
				return null;
			} catch (IOException e) {
				Log.e(LOGTAG, "Media Player io exception: " + e.getMessage());
				e.printStackTrace();
				return null;
			}
		}

		public void startMediaPlayer()
		{
			mHandler.post(new Runnable() {
				@Override
				public void run()
				{
					if (mMediaPlayer == null) {
						mMediaPlayer = initializeMediaPlayer();

						if (mMediaPlayer == null) {
							Log.e(LOGTAG, "failed to initialize MediaPlayer");
							return;
						}
					}

					Log.i(LOGTAG, "start ringing");
					mMediaPlayer.start();

					mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
						@Override
						public void onCompletion(MediaPlayer mp)
						{
							Log.i(LOGTAG, "MediaPlayer onCompletion");
							startMediaPlayer();
						}
					});
				}
			});
		}

		public void stopMediaPlayer()
		{
			mHandler.removeCallbacksAndMessages(null);

			mHandler.post(new Runnable() {
				@Override
				public void run()
				{
					Log.i(LOGTAG, "stop ringing");
					mMediaPlayer.stop();
					mMediaPlayer.release();
					mMediaPlayer = null;
					Looper.myLooper().quit();
					Log.i(LOGTAG, "ringing stopped");
				}
			});
		}
	}

	public RingtoneThread(final Context context)
	{
		mContext = context;
	}

	public void start()
	{
		if (mThread != null) {
			Log.i(LOGTAG, "already ringing => abort");
			return;
		}

		mThread = new RingtoneThreadImpl();
		mThread.start();
	}

	public void stop()
	{
		if (mThread == null) {
			Log.i(LOGTAG, "not ringing");
			return;
		}

		mThread.stopMediaPlayer();

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
}

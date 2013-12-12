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
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

class SoundEffectManager
{
	static final String LOGTAG = SoundEffectManager.class.getSimpleName();
	static final long MIN_PLAY_TIME = VibratorThread.VIBRATE_LENGTH + VibratorThread.VIBRATE_PAUSE;

	Context mContext = null;
	private final Map<SoundEffectType, SoundEffectThreadImpl> mThreads = new HashMap<SoundEffectType, SoundEffectThreadImpl>();

	public enum SoundEffectType {
		RINGTONE,
		UNENCRYPTED_CALL_ALARM
	}

	private class SoundEffectThreadImpl extends Thread
	{
		private Handler mHandler = null;
		final SoundEffectType mType;

		// should only be accessed within thread
		MediaPlayer mMediaPlayer = null;

		public SoundEffectThreadImpl(final SoundEffectType type)
		{
			super();
			mType = type;
		}

		@Override
		public void run()
		{
			Log.i(LOGTAG, "[" + mType + "] started");
			Looper.prepare();
			mHandler = new Handler();
			startMediaPlayer();
			Looper.loop();
			Log.i(LOGTAG, "[" + mType + "] ended");
		}

		MediaPlayer initializeMediaPlayer()
		{
			try {
				switch (mType) {
				case RINGTONE:
					MediaPlayer mediaPlayer = new MediaPlayer();
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
					mediaPlayer.setDataSource(mContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
					mediaPlayer.prepare();
					mediaPlayer.setLooping(false);
					return mediaPlayer;
				case UNENCRYPTED_CALL_ALARM:
					return MediaPlayer.create(mContext, R.raw.unencrypted_call);
				default:
					Log.e(LOGTAG, "unknown type");
					return null;
				}
			} catch (IllegalStateException e) {
				Log.e(LOGTAG, "[" + mType + "] Media Player illegal state: " + e.getMessage(), e);
				return null;
			} catch (IOException e) {
				Log.e(LOGTAG, "[" + mType + "] Media Player io exception: " + e.getMessage(), e);
				return null;
			}
		}

		public void startMediaPlayer()
		{
			startMediaPlayer(0);
		}

		void startMediaPlayer(final long delayMillis)
		{
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run()
				{
					if (mMediaPlayer == null) {
						mMediaPlayer = initializeMediaPlayer();

						if (mMediaPlayer == null) {
							Log.e(LOGTAG, "[" + mType + "] failed to initialize MediaPlayer");
							return;
						}
					}

					final long playStartTime = SystemClock.elapsedRealtime();
					Log.i(LOGTAG, "[" + mType + "] start playing at time: " + playStartTime);
					mMediaPlayer.start();

					mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
						@Override
						public void onCompletion(MediaPlayer mp)
						{
							final long now = SystemClock.elapsedRealtime();
							final long delay = Math.max(0, playStartTime + MIN_PLAY_TIME - now);
							Log.i(LOGTAG, "[" + mType + "] MediaPlayer onCompletion at: " + now + " restarting with delay: " + delay);
							startMediaPlayer(delay);
						}
					});
				}
			}, delayMillis);
		}

		public void stopMediaPlayer()
		{
			mHandler.removeCallbacksAndMessages(null);

			mHandler.post(new Runnable() {
				@Override
				public void run()
				{
					Log.i(LOGTAG, "[" + mType + "] stop playing");
					if (mMediaPlayer != null) {
						mMediaPlayer.stop();
						mMediaPlayer.release();
						mMediaPlayer = null;
					}
					Looper.myLooper().quit();
					Log.i(LOGTAG, "[" + mType + "] playing stopped");
				}
			});
		}
	}

	public SoundEffectManager(final Context context)
	{
		mContext = context;
	}

	public void start(final SoundEffectType type)
	{
		if (type == null) {
			Log.e(LOGTAG, "start with type null");
			return;
		}

		if (mThreads.containsKey(type)) {
			Log.i(LOGTAG, "[" + type + "] already playing");
			return;
		}

		//Log.i(LOGTAG, "[" + type + "] start playing");

		mThreads.put(type, new SoundEffectThreadImpl(type));
		mThreads.get(type).start();
	}

	public void stop(final SoundEffectType type)
	{
		if (type == null) {
			Log.e(LOGTAG, "stop with type null");
			return;
		}

		if (!mThreads.containsKey(type)) {
			//Log.i(LOGTAG, "[" + type + "] not playing");
			return;
		}

		mThreads.get(type).stopMediaPlayer();

		try {
			mThreads.get(type).join(300);
		} catch (InterruptedException e) {
			Log.e(LOGTAG, "[" + type + "] join interrupted: " + e.getMessage(), e);
		} finally {
			Log.i(LOGTAG, "[" + type + "] thread joined");
			mThreads.remove(type);
		}
	}

	public void stopAll()
	{
		for (final SoundEffectType type : SoundEffectType.values()) {
			stop(type);
		}
	}
}

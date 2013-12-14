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

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

final class SoundEffectManager
{
	static final String LOGTAG = SoundEffectManager.class.getSimpleName();
	static final long MIN_PLAY_TIME = VibratorManager.VIBRATE_LENGTH + VibratorManager.VIBRATE_PAUSE;

	final Context mContext;
	private final Map<SoundEffectType, SoundEffectPlayer> mPlayers = new HashMap<SoundEffectType, SoundEffectPlayer>();

	public enum SoundEffectType {
		RINGTONE,
		ENCRYPTION_HANDSHAKE,
		UNENCRYPTED_CALL_ALARM
	}

	private final class SoundEffectPlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener
	{
		final SoundEffectType mType;
		final Handler mHandler = new Handler();
		private MediaPlayer mMediaPlayer;
		private final long mPlayRequestTime;
		private long mPlayStart = -1;

		public SoundEffectPlayer(final SoundEffectType type, final long now)
		{
			mType = type;
			mMediaPlayer = initializeMediaPlayer();
			mPlayRequestTime = now;

			if (mMediaPlayer == null) {
				Log.e(LOGTAG, "[" + type + "] failed to create media player");
				return;
			}

			mMediaPlayer.setOnPreparedListener(this);
			mMediaPlayer.setOnErrorListener(this);
		}

		private MediaPlayer initializeMediaPlayer()
		{
			try {
				MediaPlayer mediaPlayer = new MediaPlayer();
				switch (mType) {
				case RINGTONE:
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
					mediaPlayer.setDataSource(mContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
					mediaPlayer.setLooping(false);
					return mediaPlayer;
				case ENCRYPTION_HANDSHAKE:
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
					mediaPlayer.setDataSource(mContext,
							Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.encryption_handshake));
					mediaPlayer.setLooping(true);
					return mediaPlayer;
				case UNENCRYPTED_CALL_ALARM:
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
					mediaPlayer.setDataSource(mContext, Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.unencrypted_call));
					mediaPlayer.setLooping(true);
					return mediaPlayer;
				default:
					Log.e(LOGTAG, "[" + mType + "] unknown type");
					return null;
				}
			} catch (final IOException e) {
				Log.e(LOGTAG, "[" + mType + "] Media Player io exception: " + e.getMessage(), e);
				return null;
			}
		}

		public void startMediaPlayer()
		{
			if (mMediaPlayer == null) {
				Log.e(LOGTAG, "[" + mType + "] not initialized");
				return;
			}

			Log.i(LOGTAG, "[" + mType + "] preparing");
			mMediaPlayer.prepareAsync();
		}

		@Override
		public void onPrepared(final MediaPlayer mp)
		{
			if (mMediaPlayer == null) {
				Log.e(LOGTAG, "[" + mType + "] not initialized");
				return;
			}

			if (mPlayStart == -1) {
				mPlayStart = SystemClock.elapsedRealtime();
			}
			final long playStartTime = SystemClock.elapsedRealtime();
			Log.i(LOGTAG, "[" + mType + "] start playing at time: " + playStartTime);
			mMediaPlayer.start();

			mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
				@Override
				public void onCompletion(final MediaPlayer mp2)
				{
					final long now = SystemClock.elapsedRealtime();
					final long delay = Math.max(0, playStartTime + MIN_PLAY_TIME - now);
					Log.i(LOGTAG, "[" + mType + "] MediaPlayer onCompletion at: " + now + " restarting with delay: " + delay);

					if (delay > 0) {
						mHandler.postDelayed(new Runnable() {
							@Override
							public void run()
							{
								onPrepared(mp2);
							}
						}, delay);
					} else {
						onPrepared(mp2);
					}
				}
			});
		}

		public void stopMediaPlayer()
		{
			mHandler.removeCallbacksAndMessages(null);
			if (mMediaPlayer != null) {
				mMediaPlayer.stop();
				mMediaPlayer.reset();
				mMediaPlayer.release();
				mMediaPlayer = null;
				final long now = SystemClock.elapsedRealtime();
				Log.i(LOGTAG, "[" + mType + "] play time=" + (now - mPlayStart)
						+ "ms delay=" + (mPlayRequestTime - mPlayStart)
						+ "ms sum=" + (now - mPlayRequestTime) + "ms");
			}
		}

		@Override
		public boolean onError(MediaPlayer mp, int what, int extra)
		{
			Log.e(LOGTAG, "[" + mType + "] MediaPlayer Error what=" + what + " extra=" + extra);
			mHandler.removeCallbacksAndMessages(null);
			if (mMediaPlayer != null) {
				mMediaPlayer.reset();
				mMediaPlayer.release();
				mMediaPlayer = null;
			}
			return true;
		}
	}

	public SoundEffectManager(final Context context)
	{
		mContext = context;
	}

	public void start(final SoundEffectType type)
	{
		final long now = SystemClock.elapsedRealtime();

		if (type == null) {
			Log.e(LOGTAG, "start with type null");
			return;
		}

		if (mPlayers.containsKey(type)) {
			Log.i(LOGTAG, "[" + type + "] already playing");
			return;
		}

		mPlayers.put(type, new SoundEffectPlayer(type, now));
		mPlayers.get(type).startMediaPlayer();
	}

	public void stop(final SoundEffectType type)
	{
		if (type == null) {
			Log.e(LOGTAG, "stop with type null");
			return;
		}

		if (!mPlayers.containsKey(type)) {
			//Log.i(LOGTAG, "[" + type + "] not playing");
			return;
		}

		if (mPlayers.get(type) == null) {
			Log.e(LOGTAG, "[" + type + "] not initialized");
			mPlayers.remove(type);
			return;
		}

		mPlayers.get(type).stopMediaPlayer();
		mPlayers.remove(type);

		Log.i(LOGTAG, "[" + type + "] stopped");
	}

	public void stopAll()
	{
		for (final SoundEffectType type : SoundEffectType.values()) {
			stop(type);
		}
	}

	@SuppressLint("InlinedApi")
	public void setInCallMode(final boolean enabled)
	{
		Log.i(LOGTAG, "setInCallMode: " + enabled);

		if (enabled) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_IN_COMMUNICATION);
			} else {
				((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_IN_CALL);
			}
		} else {
			((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
		}
	}
}

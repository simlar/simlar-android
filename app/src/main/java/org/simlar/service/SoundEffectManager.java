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

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.RawRes;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import org.simlar.R;
import org.simlar.helper.RingtoneHelper;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

final class SoundEffectManager
{
	private static final long MIN_PLAY_TIME = VibratorManager.VIBRATE_LENGTH + VibratorManager.VIBRATE_PAUSE;

	private final Context mContext;
	private final Map<SoundEffectType, SoundEffectPlayer> mPlayers = new EnumMap<>(SoundEffectType.class);

	public enum SoundEffectType
	{
		RINGTONE,
		WAITING_FOR_CONTACT,
		ENCRYPTION_HANDSHAKE,
		CALL_INTERRUPTION
	}

	private final class SoundEffectPlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener
	{
		final SoundEffectType mType;
		final Handler mHandler = new Handler();
		private MediaPlayer mMediaPlayer;
		private long mPlayRequestTime;
		private long mPlayStart = -1;

		SoundEffectPlayer(final SoundEffectType type, final long now)
		{
			mType = type;
			mMediaPlayer = initializeMediaPlayer();
			mPlayRequestTime = now;

			if (mMediaPlayer == null) {
				Lg.e("[", type, "] failed to create media player");
				return;
			}

			mMediaPlayer.setOnErrorListener(this);
		}

		private Uri createSoundUri(@RawRes final int sound)
		{
			return Uri.parse("android.resource://" + mContext.getPackageName() + '/' + sound);
		}

		private MediaPlayer initializeMediaPlayer()
		{
			try {
				final MediaPlayer mediaPlayer = new MediaPlayer();
				switch (mType) {
				case RINGTONE:
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
					// in case we do have permissions to read the ringtone
					try {
						mediaPlayer.setDataSource(mContext, RingtoneHelper.getDefaultRingtone());
					} catch (final IOException e) {
						Lg.w("[", mType, "] falling back to provided ringtone");
						mediaPlayer.setDataSource(mContext, createSoundUri(R.raw.ringtone));
					}
					mediaPlayer.setLooping(false);
					return mediaPlayer;
				case WAITING_FOR_CONTACT:
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
					mediaPlayer.setDataSource(mContext, createSoundUri(R.raw.waiting_for_contact));
					mediaPlayer.setLooping(true);
					return mediaPlayer;
				case ENCRYPTION_HANDSHAKE:
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
					mediaPlayer.setDataSource(mContext, createSoundUri(R.raw.encryption_handshake));
					mediaPlayer.setLooping(true);
					return mediaPlayer;
				case CALL_INTERRUPTION:
					mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
					mediaPlayer.setDataSource(mContext, createSoundUri(R.raw.call_interruption));
					mediaPlayer.setLooping(true);
					return mediaPlayer;
				default:
					Lg.e("[", mType, "] unknown type");
					return null;
				}
			} catch (final IOException e) {
				Lg.ex(e, "[", mType, "] Media Player IOException");
				return null;
			}
		}

		void prepare(final boolean start)
		{
			if (mMediaPlayer == null) {
				Lg.e("[", mType, "] not initialized");
				return;
			}

			Lg.i("[", mType, "] preparing");
			if (start) {
				mMediaPlayer.setOnPreparedListener(this);
			}
			mMediaPlayer.prepareAsync();
		}

		void startPrepared(final long now)
		{
			mPlayRequestTime = now;
			onPrepared(mMediaPlayer);
		}

		@Override
		public void onPrepared(final MediaPlayer mp)
		{
			if (mMediaPlayer == null) {
				Lg.e("[", mType, "] not initialized");
				return;
			}

			if (mPlayStart == -1) {
				mPlayStart = SystemClock.elapsedRealtime();
			}
			final long playStartTime = SystemClock.elapsedRealtime();
			Lg.i("[", mType, "] start playing at time: ", playStartTime);
			mMediaPlayer.start();

			mMediaPlayer.setOnCompletionListener(mp2 -> {
				final long now = SystemClock.elapsedRealtime();
				final long delay = Math.max(0, playStartTime + MIN_PLAY_TIME - now);
				Lg.i("[", mType, "] MediaPlayer onCompletion at: ", now, " restarting with delay: ", delay);

				if (delay > 0) {
					mHandler.postDelayed(() -> onPrepared(mp2), delay);
				} else {
					onPrepared(mp2);
				}
			});
		}

		void stopMediaPlayer()
		{
			mHandler.removeCallbacksAndMessages(null);
			if (mMediaPlayer != null) {
				mMediaPlayer.stop();
				mMediaPlayer.reset();
				mMediaPlayer.release();
				mMediaPlayer = null;
				final long now = SystemClock.elapsedRealtime();
				Lg.i("[", mType, "] play time=", now - mPlayStart,
						"ms delay=", mPlayRequestTime - mPlayStart,
						"ms sum=", now - mPlayRequestTime + "ms");
			}
		}

		@Override
		public boolean onError(final MediaPlayer mp, final int what, final int extra)
		{
			Lg.e("[", mType, "] MediaPlayer Error what=", what, " extra=", extra);
			mHandler.removeCallbacksAndMessages(null);
			if (mMediaPlayer != null) {
				mMediaPlayer.reset();
				mMediaPlayer.release();
				mMediaPlayer = null;
			}
			return true;
		}
	}

	SoundEffectManager(final Context context)
	{
		mContext = context;
	}

	public void start(final SoundEffectType type)
	{
		final long now = SystemClock.elapsedRealtime();

		if (type == null) {
			Lg.e("start with type null");
			return;
		}

		if (mPlayers.containsKey(type)) {
			Lg.i("[", type, "] already playing");
			return;
		}

		mPlayers.put(type, new SoundEffectPlayer(type, now));
		final SoundEffectPlayer player = mPlayers.get(type);
		if (player == null) {
			Lg.e("no player");
			return;
		}

		player.prepare(true);
	}

	@SuppressWarnings("SameParameterValue")
	public void prepare(final SoundEffectType type)
	{
		if (type == null) {
			Lg.e("start with type null");
			return;
		}

		if (mPlayers.containsKey(type)) {
			Lg.i("[", type, "] already prepared or playing");
			return;
		}

		mPlayers.put(type, new SoundEffectPlayer(type, -1));
		final SoundEffectPlayer player = mPlayers.get(type);
		if (player == null) {
			Lg.e("no player");
			return;
		}

		player.prepare(false);
	}

	@SuppressWarnings("SameParameterValue")
	public void startPrepared(final SoundEffectType type)
	{
		final long now = SystemClock.elapsedRealtime();

		Lg.i("[", type, "] playing prepared requested");

		if (type == null) {
			Lg.e("start with type null");
			return;
		}

		if (!mPlayers.containsKey(type)) {
			Lg.e("[", type, "] not prepared");
			return;
		}

		final SoundEffectPlayer player = mPlayers.get(type);
		if (player == null) {
			Lg.e("no player");
			return;
		}

		player.startPrepared(now);
	}

	public void stop(final SoundEffectType type)
	{
		if (type == null) {
			Lg.e("stop with type null");
			return;
		}

		if (!mPlayers.containsKey(type)) {
			//Log.i("[" + type + "] not playing");
			return;
		}

		final SoundEffectPlayer player = mPlayers.get(type);
		if (player == null) {
			Lg.e("[", type, "] not initialized");
			mPlayers.remove(type);
			return;
		}

		player.stopMediaPlayer();
		mPlayers.remove(type);

		Lg.i("[", type, "] stopped");
	}

	public void stopAll()
	{
		for (final SoundEffectType type : SoundEffectType.values()) {
			stop(type);
		}
	}

	public void setInCallMode(final boolean enabled)
	{
		Lg.i("setInCallMode: ", enabled);

		final AudioManager audioManager = Util.getSystemService(mContext, Context.AUDIO_SERVICE);
		if (enabled) {
			audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		} else {
			audioManager.setMode(AudioManager.MODE_NORMAL);
		}
	}
}

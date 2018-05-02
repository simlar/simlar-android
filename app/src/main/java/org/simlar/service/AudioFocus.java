/**
 * Copyright (C) 2015 The Simlar Authors.
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

import org.simlar.logging.Lg;
import org.simlar.utils.Util;

final class AudioFocus
{
	private final AudioManager mAudioManager;
	private boolean mRequested = false;

	AudioFocus(final Context context)
	{
		mAudioManager = Util.getSystemService(context, Context.AUDIO_SERVICE);
	}

	void request()
	{
		if (mRequested) {
			return;
		}

		// We acquire AUDIOFOCUS_GAIN_TRANSIENT instead of AUDIOFOCUS_GAIN because we want the music to resume after ringing or call
		final int status = mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
		switch (status) {
		case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
			Lg.i("AudioFocus granted");
			mRequested = true;
			break;
		case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
			Lg.w("AudioFocus request not granted");
			break;
		default:
			Lg.e("requesting AudioFocus failed with unknown status: ", status);
			break;
		}
	}

	void release()
	{
		if (!mRequested) {
			return;
		}

		final int status = mAudioManager.abandonAudioFocus(null);
		switch (status) {
		case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
			Lg.i("AudioFocus released");
			mRequested = true;
			break;
		case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
			Lg.w("AudioFocus release not granted ");
			break;
		default:
			Lg.e("releasing AudioFocus failed with unknown status: ", status);
			break;
		}
	}
}

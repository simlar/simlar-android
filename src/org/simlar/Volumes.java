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

public class Volumes
{
	private static final float GAIN_MAX = 15.0f;
	private static final float GAIN_MIN = -15.0f;

	private final float mPlayGain;
	private final float mMicGain;
	private final boolean mExternalSpeaker;
	private final boolean mMicrophoneMuted;

	public Volumes()
	{
		mPlayGain = 0.0f;
		mMicGain = 0.0f;
		mExternalSpeaker = false;
		mMicrophoneMuted = false;
	}

	public Volumes(final float playGain, final float micGain, final boolean externalSpeaker, final boolean microphoneMuted)
	{
		mPlayGain = playGain;
		mMicGain = micGain;
		mExternalSpeaker = externalSpeaker;
		mMicrophoneMuted = microphoneMuted;
	}

	@Override
	public String toString()
	{
		return "playGain: " + mPlayGain + " micGain: " + mMicGain;
	}

	public float getPlayGain()
	{
		return mPlayGain;
	}

	public float getMicrophoneGain()
	{
		return mMicGain;
	}

	public boolean getExternalSpeaker()
	{
		return mExternalSpeaker;
	}

	public boolean getMicrophoneMuted()
	{
		return mMicrophoneMuted;
	}

	public Volumes toggleExternalSpeaker()
	{
		return new Volumes(mPlayGain, mMicGain, !mExternalSpeaker, mMicrophoneMuted);
	}

	public Volumes toggleMicrophoneMuted()
	{
		return new Volumes(mPlayGain, mMicGain, mExternalSpeaker, !mMicrophoneMuted);
	}

	public int getProgressSpeaker()
	{
		return gain2Progress(mPlayGain);
	}

	public int getProgessMicrophone()
	{
		return gain2Progress(mMicGain);
	}

	public Volumes setProgressSpeaker(final int progress)
	{
		return new Volumes(progress2Gain(progress), mMicGain, mExternalSpeaker, mMicrophoneMuted);
	}

	public Volumes setProgressMicrophone(final int progress)
	{
		return new Volumes(mPlayGain, progress2Gain(progress), mExternalSpeaker, mMicrophoneMuted);
	}

	// GAIN_MIN ... GAIN_MAX -> 0 ... 100
	private static int gain2Progress(final float gain)
	{
		if (gain <= GAIN_MIN) {
			return 0;
		}

		if (gain >= GAIN_MAX) {
			return 100;
		}

		return 50 + Math.round(100.0f / (GAIN_MAX - GAIN_MIN) * gain);
	}

	// 0 ... 100 -> GAIN_MIN ... GAIN_MAX
	private static float progress2Gain(final int progress)
	{
		if (progress <= 0) {
			return GAIN_MIN;
		}

		if (progress >= 100) {
			return GAIN_MAX;
		}

		return (GAIN_MIN - GAIN_MAX) / 2 + (GAIN_MAX - GAIN_MIN) * (progress / 100.0f);
	}
}

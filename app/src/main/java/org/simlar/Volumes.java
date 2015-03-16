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

package org.simlar;

public final class Volumes
{
	private static final float GAIN_MAX = 15.0f;
	private static final float GAIN_MIN = -15.0f;

	private final float mPlayGain;
	private final float mMicGain;
	private final boolean mExternalSpeaker;
	private final MicrophoneStatus mMicrophoneStatus;
	private final boolean mEchoLimiter;

	public enum MicrophoneStatus {
		DISABLED,
		MUTED,
		ON
	}

	public Volumes()
	{
		mPlayGain = 0.0f;
		mMicGain = 0.0f;
		mExternalSpeaker = false;
		mMicrophoneStatus = MicrophoneStatus.ON;
		mEchoLimiter = false;
	}

	public Volumes(final float playGain, final float micGain, final boolean externalSpeaker, final MicrophoneStatus microphoneStatus,
			final boolean echoLimiter)
	{
		mPlayGain = playGain;
		mMicGain = micGain;
		mExternalSpeaker = externalSpeaker;
		mMicrophoneStatus = microphoneStatus;
		mEchoLimiter = echoLimiter;
	}

	@Override
	public String toString()
	{
		return "playGain: " + mPlayGain + " micGain: " + mMicGain + " micStatus: " + mMicrophoneStatus + " externalSpeaker: " + mExternalSpeaker
				+ " echoLimiter:" + mEchoLimiter;
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

	public MicrophoneStatus getMicrophoneStatus()
	{
		return mMicrophoneStatus;
	}

	public boolean getMicrophoneMuted()
	{
		return mMicrophoneStatus != MicrophoneStatus.ON;
	}

	public boolean getEchoLimiter()
	{
		return mEchoLimiter;
	}

	public Volumes toggleEchoLimiter()
	{
		return new Volumes(mPlayGain, mMicGain, mExternalSpeaker, mMicrophoneStatus, !mEchoLimiter);
	}

	public Volumes toggleExternalSpeaker()
	{
		return new Volumes(mPlayGain, mMicGain, !mExternalSpeaker, mMicrophoneStatus, mEchoLimiter);
	}

	public Volumes toggleMicrophoneMuted()
	{
		switch (mMicrophoneStatus) {
		case DISABLED:
			return this;
		case MUTED:
			return new Volumes(mPlayGain, mMicGain, mExternalSpeaker, MicrophoneStatus.ON, mEchoLimiter);
		case ON:
		default:
			return new Volumes(mPlayGain, mMicGain, mExternalSpeaker, MicrophoneStatus.MUTED, mEchoLimiter);
		}
	}

	public Volumes setMicrophoneStatus(final MicrophoneStatus microphoneStatus)
	{
		return new Volumes(mPlayGain, mMicGain, mExternalSpeaker, microphoneStatus, mEchoLimiter);
	}

	public int getProgressSpeaker()
	{
		return gain2Progress(mPlayGain);
	}

	public int getProgressMicrophone()
	{
		return gain2Progress(mMicGain);
	}

	public Volumes setProgressSpeaker(final int progress)
	{
		return new Volumes(progress2Gain(progress), mMicGain, mExternalSpeaker, mMicrophoneStatus, mEchoLimiter);
	}

	public Volumes setProgressMicrophone(final int progress)
	{
		return new Volumes(mPlayGain, progress2Gain(progress), mExternalSpeaker, mMicrophoneStatus, mEchoLimiter);
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

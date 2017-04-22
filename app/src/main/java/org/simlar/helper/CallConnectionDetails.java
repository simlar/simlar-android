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

package org.simlar.helper;

import org.simlar.utils.Util;

import java.text.DecimalFormat;

public final class CallConnectionDetails
{
	private static final DecimalFormat GUI_VALUE = new DecimalFormat("#0.0");

	private NetworkQuality mQuality = NetworkQuality.UNKNOWN;
	private String mCodec = null;
	private String mIceState = null;
	private int mUpload = -1;
	private int mDownload = -1;
	private int mJitter = -1;
	private int mPacketLoss = -1;
	private long mLatePackets = -1;
	private int mRoundTripDelay = -1;
	private boolean mEndedCall = false;

	public boolean updateCallStats(final NetworkQuality quality, final String codec, final String iceState, final int upload, final int download,
	                               final int jitter, final int packetLoss, final long latePackets, final int roundTripDelay)
	{
		if (quality == mQuality && Util.equalString(codec, mCodec) && Util.equalString(iceState, mIceState)
				&& upload == mUpload && download == mDownload && jitter == mJitter && packetLoss == mPacketLoss
				&& latePackets == mLatePackets && roundTripDelay == mRoundTripDelay)
		{
			return false;
		}

		mQuality = quality;
		mCodec = codec;
		mIceState = iceState;
		mUpload = upload;
		mDownload = download;
		mJitter = jitter;
		mPacketLoss = packetLoss;
		mLatePackets = latePackets;
		mRoundTripDelay = roundTripDelay;

		return true;
	}

	public boolean updateEndedCall()
	{
		if (mEndedCall) {
			return false;
		}

		mEndedCall = true;
		return true;
	}

	public boolean hasConnectionInfo()
	{
		return mQuality.isKnown() &&
				mUpload >= 0 && mDownload >= 0 &&
				!Util.isNullOrEmpty(mCodec) && !Util.isNullOrEmpty(mIceState);
	}

	private String formatCodec()
	{
		if (Util.isNullOrEmpty(mCodec)) {
			return "";
		}

		return " Codec=" + mCodec;
	}

	private String formatIceState()
	{
		if (Util.isNullOrEmpty(mIceState)) {
			return "";
		}

		return " IceState=" + mIceState;
	}

	private static String formatValue(final String name, final int value)
	{
		if (value <= 0) {
			return "";
		}

		return " " + name + "=" + value;
	}

	@SuppressWarnings("SameParameterValue")
	private static String formatValue(final String name, final long value)
	{
		if (value <= 0) {
			return "";
		}

		return " " + name + "=" + value;
	}

	@Override
	public String toString()
	{
		return "quality=" + mQuality + formatIceState() + formatCodec()
				+ formatValue("upload", mUpload) + formatValue("download", mDownload) + formatValue("jitter", mJitter)
				+ formatValue("PacketLoss", mPacketLoss) + formatValue("LatePackets", mLatePackets) + formatValue("RoundTripDelay", mRoundTripDelay);
	}

	public int getQualityDescription()
	{
		return mQuality.getDescription();
	}

	public String getCodec()
	{
		return mCodec;
	}

	public String getIceState()
	{
		return mIceState;
	}

	public String getUpload()
	{
		return GUI_VALUE.format(mUpload / 10.0f);
	}

	public String getDownload()
	{
		return GUI_VALUE.format(mDownload / 10.0f);
	}

	public String getJitter()
	{
		return String.valueOf(mJitter);
	}

	public String getPacketLoss()
	{
		return GUI_VALUE.format(mPacketLoss / 10.0f);
	}

	public String getLatePackets()
	{
		return String.valueOf(mLatePackets);
	}

	public String getRoundTripDelay()
	{
		return String.valueOf(mRoundTripDelay);
	}
}

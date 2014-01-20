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

import java.text.DecimalFormat;

public class CallConnectionDetails
{
	private static final DecimalFormat GUI_VALUE = new DecimalFormat("#0.0");

	private NetworkQuality mQuality = NetworkQuality.UNKNOWN;
	private String mCodec = null;
	private String mIceState = null;
	private int mUpload = -1;
	private int mDownload = -1;
	private boolean mEndedCall = false;

	public boolean updateCallStats(final NetworkQuality quality, final String codec, final String iceState, final int upload, final int download)
	{
		if (quality == mQuality && Util.equalString(codec, mCodec) && Util.equalString(iceState, mIceState)
				&& upload == mUpload && download == mDownload)
		{
			return false;
		}

		mQuality = quality;
		mCodec = codec;
		mIceState = iceState;
		mUpload = upload;
		mDownload = download;

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
		if (!mQuality.isKnown() || mUpload < 0 || mDownload < 0) {
			return false;
		}

		if (Util.isNullOrEmpty(mCodec) || Util.isNullOrEmpty(mIceState)) {
			return false;
		}

		return true;
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

		return " " + name + "=" + String.valueOf(value);
	}

	@Override
	public String toString()
	{
		return "quality=" + mQuality + formatIceState() + formatCodec() + formatValue("upload", mUpload) + formatValue("download", mDownload);
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

	public boolean isEndedCall()
	{
		return mEndedCall;
	}
}

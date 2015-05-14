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

package org.simlar.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TextView;

import org.simlar.R;
import org.simlar.helper.CallConnectionDetails;

public class ConnectionDetailsView extends TableLayout
{
	private final Context mContext;

	private final TextView mTextViewQuality;
	private final TextView mTextViewUpload;
	private final TextView mTextViewDownload;
	private final TextView mTextViewCodec;
	private final TextView mTextViewJitter;
	private final TextView mTextViewPacketLoss;
	private final TextView mTextViewLatePackets;
	private final TextView mTextViewRoundTripDelay;
	private final TextView mTextViewIceState;

	public ConnectionDetailsView(final Context context)
	{
		this(context, null);
	}

	public ConnectionDetailsView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);

		mContext = context;
		View.inflate(mContext, R.layout.view_connection_details, this);

		mTextViewQuality = (TextView) findViewById(R.id.textViewQuality);
		mTextViewUpload = (TextView) findViewById(R.id.textViewUpload);
		mTextViewDownload = (TextView) findViewById(R.id.textViewDownload);
		mTextViewCodec = (TextView) findViewById(R.id.textViewCodec);
		mTextViewJitter = (TextView) findViewById(R.id.textViewJitter);
		mTextViewPacketLoss = (TextView) findViewById(R.id.textViewPacketLoss);
		mTextViewLatePackets = (TextView) findViewById(R.id.textViewLatePackets);
		mTextViewRoundTripDelay = (TextView) findViewById(R.id.textViewRoundTripDelay);
		mTextViewIceState = (TextView) findViewById(R.id.textViewIceState);
	}

	public void setCallConnectionDetails(final CallConnectionDetails callConnectionDetails)
	{
		if (callConnectionDetails == null || !callConnectionDetails.hasConnectionInfo()) {
			/// TODO: think about emptying
			return;
		}

		mTextViewQuality.setText(mContext.getString(callConnectionDetails.getQualityDescription()));
		mTextViewUpload.setText(callConnectionDetails.getUpload() + " " + mContext.getString(R.string.connection_details_activity_kilobytes_per_second));
		mTextViewDownload.setText(callConnectionDetails.getDownload() + " " + mContext.getString(R.string.connection_details_activity_kilobytes_per_second));
		mTextViewCodec.setText(callConnectionDetails.getCodec());
		mTextViewJitter.setText(callConnectionDetails.getJitter());
		mTextViewPacketLoss.setText(callConnectionDetails.getPacketLoss() + " " + mContext.getString(R.string.connection_details_activity_percent));
		mTextViewLatePackets.setText(callConnectionDetails.getLatePackets());
		mTextViewRoundTripDelay.setText(callConnectionDetails.getRoundTripDelay() + " " + mContext.getString(R.string.connection_details_activity_milli_seconds));
		mTextViewIceState.setText(callConnectionDetails.getIceState());
	}
}

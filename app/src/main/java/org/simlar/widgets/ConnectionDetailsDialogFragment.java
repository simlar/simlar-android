/*
 * Copyright (C) 2013 - 2015 The Simlar Authors.
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
 *
 */

package org.simlar.widgets;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import org.simlar.R;
import org.simlar.helper.CallConnectionDetails;
import org.simlar.logging.Lg;

public final class ConnectionDetailsDialogFragment extends DialogFragment
{
	// gui elements
	private TextView mTextViewQuality = null;
	private TextView mTextViewUpload = null;
	private TextView mTextViewDownload = null;
	private TextView mTextViewIceState = null;
	private TextView mTextViewCodec = null;
	private TextView mTextViewJitter = null;
	private TextView mTextViewPacketLoss = null;
	private TextView mTextViewLatePackets = null;
	private TextView mTextViewRoundTripDelay = null;

	@NonNull
	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		Lg.i("onCreateDialog");
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setView(createView());
		return builder.create();
	}

	private View createView()
	{
		@SuppressLint("InflateParams")
		final View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_fragment_connection_details, null);

		mTextViewQuality = (TextView) view.findViewById(R.id.textViewQuality);
		mTextViewUpload = (TextView) view.findViewById(R.id.textViewUpload);
		mTextViewDownload = (TextView) view.findViewById(R.id.textViewDownload);
		mTextViewIceState = (TextView) view.findViewById(R.id.textViewIceState);
		mTextViewCodec = (TextView) view.findViewById(R.id.textViewCodec);
		mTextViewJitter = (TextView) view.findViewById(R.id.textViewJitter);
		mTextViewPacketLoss = (TextView) view.findViewById(R.id.textViewPacketLoss);
		mTextViewLatePackets = (TextView) view.findViewById(R.id.textViewLatePackets);
		mTextViewRoundTripDelay = (TextView) view.findViewById(R.id.textViewRoundTripDelay);

		return view;
	}

	public void setCallConnectionDetails(final CallConnectionDetails callConnectionDetails)
	{
		if (callConnectionDetails == null || !callConnectionDetails.hasConnectionInfo()) {
			return;
		}

		if (isDetached() || !isResumed()) {
			return;
		}

		mTextViewQuality.setText(getString(callConnectionDetails.getQualityDescription()));
		mTextViewUpload.setText(getString(R.string.connection_details_dialog_fragment_kilobytes_per_second, callConnectionDetails.getUpload()));
		mTextViewDownload.setText(getString(R.string.connection_details_dialog_fragment_kilobytes_per_second, callConnectionDetails.getDownload()));
		mTextViewIceState.setText(callConnectionDetails.getIceState());
		mTextViewCodec.setText(callConnectionDetails.getCodec());
		mTextViewJitter.setText(callConnectionDetails.getJitter());
		mTextViewPacketLoss.setText(getString(R.string.connection_details_dialog_fragment_percent, callConnectionDetails.getPacketLoss()));
		mTextViewLatePackets.setText(callConnectionDetails.getLatePackets());
		mTextViewRoundTripDelay.setText(getString(R.string.connection_details_dialog_fragment_milli_seconds, callConnectionDetails.getRoundTripDelay()));
	}
}

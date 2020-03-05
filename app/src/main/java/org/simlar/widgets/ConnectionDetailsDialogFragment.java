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
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import org.simlar.R;
import org.simlar.helper.CallConnectionDetails;
import org.simlar.logging.Lg;

@SuppressWarnings("WeakerAccess") // class must be public otherwise crashes
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
		final FragmentActivity activity = getActivity();
		if (activity == null) {
			Lg.e("no activity cannot create dialog");
			return super.onCreateDialog(savedInstanceState);
		}

		return new AlertDialog.Builder(activity)
				.setView(createView(activity))
				.create();
	}

	private View createView(final FragmentActivity activity)
	{
		@SuppressLint("InflateParams")
		final View view = activity.getLayoutInflater().inflate(R.layout.dialog_fragment_connection_details, null);

		mTextViewQuality = view.findViewById(R.id.textViewQuality);
		mTextViewUpload = view.findViewById(R.id.textViewUpload);
		mTextViewDownload = view.findViewById(R.id.textViewDownload);
		mTextViewIceState = view.findViewById(R.id.textViewIceState);
		mTextViewCodec = view.findViewById(R.id.textViewCodec);
		mTextViewJitter = view.findViewById(R.id.textViewJitter);
		mTextViewPacketLoss = view.findViewById(R.id.textViewPacketLoss);
		mTextViewLatePackets = view.findViewById(R.id.textViewLatePackets);
		mTextViewRoundTripDelay = view.findViewById(R.id.textViewRoundTripDelay);

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

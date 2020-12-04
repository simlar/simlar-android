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

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import org.simlar.R;
import org.simlar.databinding.DialogFragmentConnectionDetailsBinding;
import org.simlar.helper.CallConnectionDetails;
import org.simlar.logging.Lg;

@SuppressWarnings("WeakerAccess") // class must be public otherwise crashes
public final class ConnectionDetailsDialogFragment extends DialogFragment
{
	private DialogFragmentConnectionDetailsBinding mBinding = null;

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
		mBinding = DialogFragmentConnectionDetailsBinding.inflate(activity.getLayoutInflater());
		return mBinding.getRoot();
	}

	@Override
	public void onDestroyView()
	{
		mBinding = null;
		super.onDestroyView();
	}

	public void setCallConnectionDetails(final CallConnectionDetails callConnectionDetails)
	{
		if (callConnectionDetails == null || !callConnectionDetails.hasConnectionInfo()) {
			return;
		}

		if (isDetached() || !isResumed()) {
			return;
		}

		mBinding.textViewQuality.setText(getString(callConnectionDetails.getQualityDescription()));
		mBinding.textViewUpload.setText(getString(R.string.connection_details_dialog_fragment_kilobytes_per_second, callConnectionDetails.getUpload()));
		mBinding.textViewDownload.setText(getString(R.string.connection_details_dialog_fragment_kilobytes_per_second, callConnectionDetails.getDownload()));
		mBinding.textViewIceState.setText(callConnectionDetails.getIceState());
		mBinding.textViewCodec.setText(callConnectionDetails.getCodec());
		mBinding.textViewJitter.setText(callConnectionDetails.getJitter());
		mBinding.textViewPacketLoss.setText(getString(R.string.connection_details_dialog_fragment_percent, callConnectionDetails.getPacketLoss()));
		mBinding.textViewLatePackets.setText(callConnectionDetails.getLatePackets());
		mBinding.textViewRoundTripDelay.setText(getString(R.string.connection_details_dialog_fragment_milli_seconds, callConnectionDetails.getRoundTripDelay()));
		mBinding.textViewEncryptionDescription.setText(callConnectionDetails.getEncryptionDescription());
	}
}

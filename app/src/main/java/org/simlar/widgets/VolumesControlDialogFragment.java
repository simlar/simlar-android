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
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;

import org.simlar.R;
import org.simlar.logging.Lg;

public final class VolumesControlDialogFragment extends DialogFragment
{
	private Listener mListener = null;

	public interface Listener
	{
		int getMicrophoneVolume();

		int getSpeakerVolume();

		boolean getEchoLimiter();

		void onMicrophoneVolumeChanged(final int progress);

		void onSpeakerVolumeChanged(final int progress);

		void onEchoLimiterChanged(final boolean enabled);
	}

	@Override
	public void onAttach(final Context context)
	{
		super.onAttach(context);

		if (context instanceof Listener) {
			mListener = (Listener) context;
		} else {
			Lg.e(context.getClass().getName(), " should implement ", Listener.class.getName());
		}
	}

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
		final View view = activity.getLayoutInflater().inflate(R.layout.dialog_fragment_volumes_control, null);

		final SeekBar seekBarSpeaker = view.findViewById(R.id.seekBarSpeaker);
		final SeekBar seekBarMicrophone = view.findViewById(R.id.seekBarMicrophone);
		final CheckBox checkBoxEchoLimiter = view.findViewById(R.id.checkBoxEchoLimiter);

		if (mListener != null) {
			seekBarSpeaker.setProgress(mListener.getSpeakerVolume());
			seekBarMicrophone.setProgress(mListener.getMicrophoneVolume());
			checkBoxEchoLimiter.setChecked(mListener.getEchoLimiter());
		}

		seekBarSpeaker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
		{
			@Override
			public void onStopTrackingTouch(final SeekBar seekBar)
			{
			}

			@Override
			public void onStartTrackingTouch(final SeekBar seekBar)
			{
			}

			@Override
			public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser)
			{
				Lg.i("seekBarSpeaker changed: ", progress);
				if (mListener == null) {
					return;
				}
				mListener.onSpeakerVolumeChanged(progress);
			}
		});

		seekBarMicrophone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
		{
			@Override
			public void onStopTrackingTouch(final SeekBar seekBar)
			{
			}

			@Override
			public void onStartTrackingTouch(final SeekBar seekBar)
			{
			}

			@Override
			public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser)
			{
				Lg.i("seekBarMicrophone changed: ", progress);
				if (mListener == null) {
					return;
				}
				mListener.onMicrophoneVolumeChanged(progress);
			}
		});

		checkBoxEchoLimiter.setOnCheckedChangeListener((buttonView, isChecked) -> {
			Lg.i("CheckBoxEchoLimiter.onCheckedChanged: ", isChecked);
			if (mListener == null) {
				return;
			}
			mListener.onEchoLimiterChanged(isChecked);
		});

		return view;
	}
}

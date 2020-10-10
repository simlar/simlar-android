/*
 * Copyright (C) The Simlar Authors.
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import org.simlar.R;
import org.simlar.logging.Lg;

@SuppressWarnings("WeakerAccess") // class must be public otherwise crashes
public final class AudioOutputSelectDialogFragment extends DialogFragment
{
	private Listener mListener = null;

	public interface Listener
	{
		int onAudioOutputSelected();
	}

	@Override
	public void onAttach(@NonNull final Context context)
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

		return view;
	}
}

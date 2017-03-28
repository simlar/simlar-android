/*
 * Copyright (C) 2013 - 2017 The Simlar Authors.
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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.simlar.R;
import org.simlar.logging.Lg;


public final class NoContactPermissionFragment extends Fragment
{
	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.fragment_no_contact_permission, container, false);

		view.findViewById(R.id.buttonSettings).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				requestContactPermissionsClicked();
			}
		});
		view.findViewById(R.id.buttonCallContact).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				callContactClicked();
			}
		});

		return view;
	}

	private void requestContactPermissionsClicked()
	{
		Lg.i("requestContactPermissionsClicked");
	}

	private void callContactClicked()
	{
		Lg.i("callContactClicked");
	}
}

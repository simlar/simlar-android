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

package org.simlar.service;

import android.content.Context;

import org.simlar.R;

public enum AudioOutputType
{
	PHONE(R.string.audio_output_type_phone),
	WIRED_HEADSET(R.string.audio_output_type_wired_headset),
	SPEAKER(R.string.audio_output_type_speaker),
	BLUETOOTH(R.string.audio_output_type_bluetooth);

	private final int mResourceId;

	AudioOutputType(final int resourceId)
	{
		mResourceId = resourceId;
	}

	public String toDisplayName(final Context context)
	{
		return context.getString(mResourceId);
	}
}

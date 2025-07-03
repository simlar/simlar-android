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

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.Menu;

import androidx.appcompat.app.AppCompatActivity;

import org.simlar.databinding.ActivityAboutBinding;
import org.simlar.helper.Version;

public final class AboutActivity extends AppCompatActivity
{
	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		final ActivityAboutBinding binding = ActivityAboutBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());

		binding.textViewVersion.setText(Version.getVersionName(this));

		// make hrefs work in privacy statement and terms of use
		binding.textViewPrivacyStatementAndTermsOfUse.setMovementMethod(LinkMovementMethod.getInstance());
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}
}

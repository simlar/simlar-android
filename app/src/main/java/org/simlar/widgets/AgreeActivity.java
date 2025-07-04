/**
 * Copyright (C) 2013 - 2014 The Simlar Authors.
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

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.simlar.R;
import org.simlar.utils.Util;

public class AgreeActivity extends AppCompatActivity
{
	@Override
	protected final void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_agree);
		Util.edge2edgeLayout(findViewById(R.id.layoutAgreeActivity));

		// make hrefs work in terms and conditions
		final TextView termsAndConditions = findViewById(R.id.textViewTermsAndConditions);
		termsAndConditions.setMovementMethod(LinkMovementMethod.getInstance());

		UnmaintainedWarningDialog.show(this);
	}

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public final void createAccount(final View view)
	{
		startActivity(new Intent(this, VerifyNumberActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
		finish();
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public final void cancelAccountCreation(final View view)
	{
		finish();
	}
}

/**
 * Copyright (C) 2013 The Simlar Authors.
 *
 * This file is part of Simlar. (http://www.simlar.org)
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

package org.simlar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class RingingActivity extends Activity
{
	static final String LOGTAG = RingingActivity.class.getSimpleName();

	private SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorRinging();

	private class SimlarServiceCommunicatorRinging extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorRinging()
		{
			super(LOGTAG);
		}

		@Override
		void onBoundToSimlarService()
		{
			RingingActivity.this.onSimlarCallStateChanged();
		}

		@Override
		void onSimlarCallStateChanged()
		{
			RingingActivity.this.onSimlarCallStateChanged();
		}

		@Override
		void onServiceFinishes()
		{
			RingingActivity.this.finish();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.i(LOGTAG, "onCreate ");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_ringing);

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
				WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();
		mCommunicator.register(this, RingingActivity.class);
	}

	@Override
	protected void onPause()
	{
		Log.i(LOGTAG, "onPause");
		mCommunicator.unregister(this);
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		return true;
	}

	void onSimlarCallStateChanged()
	{
		if (mCommunicator.getService() == null) {
			Log.e(LOGTAG, "ERROR: onSimlarCallStateChanged but not bound to service");
			return;
		}

		final SimlarCallState simlarCallState = mCommunicator.getService().getSimlarCallState();
		if (simlarCallState == null || simlarCallState.isEmpty()) {
			Log.e(LOGTAG, "ERROR: onSimlarCallStateChanged simlarCallState null or empty");
			return;
		}

		Log.i(LOGTAG, "onSimlarCallStateChanged " + simlarCallState);

		setTitle(String.format(getString(R.string.ringing_activity_title), simlarCallState.getDisplayName()));

		if (simlarCallState.hasMessage()) {
			Toast.makeText(this, String.format(getString(simlarCallState.getMsgId()), simlarCallState.getDisplayName()), Toast.LENGTH_LONG).show();
		}

		if (simlarCallState.isEndedCall()) {
			finish();
		}
	}

	@SuppressWarnings("unused")
	public void pickUp(View view)
	{
		mCommunicator.getService().pickUp();
		startActivity(new Intent(this, CallActivity.class));
		finish();
	}

	@SuppressWarnings("unused")
	public void terminateCall(View view)
	{
		mCommunicator.getService().terminateCall();
		finish();
	}

	@Override
	public void onBackPressed()
	{
		// prevent switch to MainActivity
		moveTaskToBack(true);
	}
}

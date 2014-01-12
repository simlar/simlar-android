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
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class ConnectionDetailsActivity extends Activity
{
	static final String LOGTAG = ConnectionDetailsActivity.class.getSimpleName();

	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorConnectionDetails();

	// gui elements
	private TextView mTextViewQuality;
	private TextView mTextViewUpload;
	private TextView mTextViewDownload;
	private TextView mTextViewIceState;
	private TextView mTextViewCodec;

	private class SimlarServiceCommunicatorConnectionDetails extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorConnectionDetails()
		{
			super(LOGTAG);
		}

		@Override
		void onBoundToSimlarService()
		{
			ConnectionDetailsActivity.this.onSimlarCallStateChanged();
		}

		@Override
		void onSimlarCallStateChanged()
		{
			ConnectionDetailsActivity.this.onSimlarCallStateChanged();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connection_details);

		mTextViewQuality = (TextView) findViewById(R.id.textViewQuality);
		mTextViewUpload = (TextView) findViewById(R.id.textViewUpload);
		mTextViewDownload = (TextView) findViewById(R.id.textViewDownload);
		mTextViewIceState = (TextView) findViewById(R.id.textViewIceState);
		mTextViewCodec = (TextView) findViewById(R.id.textViewCodec);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	@Override
	protected void onResume()
	{
		Log.i(LOGTAG, "onResume ");
		super.onResume();
		mCommunicator.register(this, CallActivity.class);
	}

	@Override
	protected void onPause()
	{
		Log.i(LOGTAG, "onPause");
		mCommunicator.unregister(this);
		super.onPause();
	}

	public void onSimlarCallStateChanged()
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

		if (simlarCallState.isEndedCall()) {
			ConnectionDetailsActivity.this.finish();
		}

		if (simlarCallState.hasConnectionInfo()) {
			mTextViewQuality.setText(getString(simlarCallState.getQualityDescription()));
			mTextViewUpload.setText(simlarCallState.getUpload() + " " + getString(R.string.connection_details_activity_kbytes_per_second));
			mTextViewDownload.setText(simlarCallState.getDownload() + " " + getString(R.string.connection_details_activity_kbytes_per_second));
			mTextViewIceState.setText(simlarCallState.getIceState());
			mTextViewCodec.setText(simlarCallState.getCodec());
		}
	}
}

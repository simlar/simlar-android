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
	protected static final String LOGTAG = ConnectionDetailsActivity.class.getSimpleName();
	protected SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorConnectionDetails();

	private class SimlarServiceCommunicatorConnectionDetails extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorConnectionDetails()
		{
			super(LOGTAG);
		}

		@Override
		protected void onSimlarCallStateChanged()
		{
			if (getService() == null) {
				Log.e(LOGTAG, "service is null");
				return;
			}

			final SimlarCallState simlarCallState = getService().getSimlarCallState();
			if (simlarCallState == null || simlarCallState.isEmpty()) {
				Log.e(LOGTAG, "ERROR: onSimlarCallStateChanged simlarCallState null or empty");
				return;
			}

			if (simlarCallState.isEndedCall()) {
				ConnectionDetailsActivity.this.finish();
			}

			if (simlarCallState.hasConnectionInfo()) {
				setIceState(simlarCallState.getIceState());
				setCodec(simlarCallState.getCodec());
				setBandwidthInfo(simlarCallState.getUpload(), simlarCallState.getDownload(), getString(simlarCallState.getQualityDescription()));
			}

		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connection_details);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
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

	void setIceState(final String iceState)
	{
		final TextView tv = (TextView) findViewById(R.id.textViewIceState);
		tv.setText(iceState);
	}

	void setCodec(final String codec)
	{
		final TextView tv = (TextView) findViewById(R.id.textViewCodec);
		tv.setText(codec);
	}

	void setBandwidthInfo(String upload, String download, String quality)
	{
		final TextView tvUpload = (TextView) findViewById(R.id.textViewUpload);
		final TextView tvDownload = (TextView) findViewById(R.id.textViewDownload);
		final TextView tvQuality = (TextView) findViewById(R.id.textViewQuality);

		tvUpload.setText(upload + " " + getString(R.string.call_activity_kbytes_per_second));
		tvDownload.setText(download + " " + getString(R.string.call_activity_kbytes_per_second));
		tvQuality.setText(quality);
	}

}

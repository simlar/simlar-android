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

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.WindowManager;
import android.widget.TextView;

import org.simlar.R;
import org.simlar.helper.CallConnectionDetails;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarServiceCommunicator;

public final class ConnectionDetailsActivity extends Activity
{
	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorConnectionDetails();

	// gui elements
	private TextView mTextViewQuality;
	private TextView mTextViewUpload;
	private TextView mTextViewDownload;
	private TextView mTextViewIceState;
	private TextView mTextViewCodec;
	private TextView mTextViewJitter;
	private TextView mTextViewPacketLoss;
	private TextView mTextViewLatePackets;
	private TextView mTextViewRoundTripDelay;

	private final class SimlarServiceCommunicatorConnectionDetails extends SimlarServiceCommunicator
	{
		@Override
		public void onBoundToSimlarService()
		{
			ConnectionDetailsActivity.this.onCallConnectionDetailsChanged();
		}

		@Override
		public void onCallConnectionDetailsChanged()
		{
			ConnectionDetailsActivity.this.onCallConnectionDetailsChanged();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_connection_details);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

		mTextViewQuality = (TextView) findViewById(R.id.textViewQuality);
		mTextViewUpload = (TextView) findViewById(R.id.textViewUpload);
		mTextViewDownload = (TextView) findViewById(R.id.textViewDownload);
		mTextViewIceState = (TextView) findViewById(R.id.textViewIceState);
		mTextViewCodec = (TextView) findViewById(R.id.textViewCodec);
		mTextViewJitter = (TextView) findViewById(R.id.textViewJitter);
		mTextViewPacketLoss = (TextView) findViewById(R.id.textViewPacketLoss);
		mTextViewLatePackets = (TextView) findViewById(R.id.textViewLatePackets);
		mTextViewRoundTripDelay = (TextView) findViewById(R.id.textViewRoundTripDelay);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Lg.i("onStart");

		if (!mCommunicator.register(this, CallActivity.class)) {
			finish();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Lg.i("onResume");
	}

	@Override
	protected void onPause()
	{
		Lg.i("onPause");
		super.onPause();
	}

	@Override
	protected void onStop()
	{
		Lg.i("onStop");
		mCommunicator.unregister();
		super.onStop();
	}

	private void onCallConnectionDetailsChanged()
	{
		if (mCommunicator.getService() == null) {
			Lg.e("ERROR: onSimlarCallStateChanged but not bound to service");
			return;
		}

		final CallConnectionDetails callConnectionDetails = mCommunicator.getService().getCallConnectionDetails();

		if (callConnectionDetails == null) {
			Lg.e("ERROR: onCallConnectionDetailsChanged but callConnectionDetails null or empty");
			return;
		}

		if (callConnectionDetails.isEndedCall()) {
			ConnectionDetailsActivity.this.finish();
		}

		if (callConnectionDetails.hasConnectionInfo()) {
			mTextViewQuality.setText(getString(callConnectionDetails.getQualityDescription()));
			mTextViewUpload.setText(getString(R.string.connection_details_activity_kilobytes_per_second, callConnectionDetails.getUpload()));
			mTextViewDownload.setText(getString(R.string.connection_details_activity_kilobytes_per_second, callConnectionDetails.getDownload()));
			mTextViewIceState.setText(callConnectionDetails.getIceState());
			mTextViewCodec.setText(callConnectionDetails.getCodec());
			mTextViewJitter.setText(callConnectionDetails.getJitter());
			mTextViewPacketLoss.setText(getString(R.string.connection_details_activity_percent, callConnectionDetails.getPacketLoss()));
			mTextViewLatePackets.setText(callConnectionDetails.getLatePackets());
			mTextViewRoundTripDelay.setText(getString(R.string.connection_details_activity_milli_seconds, callConnectionDetails.getRoundTripDelay()));
		}
	}
}

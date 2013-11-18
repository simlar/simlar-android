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
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class VolumesControlActivity extends Activity
{
	static final String LOGTAG = VolumesControlActivity.class.getSimpleName();

	SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorVolumes();

	Volumes mVolumes = null;

	private class SimlarServiceCommunicatorVolumes extends SimlarServiceCommunicator
	{
		public SimlarServiceCommunicatorVolumes()
		{
			super(LOGTAG);
		}

		@Override
		void onBoundToSimlarService()
		{
			VolumesControlActivity.this.setVolumes();
		}

		@Override
		void onSimlarCallStateChanged()
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
				VolumesControlActivity.this.finish();
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_volumes_control);

		SeekBar seekBarSpeaker = (SeekBar) findViewById(R.id.seekBarSpeaker);
		SeekBar seekBarMicrophone = (SeekBar) findViewById(R.id.seekBarMicrophone);

		seekBarSpeaker.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar)
			{
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar)
			{
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
			{
				Log.i(LOGTAG, "seekBarSpeaker changed: " + progress);
				if (mVolumes == null || mCommunicator == null) {
					return;
				}

				mVolumes = mVolumes.setProgressSpeaker(progress);
				mCommunicator.getService().setVolumes(mVolumes);
			}
		});

		seekBarMicrophone.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar)
			{
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar)
			{
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
			{
				Log.i(LOGTAG, "seekBarMicrophone changed: " + progress);
				if (mVolumes == null || mCommunicator == null) {
					return;
				}

				mVolumes = mVolumes.setProgressMicrophone(progress);
				mCommunicator.getService().setVolumes(mVolumes);
			}
		});
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

	@SuppressWarnings("unused")
	public void toggleSpeakerMuted(View view)
	{
		mVolumes = mVolumes.toggleExternalSpeaker();
		mCommunicator.getService().setVolumes(mVolumes);
		setButtonSpeakerMute();
	}

	@SuppressWarnings("unused")
	public void toggleMicrophoneMuted(View view)
	{
		mVolumes = mVolumes.toggleMicrophoneMuted();
		mCommunicator.getService().setVolumes(mVolumes);
		setButtonMicophoneMute();
	}

	void setVolumes()
	{
		mVolumes = mCommunicator.getService().getVolumes();

		SeekBar seekBarSpeaker = (SeekBar) findViewById(R.id.seekBarSpeaker);
		SeekBar seekBarMicrophone = (SeekBar) findViewById(R.id.seekBarMicrophone);

		seekBarSpeaker.setProgress(mVolumes.getProgressSpeaker());
		seekBarMicrophone.setProgress(mVolumes.getProgessMicrophone());

		setButtonSpeakerMute();
		setButtonMicophoneMute();
	}

	private void setButtonSpeakerMute()
	{
		ImageButton button = (ImageButton) findViewById(R.id.imageButtonSpeaker);

		if (mVolumes.getExternalSpeaker()) {
			button.setImageResource(R.drawable.volume_control_activity_speaker_external);
		} else {
			button.setImageResource(R.drawable.volume_control_activity_speaker_internal);
		}
	}

	private void setButtonMicophoneMute()
	{
		ImageButton button = (ImageButton) findViewById(R.id.imageButtonMicrophone);

		if (mVolumes.getMicrophoneMuted()) {
			button.setImageResource(R.drawable.volume_control_activity_microphone_muted);
		} else {
			button.setImageResource(R.drawable.volume_control_activity_microphone);
		}
	}
}

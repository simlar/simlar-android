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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import org.simlar.R;
import org.simlar.helper.Volumes;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarCallState;
import org.simlar.service.SimlarServiceCommunicator;

public final class VolumesControlActivity extends Activity
{
	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorVolumes();

	private Volumes mVolumes = null;
	private SeekBar mSeekBarSpeaker;
	private SeekBar mSeekBarMicrophone;
	private CheckBox mCheckBoxEchoLimiter;

	private final class SimlarServiceCommunicatorVolumes extends SimlarServiceCommunicator
	{
		@Override
		public void onBoundToSimlarService()
		{
			VolumesControlActivity.this.setVolumes();
		}

		@Override
		public void onSimlarCallStateChanged()
		{
			if (getService() == null) {
				Lg.e("service is null");
				return;
			}

			final SimlarCallState simlarCallState = getService().getSimlarCallState();
			if (simlarCallState == null || simlarCallState.isEmpty()) {
				Lg.e("ERROR: onSimlarCallStateChanged simlarCallState null or empty");
				return;
			}

			if (simlarCallState.isEndedCall()) {
				VolumesControlActivity.this.finish();
			}
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_volumes_control);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

		mSeekBarSpeaker = (SeekBar) findViewById(R.id.seekBarSpeaker);
		mSeekBarMicrophone = (SeekBar) findViewById(R.id.seekBarMicrophone);
		mCheckBoxEchoLimiter = (CheckBox) findViewById(R.id.checkBoxEchoLimiter);

		mSeekBarSpeaker.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

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
				Lg.i("seekBarSpeaker changed: ", progress);
				if (mVolumes == null) {
					return;
				}

				mVolumes = mVolumes.setProgressSpeaker(progress);
				mCommunicator.getService().setVolumes(mVolumes);
			}
		});

		mSeekBarMicrophone.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(final SeekBar seekBar)
			{
			}

			@Override
			public void onStartTrackingTouch(final SeekBar seekBar)
			{
			}

			@Override
			public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser)
			{
				Lg.i("seekBarMicrophone changed: ", progress);
				if (mVolumes == null) {
					return;
				}

				mVolumes = mVolumes.setProgressMicrophone(progress);
				mCommunicator.getService().setVolumes(mVolumes);
			}
		});

		mCheckBoxEchoLimiter.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked)
			{
				Lg.i("CheckBoxEchoLimiter.onCheckedChanged: ", isChecked);
				if (mVolumes == null) {
					return;
				}

				if (mVolumes.getEchoLimiter() == isChecked) {
					return;
				}

				mVolumes = mVolumes.toggleEchoLimiter();
				mCommunicator.getService().setVolumes(mVolumes);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	@Override
	protected void onResume()
	{
		Lg.i("onResume");
		super.onResume();
		if (!mCommunicator.register(this, CallActivity.class)) {
			finish();
		}
	}

	@Override
	protected void onPause()
	{
		Lg.i("onPause");
		mCommunicator.unregister();
		super.onPause();
	}

	private void setVolumes()
	{
		mVolumes = mCommunicator.getService().getVolumes();

		mSeekBarSpeaker.setProgress(mVolumes.getProgressSpeaker());
		mSeekBarMicrophone.setProgress(mVolumes.getProgressMicrophone());
		mCheckBoxEchoLimiter.setChecked(mVolumes.getEchoLimiter());
	}
}

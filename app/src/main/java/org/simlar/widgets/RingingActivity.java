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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarCallState;
import org.simlar.service.SimlarServiceCommunicator;
import org.simlar.utils.Util;

import java.util.ArrayList;
import java.util.List;

public final class RingingActivity extends AppCompatActivity
{
	private final List<View> mCircles = new ArrayList<>();
	private final SimlarServiceCommunicator mCommunicator = new SimlarServiceCommunicatorRinging();

	private final class SimlarServiceCommunicatorRinging extends SimlarServiceCommunicator
	{
		@Override
		public void onBoundToSimlarService()
		{
			RingingActivity.this.onSimlarCallStateChanged();
		}

		@Override
		public void onSimlarCallStateChanged()
		{
			RingingActivity.this.onSimlarCallStateChanged();
		}

		@Override
		public void onServiceFinishes()
		{
			RingingActivity.this.finish();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Lg.i("onCreate");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_ringing);

		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
				WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

		final Animation logoAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_logo);
		final ImageView logo = (ImageView) findViewById(R.id.logo);
		logo.startAnimation(logoAnimation);

		createCircles();
		animateCircles();
	}

	private void createCircles()
	{
		final int diameter = Math.round(250.0f * getResources().getDisplayMetrics().density);
		final RelativeLayout mainLayout = (RelativeLayout) findViewById(R.id.layoutRingingActivity);

		for (int i = 0; i < 3; i++) {
			final View circle = new View(this);
			Util.setBackgroundCompatible(circle, Util.getDrawableCompatible(getResources(), R.drawable.circle));

			final RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(diameter, diameter);
			layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
			circle.setLayoutParams(layoutParams);
			mainLayout.addView(circle);

			mCircles.add(circle);
		}
	}

	private void animateCircles()
	{
		final long animationStartTime = AnimationUtils.currentAnimationTimeMillis() + 100;
		int i = 0;
		for (final View circle : mCircles) {
			final Animation circleAnimation = AnimationUtils.loadAnimation(this, R.anim.ringing_circle);
			circleAnimation.setStartTime(animationStartTime + (i++) * 250);
			circleAnimation.setFillAfter(true);
			circle.setAnimation(circleAnimation);

			if (i == mCircles.size()) {
				circleAnimation.setAnimationListener(new Animation.AnimationListener()
				{
					@Override
					public void onAnimationStart(Animation animation)
					{
					}

					@Override
					public void onAnimationEnd(Animation animation)
					{
						RingingActivity.this.animateCircles();
					}

					@Override
					public void onAnimationRepeat(Animation animation)
					{
					}
				});
			}
		}
	}

	@Override
	protected void onResume()
	{
		Lg.i("onResume");
		super.onResume();
		if (!mCommunicator.register(this, RingingActivity.class)) {
			Lg.w("SimlarService is not running, starting MainActivity");
			startActivity(new Intent(this, MainActivity.class));
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

	@Override
	public void onStop()
	{
		Lg.i("onStop");
		super.onStop();
	}

	@Override
	public void onDestroy()
	{
		Lg.i("onDestroy");
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		return true;
	}

	private void onSimlarCallStateChanged()
	{
		if (mCommunicator.getService() == null) {
			Lg.e("ERROR: onSimlarCallStateChanged but not bound to service");
			return;
		}

		final SimlarCallState simlarCallState = mCommunicator.getService().getSimlarCallState();
		if (simlarCallState == null || simlarCallState.isEmpty()) {
			Lg.e("ERROR: onSimlarCallStateChanged simlarCallState null or empty");
			return;
		}

		Lg.i("onSimlarCallStateChanged ", simlarCallState);

		if (simlarCallState.isEndedCall()) {
			finish();
		}

		final ImageView contactImage = (ImageView) findViewById(R.id.contactImage);
		final TextView contactName = (TextView) findViewById(R.id.contactName);

		contactImage.setImageBitmap(simlarCallState.getContactPhotoBitmap(this, R.drawable.contact_picture));
		contactName.setText(simlarCallState.getContactName());
	}

	@SuppressWarnings("unused")
	public void pickUp(final View view)
	{
		mCommunicator.getService().pickUp();
		startActivity(new Intent(this, CallActivity.class));
		finish();
	}

	@SuppressWarnings("unused")
	public void terminateCall(final View view)
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

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

import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.List;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.service.SimlarCallState;
import org.simlar.service.SimlarServiceCommunicator;
import org.simlar.utils.Util;

import static androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;

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
			finish();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Lg.i("onCreate");

		setContentView(R.layout.activity_ringing);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layoutRingingActivity), (v, insets) -> {
			final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
			v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});


		//Util.edge2edgeLayout(findViewById(R.id.layoutRingingActivity));

		/*

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layoutRingingActivity), (v, insets) -> {
			final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			//final Insets systemBars2 = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			//v.setPadding(0, systemBars.top, 0, 0);
			v.setBackgroundColor(Color.BLACK);
			//v.setBackgroundColor(Color.WHITE);
			Lg.i("WTF xx ", systemBars.top);
			return insets;
		});

		*/
		//new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).hide(WindowInsetsCompat.Type.navigationBars());
		//getWindow().getInsetsController().hide(WindowInsetsCompat.Type.navigationBars());
		WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).hide(WindowInsetsCompat.Type.systemBars());
		//WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setSystemBarsBehavior(BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);


		Lg.i("****WTF 7 ***");

		//ActivityCompat.set
		//if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			//getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		//}


		//setTurnScreenOn(true);
		//setShowWhenLocked(true);

		/*
		new KeyguardManager().requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
			@Override
			public void onDismissError()
			{
				//super.onDismissError();
			}

			@Override
			public void onDismissSucceeded()
			{
				super.onDismissSucceeded();
			}

			@Override
			public void onDismissCancelled()
			{
				super.onDismissCancelled();
			}
		});

		 */
		/*
	   getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
                                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
*/
		/*
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE              |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
                                                              View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
                                                              View.SYSTEM_UI_FLAG_FULLSCREEN);

		 */

		getWindow().addFlags(//WindowManager.LayoutParams.FLAG_FULLSCREEN |
				WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
				WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

		/*
		// make sure this activity is shown even if the phone is locked
		getWindow().addFlags(//WindowManager.LayoutParams.FLAG_FULLSCREEN |
				//WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
				//WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
				WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

		 */

		final Animation logoAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_logo);
		final ImageView logo = findViewById(R.id.logo);
		logo.startAnimation(logoAnimation);

		createCircles();
		animateCircles();
	}

	private void createCircles()
	{
		final int diameter = Math.round(250.0f * getResources().getDisplayMetrics().density);
		final RelativeLayout mainLayout = findViewById(R.id.layoutRingingActivity);

		for (int i = 0; i < 3; i++) {
			final View circle = new View(this);
			circle.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.circle, null));

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
			circleAnimation.setStartTime(animationStartTime + i++ * 250L);
			circleAnimation.setFillAfter(true);
			circle.setAnimation(circleAnimation);

			if (i == mCircles.size()) {
				circleAnimation.setAnimationListener(new Animation.AnimationListener()
				{
					@Override
					public void onAnimationStart(final Animation animation)
					{
					}

					@Override
					public void onAnimationEnd(final Animation animation)
					{
						animateCircles();
					}

					@Override
					public void onAnimationRepeat(final Animation animation)
					{
					}
				});
			}
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Lg.i("onStart");

		if (!mCommunicator.register(this, RingingActivity.class)) {
			Lg.w("SimlarService is not running, starting MainActivity");
			startActivity(new Intent(this, MainActivity.class));
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
	public void onStop()
	{
		Lg.i("onStop");
		mCommunicator.unregister();
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

		final ImageView contactImage = findViewById(R.id.contactImage);
		final TextView contactName = findViewById(R.id.contactName);

		contactImage.setImageBitmap(simlarCallState.getContactPhotoBitmap(this, R.drawable.contact_picture));
		contactName.setText(simlarCallState.getContactName());
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
	public void pickUp(final View view)
	{
		mCommunicator.getService().pickUp();
		startActivity(new Intent(this, CallActivity.class));
		finish();
	}

	@SuppressWarnings({ "unused", "RedundantSuppression" })
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

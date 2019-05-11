/**
 * Copyright (C) 2015 The Simlar Authors.
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


import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.simlar.R;
import org.simlar.helper.VideoSize;
import org.simlar.logging.Lg;

@SuppressWarnings("WeakerAccess") /// liblinphone requires this class to be pubic
public class VideoFragment extends Fragment
{
	private Listener mListener = null;

	// gui elements
	private TextureView mVideoView = null;
	private TextureView mCaptureView = null;
	private View mInitializingView = null;

	public interface Listener
	{
		void setVideoWindows(final TextureView videoView, final TextureView captureView);
		void destroyVideoWindows();
		void onVideoViewClick();
		void onCaptureViewClick();
		VideoSize getVideoPreviewSize();
	}

	@Override
	public final void onAttach(@NonNull final Context context)
	{
		super.onAttach(context);
		Lg.i("onAttach");

		if (!(context instanceof Listener)) {
			Lg.e("not attached to listener object");
			return;
		}

		mListener = (Listener) context;
	}

	@Override
	public final void onDetach()
	{
		Lg.i("onDetach");

		if (mListener == null) {
			Lg.e("onDetach: no listener registered => not destroying potential video");
		} else {
			mListener.destroyVideoWindows();
			mListener = null;
		}

		super.onDetach();
	}

	@Override
	public final View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		Lg.i("onCreateView");
		final View view = inflater.inflate(R.layout.fragment_video, container, false);

		mVideoView = view.findViewById(R.id.videoSurface);
		mCaptureView = view.findViewById(R.id.videoCaptureSurface);
		mInitializingView = view.findViewById(R.id.layoutInitializingVideo);

		if (mListener == null) {
			Lg.e("onCreateView, no listener registered => not initializing video");
		} else {
			mListener.setVideoWindows(mVideoView, mCaptureView);
		}

		mVideoView.setOnClickListener(view12 -> {
			if (mListener != null) {
				mListener.onVideoViewClick();
			}
		});

		mCaptureView.setOnClickListener(view1 -> {
			if (mListener != null) {
				mListener.onCaptureViewClick();
			}
		});

		return view;
	}

	@Override
	public final void onDestroy()
	{
		Lg.i("onDestroy");

		if (mCaptureView != null) {
			mCaptureView.setOnTouchListener(null);
			mCaptureView = null;
		}

		if (mVideoView != null) {
			mVideoView.setOnTouchListener(null);
			mVideoView = null;
		}

		super.onDestroy();
	}

	@Override
	public final void onStart()
	{
		super.onStart();
		Lg.i("onStart");
	}

	@Override
	public final void onResume()
	{
		super.onResume();
		Lg.i("onResume");

		resizePreview();
	}

	private void resizePreview() {
		mListener.getVideoPreviewSize();

/*
		Core core = LinphoneService.getCore();
		if (core.getCallsNb() > 0) {
			Call call = core.getCurrentCall();
			if (call == null) {
				call = core.getCalls()[0];
			}
			if (call == null) return;

			DisplayMetrics metrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);
			int screenHeight = metrics.heightPixels;
			int maxHeight =
					screenHeight / 4; // Let's take at most 1/4 of the screen for the camera preview

			VideoDefinition videoSize =
					call.getCurrentParams()
							.getSentVideoDefinition(); // It already takes care of rotation
			if (videoSize.getWidth() == 0 || videoSize.getHeight() == 0) {
				Log.w(
						"[Video] Couldn't get sent video definition, using default video definition");
				videoSize = core.getPreferredVideoDefinition();
			}
			int width = videoSize.getWidth();
			int height = videoSize.getHeight();

			Log.d("[Video] Video height is " + height + ", width is " + width);
			width = width * maxHeight / height;
			height = maxHeight;

			if (mCaptureView == null) {
				Log.e("[Video] mCaptureView is null !");
				return;
			}

			RelativeLayout.LayoutParams newLp = new RelativeLayout.LayoutParams(width, height);
			newLp.addRule(
					RelativeLayout.ALIGN_PARENT_BOTTOM,
					1); // Clears the rule, as there is no removeRule until API 17.
			newLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 1);
			mCaptureView.setLayoutParams(newLp);
			Log.d("[Video] Video preview size set to " + width + "x" + height);
		}*/
	}

	@Override
	public final void onPause()
	{
		Lg.i("onPause");
		super.onPause();
	}

	@Override
	public final void onStop()
	{
		Lg.i("onStop");
		super.onStop();
	}

	public final void setNowPlaying()
	{
		if (mInitializingView == null) {
			Lg.e("called setNowPlaying too early");
			return;
		}
		mInitializingView.setVisibility(View.GONE);
	}
}

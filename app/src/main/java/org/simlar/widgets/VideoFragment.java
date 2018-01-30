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
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.simlar.R;
import org.simlar.logging.Lg;

public class VideoFragment extends Fragment
{
	private Listener mListener = null;

	// gui elements
	private GLSurfaceView mVideoView = null;
	private SurfaceView mCaptureView = null;
	private ProgressBar mProgressBarInitializing = null;

	public interface Listener
	{
		void setVideoWindows(final SurfaceView videoView, final SurfaceView captureView);
		void enableVideoWindow(final boolean enable);
		void destroyVideoWindows();
		void onVideoViewClick();
		void onCaptureViewClick();
	}

	@Override
	public final void onAttach(final Context context)
	{
		super.onAttach(context);
		Lg.i("onAttach");

		if ( ! (context instanceof Listener )) {
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
			Lg.e("onDestroy: no listener registered => not destroying potential video");
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
		mProgressBarInitializing = view.findViewById(R.id.progressBarInitializingVideo);

		fixZOrder();

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

	private void fixZOrder()
	{
		mVideoView.setZOrderOnTop(false);
		mCaptureView.setZOrderOnTop(true);
		mCaptureView.setZOrderMediaOverlay(true);
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

		if (mVideoView != null) {
			mVideoView.onResume();
		}

		enableVideoWindow(true);
	}

	@Override
	public final void onResume()
	{
		super.onResume();
		Lg.i("onResume");
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

		enableVideoWindow(false);

		if (mVideoView != null) {
			mVideoView.onPause();
		}

		super.onStop();
	}

	private void enableVideoWindow(final boolean enable)
	{
		if (mListener == null) {
			Lg.e("no listener");
			return;
		}

		Lg.i("enableVideoWindow enable=", enable);
		mListener.enableVideoWindow(enable);
	}

	public final void setNowPlaying()
	{
		mProgressBarInitializing.setVisibility(View.GONE);
	}
}

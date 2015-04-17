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


import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import org.simlar.R;
import org.simlar.logging.Lg;

public class VideoFragment extends android.support.v4.app.Fragment
{
	private Listener mListener;

	// gui elements
	private GLSurfaceView mVideoView;
	private SurfaceView mCaptureView;

	public interface Listener
	{
		void setVideoWindows(final SurfaceView videoView, final SurfaceView captureView);
		void enableVideoWindow(final boolean enable);
		void destroyVideoWindows();
	}

	@Override
	public void onAttach(final Activity activity)
	{
		Lg.i("onAttach");
		super.onAttach(activity);
		try {
			mListener = (Listener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement VideoFragment.Listener");
		}
	}

	@Override
	public void onDetach()
	{
		Lg.i("onDetach");
		super.onDetach();

		if (mListener == null) {
			Lg.e("onDestroy: no listener registered => not destroying potential video");
		} else {
			mListener.destroyVideoWindows();
			mListener = null;
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		Lg.i("onCreateView");
		final View view = inflater.inflate(R.layout.fragment_video, container, false);

		mVideoView = (GLSurfaceView) view.findViewById(R.id.videoSurface);
		mCaptureView = (SurfaceView) view.findViewById(R.id.videoCaptureSurface);

		fixZOrder();

		if (mListener == null) {
			Lg.e("onCreateView, no listener registered => not initializing video");
		} else {
			mListener.setVideoWindows(mVideoView, mCaptureView);
		}

		return view;
	}

	private void fixZOrder()
	{
		mVideoView.setZOrderOnTop(false);
		mCaptureView.setZOrderOnTop(true);
		mCaptureView.setZOrderMediaOverlay(true);
	}

	@Override
	public void onDestroy()
	{
		Lg.i("onDestroy");

		mCaptureView = null;
		mVideoView = null;

		super.onDestroy();
	}

	@Override
	public void onResume()
	{
		Lg.i("onResume");
		super.onResume();

		if (mVideoView != null) {
			mVideoView.onResume();
		}

		enableVideoWindow(true);
	}

	@Override
	public void onPause()
	{
		Lg.i("onPause");

		enableVideoWindow(false);

		if (mVideoView != null) {
			mVideoView.onPause();
		}

		super.onPause();
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
}

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
import org.simlar.logging.Lg;

@SuppressWarnings("WeakerAccess") /// liblinphone requires this class to be pubic
public class VideoFragment extends Fragment
{
	private Listener mListener = null;

	// gui elements
	private TextureView mVideoView = null;
	private TextureView mCaptureView = null;

	public interface Listener
	{
		void setVideoWindows(final TextureView videoView, final TextureView captureView);
		void destroyVideoWindows();
		void onVideoViewClick();
		void onCaptureViewClick();
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
			//view.animate().setDuration(2000).rotationYBy(180);
			//mCaptureView.animate().setDuration(2000).scaleX(2).scaleY(2);
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
//		final ViewGroup.LayoutParams params = mCaptureView.getLayoutParams();
//		params.width = (int)(120 * getResources().getDisplayMetrics().density);
//		params.height = (int)(145 * getResources().getDisplayMetrics().density);
//		mCaptureView.setLayoutParams(params);
		mCaptureView.animate().setDuration(2000).scaleXBy(2).scaleYBy(2);
	}
}

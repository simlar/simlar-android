/*
 * Copyright (C) The Simlar Authors.
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
 *
 */
package org.simlar.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

import org.simlar.logging.Lg;

public final class BluetoothManager {
    private BluetoothHeadset mBluetoothHeadset = null;
    private BroadcastReceiver mBluetoothReceiver = null;
    private BroadcastReceiver mBluetoothScoReceiver = null;

    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private final Context mContext;
    private final Listener mListener;

    @FunctionalInterface
    public interface Listener {
        void onBlueToothHeadsetAvailable(final boolean available);
    }

    public BluetoothManager(final Context context, final Listener listener)
    {
        mContext = context;
        mListener = listener;

        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.getProfileProxy(mContext, new BluetoothProfile.ServiceListener()
            {
                @Override
                public void onServiceConnected(final int profile, final BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HEADSET) {
                        mBluetoothHeadset = (BluetoothHeadset) proxy;

                        Lg.i("registering bluetooth receiver");
                        mBluetoothReceiver = new BroadcastReceiver()
                        {
                            @Override
                            public void onReceive(final Context context, final Intent intent)
                            {
                                final int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -2);
                                final boolean connected = state == BluetoothProfile.STATE_CONNECTED;
                                Lg.i("bluetooth receive state: ", state, " => headset connected: ", connected);
                                mListener.onBlueToothHeadsetAvailable(connected);
                            }
                        };
                        mContext.registerReceiver(mBluetoothReceiver, new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));

                        mBluetoothScoReceiver = new BroadcastReceiver()
                        {
                            @Override
                            public void onReceive(final Context context, final Intent intent)
                            {
                                final int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -2);
                                final boolean connected = state == AudioManager.SCO_AUDIO_STATE_CONNECTED;
                                Lg.i("bluetooth sco receive state: ", state, " => connected: ", connected);
                                mListener.onBlueToothHeadsetAvailable(connected);
                            }
                        };
                        mContext.registerReceiver(mBluetoothScoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
                    }
                }

                @Override
                public void onServiceDisconnected(final int profile) {
                    if (profile == BluetoothProfile.HEADSET && mBluetoothHeadset != null) {
                        Lg.i("bluetooth service disconnected");
                        mBluetoothHeadset = null;
                    }
                }
            }, BluetoothProfile.HEADSET);
        }
    }

    public void destroy()
    {
        if (mBluetoothAdapter != null && mBluetoothHeadset != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
        }

        if (mBluetoothReceiver != null) {
            mContext.unregisterReceiver(mBluetoothReceiver);
        }

        if (mBluetoothScoReceiver != null) {
            mContext.unregisterReceiver(mBluetoothScoReceiver);
        }
    }
}

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
import android.content.Context;

import org.simlar.logging.Lg;

public final class BluetoothManager {
    private BluetoothHeadset mBluetoothHeadset = null;
    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    public BluetoothManager(final Context context)
    {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener()
            {
                @Override
                public void onServiceConnected(final int profile, final BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HEADSET) {
                        mBluetoothHeadset = (BluetoothHeadset) proxy;
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
    }
}

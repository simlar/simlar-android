/*
 * Copyright (C) 2013 - 2020 The Simlar Authors.
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import org.simlar.logging.Lg;

public final class BluetoothReceiverLinphone extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        onReceiveLinphone(context, intent);
    }

    public void onReceiveLinphone(Context context, Intent intent) {
        String action = intent.getAction();

if (! "ben".equals(action)) {
    return;
}

        Lg.i("[Bluetooth] Bluetooth broadcast received");

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    Lg.w("[Bluetooth] Adapter has been turned off");
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    Lg.w("[Bluetooth] Adapter is being turned off");
                    break;
                case BluetoothAdapter.STATE_ON:
                    Lg.i("[Bluetooth] Adapter has been turned on");
                    //LinphoneManager.getAudioManager().bluetoothAdapterStateChanged();
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    Lg.i("[Bluetooth] Adapter is being turned on");
                    break;
                case BluetoothAdapter.ERROR:
                    Lg.e("[Bluetooth] Adapter is in error state !");
                    break;
                default:
                    Lg.w("[Bluetooth] Unknown adapter state: ", state);
                    break;
            }
        } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
            int state =
                    intent.getIntExtra(
                            BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
            if (state == BluetoothHeadset.STATE_CONNECTED) {
                Lg.i("[Bluetooth] Bluetooth headset connected");
                Lg.w("mListener.bluetoothHeadetConnectionChanged(true)");
            } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                Lg.i("[Bluetooth] Bluetooth headset disconnected");
                Lg.w("mListener.bluetoothHeadetConnectionChanged(false)");
            } else {
                Lg.w("[Bluetooth] Bluetooth headset unknown state changed: " + state);
            }
        } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
            int state =
                    intent.getIntExtra(
                            BluetoothHeadset.EXTRA_STATE,
                            BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
            if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                Lg.i("[Bluetooth] Bluetooth headset audio connected");
                Lg.w("mListener.bluetoothHeadetAudioConnectionChanged(true)");
            } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                Lg.i("[Bluetooth] Bluetooth headset audio disconnected");
                Lg.w("mListener.bluetoothHeadetAudioConnectionChanged(false)");
            } else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
                Lg.i("[Bluetooth] Bluetooth headset audio connecting");
            } else {
                Lg.w("[Bluetooth] Bluetooth headset unknown audio state changed: " + state);
            }
        } else if (action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
            int state =
                    intent.getIntExtra(
                            AudioManager.EXTRA_SCO_AUDIO_STATE,
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                Lg.i("[Bluetooth] Bluetooth headset SCO connected");
                Lg.w("mListener.bluetoothHeadetScoConnectionChanged(true)");
            } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                Lg.i("[Bluetooth] Bluetooth headset SCO disconnected");
                Lg.w("mListener.bluetoothHeadetScoConnectionChanged(false)");
            } else if (state == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
                Lg.i("[Bluetooth] Bluetooth headset SCO connecting");
            } else if (state == AudioManager.SCO_AUDIO_STATE_ERROR) {
                Lg.i("[Bluetooth] Bluetooth headset SCO connection error");
            } else {
                Lg.w("[Bluetooth] Bluetooth headset unknown SCO state changed: " + state);
            }
        } else if (action.equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
            String command =
                    intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
            int type =
                    intent.getIntExtra(
                            BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1);

            String commandType;
            switch (type) {
                case BluetoothHeadset.AT_CMD_TYPE_ACTION:
                    commandType = "AT Action";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_READ:
                    commandType = "AT Read";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_TEST:
                    commandType = "AT Test";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_SET:
                    commandType = "AT Set";
                    break;
                case BluetoothHeadset.AT_CMD_TYPE_BASIC:
                    commandType = "AT Basic";
                    break;
                default:
                    commandType = "AT Unknown";
                    break;
            }
            Lg.i("[Bluetooth] Vendor action " + commandType + " : " + command);
        } else {
            Lg.w("[Bluetooth] Bluetooth unknown action: " + action);
        }
    }
}

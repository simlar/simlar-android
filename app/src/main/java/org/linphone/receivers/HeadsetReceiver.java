/*
 * Copyright (C) 2013 - 2015 The Simlar Authors.
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
package org.linphone.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import org.simlar.logging.Lg;

public class HeadsetReceiver extends BroadcastReceiver {
    Listener mListener;

    public interface Listener {
        void routeAudioToEarPiece();
    }

    public HeadsetReceiver(final Listener mListener)
    {
        this.mListener = mListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isInitialStickyBroadcast()) {
            Lg.i("[Headset] Received broadcast from sticky cache, ignoring...");
            return;
        }

        String action = intent.getAction();
        if (action.equals(AudioManager.ACTION_HEADSET_PLUG)) {
            // This happens when the user plugs a Jack headset to the device for example
            // https://developer.android.com/reference/android/media/AudioManager.html#ACTION_HEADSET_PLUG
            int state = intent.getIntExtra("state", 0);
            String name = intent.getStringExtra("name");
            int hasMicrophone = intent.getIntExtra("microphone", 0);

            if (state == 0) {
                Lg.i("[Headset] Headset disconnected:" + name);
            } else if (state == 1) {
                Lg.i("[Headset] Headset connected:" + name);
                if (hasMicrophone == 1) {
                    Lg.i("[Headset] Headset " + name + " has a microphone");
                }
            } else {
                Lg.w("[Headset] Unknown headset plugged state: " + state);
            }


            mListener.routeAudioToEarPiece();
        } else if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            // This happens when the user disconnect a headset, so we shouldn't play audio loudly
            Lg.i("[Headset] Noisy state detected, most probably a headset has been disconnected");
            mListener.routeAudioToEarPiece();
        } else {
            Lg.w("[Headset] Unknown action: " + action);
        }
    }
}

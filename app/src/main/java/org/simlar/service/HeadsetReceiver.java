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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;

import org.simlar.logging.Lg;

public final class HeadsetReceiver extends BroadcastReceiver {
    private final Listener mListener;

    @FunctionalInterface
    public interface Listener {
        void onWiredHeadsetConnected(final boolean connected);
    }

    public HeadsetReceiver(final Listener mListener)
    {
        this.mListener = mListener;
    }

    public void registerReceiver(final Context context)
    {
        final String action = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? AudioManager.ACTION_HEADSET_PLUG
                : Intent.ACTION_HEADSET_PLUG;
        context.registerReceiver(this, new IntentFilter(action));
    }

    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        final String action = intent.getAction();
        if (!AudioManager.ACTION_HEADSET_PLUG.equals(action)) {
            Lg.e("received unknown action: ", action);
            return;
        }

        final int state = intent.getIntExtra("state", -1);
        final boolean connected = state != 0;
        Lg.i("received state: ", state, " -> wired headset connected: ", connected);
        mListener.onWiredHeadsetConnected(connected);
    }
}

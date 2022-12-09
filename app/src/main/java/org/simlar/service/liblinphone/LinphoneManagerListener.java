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

package org.simlar.service.liblinphone;

import java.util.Set;

import org.linphone.core.Call.State;
import org.linphone.core.RegistrationState;

import org.simlar.helper.CallEndReason;
import org.simlar.helper.NetworkQuality;
import org.simlar.helper.VideoState;
import org.simlar.service.AudioOutputType;

public interface LinphoneManagerListener
{
	void onRegistrationStateChanged(final RegistrationState state);

	void onCallStatsChanged(final NetworkQuality quality, final int callDuration, final String codec, final String iceState,
	                        final int upload, final int download, final int jitter, final int packetLoss, final long latePackets,
	                        final int roundTripDelay, final String encryptionDescription);

	void onCallStateChanged(final String number, final State callState, final CallEndReason callEndReason);

	void onCallEncryptionChanged(final String authenticationToken, final boolean authenticationTokenVerified);

	void onVideoStateChanged(final VideoState videoState);

	void onAudioOutputChanged(final AudioOutputType currentAudioOutputType, final Set<AudioOutputType> availableAudioOutputTypes);
}

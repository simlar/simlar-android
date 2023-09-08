/**
 * Copyright (C) 2013 - 2014 The Simlar Authors.
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

import android.content.Context;

import org.linphone.core.Call;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

/// TODO: think about renaming to SimlarCallState
public enum LinphoneCallState
{
	IDLE,
	INCOMING_RECEIVED,
	OUTGOING_INIT,
	OUTGOING_PROGRESS,
	OUTGOING_RINGING,
	OUTGOING_EARLY_MEDIA,
	CONNECTED,
	STREAMS_RUNNING,
	PAUSING,
	PAUSED,
	RESUMING,
	REFERED,
	ERROR,
	CALL_END,
	PAUSED_BY_REMOTE,
	UPDATED_BY_REMOTE,
	INCOMING_EARLY_MEDIA,
	UPDATING,
	RELEASED,
	EARLY_UPDATED_BY_REMOTE,
	EARLY_UPDATING,
	UNKNOWN;

	public static LinphoneCallState fromLinphoneCallState(final Call.State state)
	{
		if (state == null) {
			Lg.e("ERROR: fromLinphoneCallState: state is null");
			return UNKNOWN;
		}

		return switch (state) {
			case Idle -> IDLE;
			case IncomingReceived, PushIncomingReceived -> INCOMING_RECEIVED;
			case OutgoingInit -> OUTGOING_INIT;
			case OutgoingProgress -> OUTGOING_PROGRESS;
			case OutgoingRinging -> OUTGOING_RINGING;
			case OutgoingEarlyMedia -> OUTGOING_EARLY_MEDIA;
			case Connected -> CONNECTED;
			case StreamsRunning -> STREAMS_RUNNING;
			case Pausing -> PAUSING;
			case Paused -> PAUSED;
			case Resuming -> RESUMING;
			case Referred -> REFERED;
			case Error -> ERROR;
			case End -> CALL_END;
			case PausedByRemote -> PAUSED_BY_REMOTE;
			case UpdatedByRemote -> UPDATED_BY_REMOTE;
			case IncomingEarlyMedia -> INCOMING_EARLY_MEDIA;
			case Updating -> UPDATING;
			case Released -> RELEASED;
			case EarlyUpdatedByRemote -> EARLY_UPDATED_BY_REMOTE;
			case EarlyUpdating -> EARLY_UPDATING;
		};

	}

	public boolean isPossibleCallEndedMessage()
	{
		return this == CALL_END || this == ERROR || this == RELEASED;
	}

	public boolean isCallOutgoingConnecting()
	{
		return this == OUTGOING_INIT || this == OUTGOING_PROGRESS;
	}

	public boolean isCallOutgoingRinging()
	{
		return this == OUTGOING_RINGING;
	}

	public boolean isTalking()
	{
		return this == CONNECTED || this == STREAMS_RUNNING || this == UPDATED_BY_REMOTE || this == UPDATING || this == EARLY_UPDATED_BY_REMOTE || this == EARLY_UPDATING;
	}

	public boolean isNewCallJustStarted()
	{
		return this == OUTGOING_INIT || this == INCOMING_RECEIVED;
	}

	public boolean isEndedCall()
	{
		return this == CALL_END;
	}

	public boolean isMyPhoneRinging()
	{
		return this == INCOMING_RECEIVED;
	}

	public boolean isBeforeEncryption()
	{
		return this == CONNECTED;
	}

	public String createNotificationText(final Context context, final String simlarId, final boolean goingDown)
	{
		if (Util.isNullOrEmpty(simlarId)) {
			return context.getString(R.string.linphone_call_state_notification_initializing);
		}

		if (goingDown) {
			return String.format(context.getString(R.string.linphone_call_state_notification_ended), simlarId);
		}

		switch (this) {
			case CALL_END:
			case ERROR:
			case RELEASED:
				return String.format(context.getString(R.string.linphone_call_state_notification_ended), simlarId);
			case INCOMING_RECEIVED:
			case INCOMING_EARLY_MEDIA:
				return context.getString(R.string.linphone_call_state_notification_receiving_call);
			case CONNECTED:
			case STREAMS_RUNNING:
			case UPDATED_BY_REMOTE:
			case UPDATING:
			case EARLY_UPDATED_BY_REMOTE:
			case EARLY_UPDATING:
				return String.format(context.getString(R.string.linphone_call_state_notification_talking), simlarId);
			case OUTGOING_INIT:
			case OUTGOING_EARLY_MEDIA:
			case OUTGOING_PROGRESS:
			case OUTGOING_RINGING:
				return String.format(context.getString(R.string.linphone_call_state_notification_calling), simlarId);
			case PAUSED:
			case PAUSED_BY_REMOTE:
			case PAUSING:
				return String.format(context.getString(R.string.linphone_call_state_notification_paused), simlarId);
			case RESUMING:
				return String.format(context.getString(R.string.linphone_call_state_notification_resuming), simlarId);
			case REFERED:
				Lg.w("createNotificationText falling back to initializing for SimlarCallState=", this);
				//$FALL-THROUGH$
			case UNKNOWN:
			case IDLE:
			default:
				return context.getString(R.string.linphone_call_state_notification_initializing);
		}
	}
}

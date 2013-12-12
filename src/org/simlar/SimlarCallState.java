/**
 * Copyright (C) 2013 The Simlar Authors.
 *
 * This file is part of Simlar. (http://www.simlar.org)
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

package org.simlar;

import org.linphone.core.LinphoneCall.State;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

public final class SimlarCallState
{
	private static final String LOGTAG = SimlarCallState.class.getSimpleName();

	private String mDisplayName = null;
	private String mDisplayPhotoId = null;
	private State mLinphoneCallState = null;
	private int mCallStatusMessageId = -1;
	private boolean mEncrypted = true;
	private String mAuthenticationToken = null;
	private boolean mAuthenticationTokenVerified = false;
	private boolean mOngoingEncryptionHandshake = false;
	private NetworkQuality mQuality = NetworkQuality.UNKNOWN;
	private int mDuration = 0;
	private long mCallStartTime = -1;

	private static boolean possibleErrorMessage(final State callState)
	{
		return State.CallEnd.equals(callState) || State.Error.equals(callState) || State.CallReleased.equals(callState);
	}

	private static boolean isCallOutgoingConnecting(final State callState)
	{
		return State.OutgoingInit.equals(callState) || State.OutgoingProgress.equals(callState);
	}

	private static boolean isCallOutgoingRinging(final State callState)
	{
		return State.OutgoingRinging.equals(callState);
	}

	private static int getMessageId(final State callState, final String message)
	{
		if (isCallOutgoingConnecting(callState)) {
			return R.string.call_activity_outgoing_connecting;
		}

		if (isCallOutgoingRinging(callState)) {
			return R.string.call_activity_outgoing_ringing;
		}

		// see linphone-android/submodules/belle-sip/src/message.c: well_known_codes
		if (!Util.isNullOrEmpty(message) && possibleErrorMessage(callState)) {
			if (message.equals("Call declined.")) {
				return R.string.call_activity_call_ended_because_declined;
			} else if (message.equals("Not Found")) {
				return R.string.call_activity_call_ended_because_user_offline;
			} else if (message.equals("Unsupported media type")) {
				return R.string.call_activity_call_ended_because_incompatible_media;
			} else if (message.equals("Busy here")) {
				return R.string.call_activity_call_ended_because_user_busy;
			}
		}

		return -1;
	}

	public boolean updateCallStateChanged(final String displayName, final String photoId, final State callState, final String message)
	{
		final int msgId = getMessageId(callState, message);
		final boolean updateCallStatusMessageId = !(possibleErrorMessage(callState) && msgId <= 0);

		if (Util.equalString(displayName, mDisplayName) && Util.equalString(photoId, mDisplayPhotoId) && Util.equals(callState, mLinphoneCallState)
				&& (mCallStatusMessageId == msgId || !updateCallStatusMessageId)) {
			return false;
		}

		if (callState == null) {
			Log.e(LOGTAG, "ERROR updateCallStateChanged: callState not set");
		}

		if (!Util.isNullOrEmpty(displayName)) {
			mDisplayName = displayName;
		}

		mDisplayPhotoId = photoId;
		mLinphoneCallState = callState;

		if (isBeforeEncryption()) {
			mOngoingEncryptionHandshake = true;
		} else if (isEndedCall()) {
			mOngoingEncryptionHandshake = false;
		}

		if (updateCallStatusMessageId) {
			mCallStatusMessageId = msgId;
		}

		if (isNewCall()) {
			mEncrypted = true;
			mAuthenticationToken = null;
			mAuthenticationTokenVerified = false;
			mQuality = NetworkQuality.UNKNOWN;
			mDuration = 0;
			mCallStartTime = -1;
		}

		return true;
	}

	public boolean updateCallStats(final NetworkQuality quality, final int callDuration)
	{
		if (quality == mQuality && callDuration == mDuration) {
			return false;
		}

		mQuality = quality;

		if (callDuration != mDuration) {
			mDuration = callDuration;
			if (mDuration <= 0) {
				mCallStartTime = -1;
			} else {
				mCallStartTime = SystemClock.elapsedRealtime() - mDuration * 1000L;
			}
		}

		return true;
	}

	public boolean updateCallEncryption(final boolean encrypted, final String authenticationToken, final boolean authenticationTokenVerified)
	{
		if (encrypted == mEncrypted && authenticationTokenVerified == mAuthenticationTokenVerified
				&& Util.equalString(authenticationToken, mAuthenticationToken)) {
			return false;
		}

		mOngoingEncryptionHandshake = false;

		mEncrypted = encrypted;
		mAuthenticationToken = authenticationToken;
		mAuthenticationTokenVerified = authenticationTokenVerified;

		return true;
	}

	public boolean isEmpty()
	{
		return mLinphoneCallState == null;
	}

	private String formatPhotoId()
	{
		if (Util.isNullOrEmpty(mDisplayPhotoId)) {
			return "";
		}

		return " photoId=" + mDisplayPhotoId;
	}

	private String formatCallStatusMessageId()
	{
		if (mCallStatusMessageId > 0) {
			return " mCallStatusMessageId=" + mCallStatusMessageId;
		}

		return "";
	}

	private String formatEncryption()
	{
		if (!mEncrypted) {
			return " NOT ENCRYPTED";
		}

		return " SAS=" + mAuthenticationToken + (mAuthenticationTokenVerified ? " (verified)" : " (not verified)");
	}

	private String formatOngoingEncryptionHandshake()
	{
		if (mOngoingEncryptionHandshake) {
			return " ongoingEncryptionHandshake";
		}
		return "";
	}

	private String formatQuality()
	{
		if (!mQuality.isKnown()) {
			return "";
		}

		return " quality=" + mQuality;
	}

	@Override
	public String toString()
	{
		if (isEmpty()) {
			return "";
		}

		return "[" + mLinphoneCallState.toString() + "] " + mDisplayName + formatPhotoId() + formatOngoingEncryptionHandshake()
				+ formatCallStatusMessageId() + formatEncryption() + formatQuality();
	}

	public String getDisplayName()
	{
		return mDisplayName;
	}

	public String getDisplayPhotoId()
	{
		return mDisplayPhotoId;
	}

	public String getCallStatusDisplayMessage(final Context context)
	{
		if (!hasCallStatusMessage()) {
			return null;
		}

		if (mOngoingEncryptionHandshake) {
			return context.getString(R.string.call_activity_encrypting);
		}

		return context.getString(mCallStatusMessageId);
	}

	public String getErrorDisplayMessage(final Context context, final String displayName)
	{
		return hasErrorMessage() ? String.format(context.getString(mCallStatusMessageId), displayName) : null;
	}

	public boolean isEncrypted()
	{
		return mEncrypted;
	}

	public String getAuthenticationToken()
	{
		return mAuthenticationToken;
	}

	public boolean hasQuality()
	{
		return mQuality.isKnown();
	}

	public int getQualityDescription()
	{
		return mQuality.getDescription();
	}

	public boolean isAuthenticationTokenVerified()
	{
		return mAuthenticationTokenVerified;
	}

	public boolean isTalking()
	{
		if (isEmpty()) {
			return false;
		}

		return State.Connected.equals(mLinphoneCallState) ||
				State.StreamsRunning.equals(mLinphoneCallState) ||
				State.CallUpdatedByRemote.equals(mLinphoneCallState) ||
				State.CallUpdating.equals(mLinphoneCallState);
	}

	public boolean isNewCall()
	{
		if (isEmpty()) {
			return false;
		}

		return State.OutgoingInit.equals(mLinphoneCallState) || State.IncomingReceived.equals(mLinphoneCallState);
	}

	public boolean isEndedCall()
	{
		if (isEmpty()) {
			return false;
		}

		return State.CallEnd.equals(mLinphoneCallState);
	}

	public boolean isRinging()
	{
		if (isEmpty()) {
			return false;
		}

		return State.IncomingReceived.equals(mLinphoneCallState);
	}

	public boolean isBeforeEncryption()
	{
		return State.Connected.equals(mLinphoneCallState);
	}

	public boolean hasCallStatusMessage()
	{
		if (isEmpty()) {
			return false;
		}

		if ((isCallOutgoingConnecting(mLinphoneCallState) || isCallOutgoingRinging(mLinphoneCallState)) && mCallStatusMessageId > 0) {
			return true;
		}

		return mOngoingEncryptionHandshake;
	}

	public boolean hasErrorMessage()
	{
		return !isEmpty() && possibleErrorMessage(mLinphoneCallState) && mCallStatusMessageId > 0;
	}

	public long getStartTime()
	{
		return mDuration > 0 ? mCallStartTime : -1;
	}
}

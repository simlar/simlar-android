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

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

public final class SimlarCallState
{
	private static final String LOGTAG = SimlarCallState.class.getSimpleName();

	private String mDisplayName = null;
	private String mDisplayPhotoId = null;
	private LinphoneCallState mLinphoneCallState = LinphoneCallState.UNKONWN;
	private CallEndReason mCallEndReason = CallEndReason.NONE;
	private boolean mEncrypted = true;
	private String mAuthenticationToken = null;
	private boolean mAuthenticationTokenVerified = false;
	private boolean mOngoingEncryptionHandshake = false;
	private NetworkQuality mQuality = NetworkQuality.UNKNOWN;
	private int mDuration = 0;
	private long mCallStartTime = -1;

	private boolean updateCallEndReason(final CallEndReason reason)
	{
		// do not override existing reasons
		if (mCallEndReason != CallEndReason.NONE) {
			return false;
		}

		if (mCallEndReason == reason) {
			return false;
		}

		Log.w(LOGTAG, "new CallEndReason=" + reason);
		mCallEndReason = reason;
		return true;
	}

	public boolean updateCallStateChanged(final String displayName, final String photoId, final LinphoneCallState callState,
			final CallEndReason reason)
	{
		if (!updateCallEndReason(reason) && Util.equalString(displayName, mDisplayName) && Util.equalString(photoId, mDisplayPhotoId)
				&& mLinphoneCallState == callState) {
			return false;
		}

		if (callState == LinphoneCallState.UNKONWN) {
			Log.e(LOGTAG, "ERROR updateCallStateChanged: callState=" + callState);
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

		if (mLinphoneCallState.isNewCallJustStarted()) {
			mCallEndReason = CallEndReason.NONE;
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
		return mLinphoneCallState == LinphoneCallState.UNKONWN;
	}

	private String formatPhotoId()
	{
		if (Util.isNullOrEmpty(mDisplayPhotoId)) {
			return "";
		}

		return " photoId=" + mDisplayPhotoId;
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
				+ "CallEndReason=" + mCallEndReason + formatEncryption() + formatQuality();
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

		return String.format(context.getString(mCallEndReason.getDisplayMessageId()), mDisplayName);
	}

	public String getErrorDisplayMessage(final Context context, final String displayName)
	{
		return hasErrorMessage() ? String.format(context.getString(mCallEndReason.getDisplayMessageId()), displayName) : null;
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
		return mLinphoneCallState.isTalking();
	}

	public boolean isNewCall()
	{
		return mLinphoneCallState.isNewCallJustStarted();
	}

	public boolean isEndedCall()
	{
		return mLinphoneCallState.isEndedCall();
	}

	public boolean isRinging()
	{
		return mLinphoneCallState.isMyPhoneRinging();
	}

	public boolean isBeforeEncryption()
	{
		return mLinphoneCallState.isBeforeEncryption();
	}

	public boolean hasCallStatusMessage()
	{
		return !isEmpty() && mLinphoneCallState.isPossibleCallEndedMessage() && mCallEndReason.getDisplayMessageId() > 0;
	}

	public long getStartTime()
	{
		return mDuration > 0 ? mCallStartTime : -1;
	}

	private boolean hasErrorMessage()
	{
		return !isEmpty() && mLinphoneCallState.isPossibleCallEndedMessage() && mCallEndReason.getDisplayMessageId() > 0;
	}
}

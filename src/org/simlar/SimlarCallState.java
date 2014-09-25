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

package org.simlar;

import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;

public final class SimlarCallState
{
	private static final String LOGTAG = SimlarCallState.class.getSimpleName();

	private String mSimlarId = null;
	private String mContactName = null;
	private String mContactPhotoId = null;
	private LinphoneCallState mLinphoneCallState = LinphoneCallState.UNKNOWN;
	private GuiCallState mGuiCallState = GuiCallState.UNKNOWN;
	private CallEndReason mCallEndReason = CallEndReason.NONE;
	private boolean mEncrypted = true;
	private String mAuthenticationToken = null;
	private boolean mAuthenticationTokenVerified = false;
	private NetworkQuality mQuality = NetworkQuality.UNKNOWN;
	private int mDuration = 0;
	private long mCallStartTime = -1;

	private enum GuiCallState
	{
		UNKNOWN,
		CONNECTING_TO_SERVER,
		WAITING_FOR_CONTACT,
		RINGING,
		ENCRYPTING,
		TALKING,
		ENDED;
	}

	private boolean updateCallEndReason(final CallEndReason reason)
	{
		// do not override existing reasons
		if (mCallEndReason != CallEndReason.NONE) {
			return false;
		}

		if (mCallEndReason == reason) {
			return false;
		}

		Lg.w(LOGTAG, "new CallEndReason=", reason);
		mCallEndReason = reason;
		return true;
	}

	public boolean updateCallStateChanged(final String simlarId, final LinphoneCallState callState, final CallEndReason reason)
	{
		if (!updateCallEndReason(reason) && Util.equalString(mSimlarId, simlarId) && mLinphoneCallState == callState) {
			return false;
		}

		if (Util.isNullOrEmpty(simlarId) && callState != LinphoneCallState.IDLE) {
			Lg.e(LOGTAG, "ERROR updateCallStateChanged: simlarId not set state=", callState);
		}

		if (callState == LinphoneCallState.UNKNOWN) {
			Lg.e(LOGTAG, "ERROR updateCallStateChanged: callState=", callState);
		}

		mSimlarId = simlarId;
		mLinphoneCallState = callState;

		final GuiCallState oldGuiCallState = mGuiCallState;
		if (mLinphoneCallState.isNewCallJustStarted()) {
			mGuiCallState = GuiCallState.CONNECTING_TO_SERVER;
		} else if (mLinphoneCallState.isPossibleCallEndedMessage()) {
			mGuiCallState = GuiCallState.ENDED;
		} else if (mLinphoneCallState.isCallOutgoingConnecting()) {
			mGuiCallState = GuiCallState.WAITING_FOR_CONTACT;
		} else if (mLinphoneCallState.isCallOutgoingRinging()) {
			mGuiCallState = GuiCallState.RINGING;
		} else if (mLinphoneCallState.isBeforeEncryption()) {
			mGuiCallState = GuiCallState.ENCRYPTING;
		}
		// NOTE: talking is set after encryption

		if (oldGuiCallState != mGuiCallState) {
			if (mGuiCallState == GuiCallState.ENDED) {
				mCallStartTime = -1;
			} else {
				mCallStartTime = SystemClock.elapsedRealtime();
			}
		}

		if (mLinphoneCallState.isNewCallJustStarted()) {
			mSimlarId = null;
			mContactName = null;
			mContactPhotoId = null;
			mCallEndReason = CallEndReason.NONE;
			mEncrypted = true;
			mAuthenticationToken = null;
			mAuthenticationTokenVerified = false;
			mQuality = NetworkQuality.UNKNOWN;
			mDuration = 0;
		}

		return true;
	}

	public boolean updateContactNameAndImage(final String name, final String photoId)
	{
		if (Util.equalString(mContactName, name) && Util.equals(name, photoId)) {
			return false;
		}

		if (Util.isNullOrEmpty(name)) {
			Lg.e(LOGTAG, "ERROR updateContactNameAndImage: name not set");
		}

		mContactName = name;
		mContactPhotoId = photoId;

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
			if (mDuration > 0) {
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

		if (mGuiCallState == GuiCallState.ENCRYPTING) {
			mGuiCallState = GuiCallState.TALKING;
			mCallStartTime = SystemClock.elapsedRealtime();
		}

		mEncrypted = encrypted;
		mAuthenticationToken = authenticationToken;
		mAuthenticationTokenVerified = authenticationTokenVerified;

		return true;
	}

	public boolean isEmpty()
	{
		return mLinphoneCallState == LinphoneCallState.UNKNOWN;
	}

	@Override
	public String toString()
	{
		return "SimlarCallState [" + (mSimlarId != null ? "mSimlarId=" + new Lg.Anonymizer(mSimlarId) + ", " : "")
				+ (mContactName != null ? "mContactName=" + new Lg.Anonymizer(mContactName) + ", " : "")
				+ (mContactPhotoId != null ? "mContactPhotoId=" + mContactPhotoId + ", " : "")
				+ (mLinphoneCallState != null ? "mLinphoneCallState=" + mLinphoneCallState + ", " : "")
				+ (mGuiCallState != null ? "mGuiCallState=" + mGuiCallState + ", " : "")
				+ (mCallEndReason != null ? "mCallEndReason=" + mCallEndReason + ", " : "") + "mEncrypted=" + mEncrypted + ", "
				+ (mAuthenticationToken != null ? "mAuthenticationToken=" + mAuthenticationToken + ", " : "") + "mAuthenticationTokenVerified="
				+ mAuthenticationTokenVerified + ", " + (mQuality != null ? "mQuality=" + mQuality + ", " : "") + "mDuration=" + mDuration
				+ ", mCallStartTime=" + mCallStartTime + "]";
	}

	public String getContactName()
	{
		if (Util.isNullOrEmpty(mContactName)) {
			return mSimlarId;
		}

		return mContactName;
	}

	public Bitmap getContactPhotoBitmap(final Context context, final int defaultResourceId)
	{
		if (Util.isNullOrEmpty(mContactPhotoId)) {
			return BitmapFactory.decodeResource(context.getResources(), defaultResourceId);
		}

		try {
			return MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(mContactPhotoId));
		} catch (final FileNotFoundException e) {
			Lg.ex(LOGTAG, e, "getContactPhotoBitmap FileNotFoundException");
		} catch (IOException e) {
			Lg.ex(LOGTAG, e, "getContactPhotoBitmap IOException");
		}

		return BitmapFactory.decodeResource(context.getResources(), defaultResourceId);
	}

	public String getCallStatusDisplayMessage(final Context context)
	{
		if (isEmpty()) {
			return null;
		}

		switch (mGuiCallState) {
		case CONNECTING_TO_SERVER:
			return context.getString(R.string.call_activity_connecting_to_server);
		case WAITING_FOR_CONTACT:
			return context.getString(R.string.call_activity_outgoing_connecting);
		case RINGING:
			return context.getString(R.string.call_activity_outgoing_ringing);
		case ENCRYPTING:
			return context.getString(R.string.call_activity_encrypting);
		case TALKING:
			return context.getString(R.string.call_activity_talking);
		case ENDED:
			return context.getString(mCallEndReason.getDisplayMessageId());
		case UNKNOWN:
		default:
			Lg.w(LOGTAG, "getCallStatusDisplayMessage mLinphoneCallState=", mLinphoneCallState);
			return "";
		}
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

	public boolean isWaitingForContact()
	{
		return mLinphoneCallState.isCallOutgoingConnecting();
	}

	public long getStartTime()
	{
		return mCallStartTime;
	}

	public void connectingToSimlarServerTimedOut()
	{
		mLinphoneCallState = LinphoneCallState.CALL_END;
		mCallEndReason = CallEndReason.SERVER_CONNECTION_TIMEOUT;
	}

	public String createNotificationText(final Context context, boolean goingDown)
	{
		return mLinphoneCallState.createNotificationText(context, getContactName(), goingDown);
	}
}

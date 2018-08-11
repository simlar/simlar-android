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

package org.simlar.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.simlar.R;
import org.simlar.contactsprovider.ContactsProvider;
import org.simlar.helper.CallEndReason;
import org.simlar.helper.NetworkQuality;
import org.simlar.logging.Lg;
import org.simlar.service.liblinphone.LinphoneCallState;
import org.simlar.utils.Util;

public final class SimlarCallState
{
	private LinphoneCallState mLinphoneCallState = LinphoneCallState.UNKNOWN;
	private GuiCallState mGuiCallState = GuiCallState.UNKNOWN;
	private String mSimlarId = null;
	private String mContactName = null;
	private String mContactPhotoId = null;
	private CallEndReason mCallEndReason = CallEndReason.NONE;
	private String mAuthenticationToken = null;
	private boolean mAuthenticationTokenVerified = false;
	private NetworkQuality mQuality = NetworkQuality.UNKNOWN;
	private long mCallStartTime = -1;
	private boolean mVideoRequested = false;
	private boolean mVideoEnabled = false;

	private enum GuiCallState
	{
		UNKNOWN,
		CONNECTING_TO_SERVER,
		WAITING_FOR_CONTACT,
		RINGING,
		ENCRYPTING,
		TALKING,
		ENDED
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

		Lg.i("new CallEndReason=", reason);
		mCallEndReason = reason;
		return true;
	}

	public boolean updateCallStateChanged(final String simlarId, final LinphoneCallState callState, final CallEndReason reason, final boolean videoEnabled)
	{
		if (!updateCallEndReason(reason) && Util.equalString(mSimlarId, simlarId) && mLinphoneCallState == callState && mVideoEnabled == videoEnabled) {
			return false;
		}

		if (Util.isNullOrEmpty(simlarId) && callState != LinphoneCallState.IDLE) {
			Lg.e("ERROR updateCallStateChanged: simlarId not set state=", callState);
		}

		if (callState == LinphoneCallState.UNKNOWN) {
			Lg.e("ERROR updateCallStateChanged: callState=", LinphoneCallState.UNKNOWN);
		}

		mSimlarId = simlarId;
		mLinphoneCallState = callState;
		mVideoEnabled = videoEnabled;

		if (mLinphoneCallState == LinphoneCallState.STREAMS_RUNNING) {
			mVideoRequested = false;
		}

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
			mCallStartTime = mGuiCallState == GuiCallState.ENDED ? -1 : SystemClock.elapsedRealtime();
		}

		if (mLinphoneCallState.isNewCallJustStarted()) {
			Lg.i("resetting call state, because of new call");
			mSimlarId = null;
			mContactName = null;
			mContactPhotoId = null;
			mCallEndReason = CallEndReason.NONE;
			mAuthenticationToken = null;
			mAuthenticationTokenVerified = false;
			mQuality = NetworkQuality.UNKNOWN;
			mVideoRequested = false;
			mVideoEnabled = false;
		}

		return true;
	}

	public void updateContactNameAndImage(final String name, final String photoId)
	{
		if (Util.equalString(mContactName, name) && Util.equalString(mContactPhotoId, photoId)) {
			return;
		}

		if (Util.isNullOrEmpty(name)) {
			Lg.e("ERROR updateContactNameAndImage: name not set");
		}

		mContactName = name;
		mContactPhotoId = photoId;
	}

	public boolean updateCallStats(final NetworkQuality quality, final int callDuration)
	{
		if (quality == mQuality && !updateCallStartTime(callDuration)) {
			return false;
		}

		mQuality = quality;

		return true;
	}

	private boolean updateCallStartTime(final int callDuration)
	{
		/// make sure to only decrease mCallStartTime so that the call duration shown in the call activity may only increase
		final long callStartTime = SystemClock.elapsedRealtime() - callDuration * 1000L;
		if (mCallStartTime != -1 && mCallStartTime - callStartTime < 500) {
			return false;
		}

		mCallStartTime = callStartTime;
		return true;
	}

	public boolean updateCallEncryption(final String authenticationToken, final boolean authenticationTokenVerified)
	{
		if (authenticationTokenVerified == mAuthenticationTokenVerified
				&& Util.equalString(authenticationToken, mAuthenticationToken)) {
			return false;
		}

		if (mGuiCallState == GuiCallState.ENCRYPTING) {
			mGuiCallState = GuiCallState.TALKING;
		}

		mAuthenticationToken = authenticationToken;
		mAuthenticationTokenVerified = authenticationTokenVerified;

		return true;
	}

	public boolean updateConnectingToServer()
	{
		if (mGuiCallState == GuiCallState.CONNECTING_TO_SERVER) {
			return false;
		}

		mGuiCallState = GuiCallState.CONNECTING_TO_SERVER;
		mCallStartTime = SystemClock.elapsedRealtime();

		return true;
	}

	public boolean isEmpty()
	{
		return mLinphoneCallState == LinphoneCallState.UNKNOWN;
	}

	@Override
	public String toString()
	{
		return "SimlarCallState{" +
				"mLinphoneCallState=" + mLinphoneCallState +
				", mGuiCallState=" + mGuiCallState +
				", mSimlarId='" + new Lg.Anonymizer(mSimlarId) + '\'' +
				", mContactName='" + mContactName + '\'' +
				", mContactPhotoId='" + mContactPhotoId + '\'' +
				", mCallEndReason=" + mCallEndReason +
				", mAuthenticationToken='" + mAuthenticationToken + '\'' +
				", mAuthenticationTokenVerified=" + mAuthenticationTokenVerified +
				", mQuality=" + mQuality +
				", mCallStartTime=" + mCallStartTime +
				", mVideoRequested=" + mVideoRequested +
				", mVideoEnabled=" + mVideoEnabled +
				'}';
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
		return ContactsProvider.getContactPhotoBitmap(context, defaultResourceId, mContactPhotoId);
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
			Lg.w("getCallStatusDisplayMessage mLinphoneCallState=", mLinphoneCallState);
			return "";
		}
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
		Lg.w("connecting to simlar server timed out");
		mGuiCallState = GuiCallState.ENDED;
		mLinphoneCallState = LinphoneCallState.CALL_END;
		mCallEndReason = CallEndReason.SERVER_CONNECTION_TIMEOUT;
	}

	public String createNotificationText(final Context context, final boolean goingDown)
	{
		return mLinphoneCallState.createNotificationText(context, getContactName(), goingDown);
	}

	public boolean isVideoRequestPossible()
	{
		return mGuiCallState == GuiCallState.TALKING;
	}

	public void setVideoRequested()
	{
		mVideoRequested = true;
	}

	public boolean isVideoRequested()
	{
		return mVideoRequested;
	}

	public boolean isVideoEnabled()
	{
		return mVideoEnabled;
	}
}

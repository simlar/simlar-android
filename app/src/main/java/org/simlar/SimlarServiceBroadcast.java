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

import java.io.Serializable;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public final class SimlarServiceBroadcast implements Serializable
{
	public static final String BROADCAST_NAME = "SimlarServiceBroadcast";
	public static final String INTENT_EXTRA = "SimlarServiceBroadcast";

	public enum Type {
		SIMLAR_STATUS,
		SIMLAR_CALL_STATE,
		CALL_CONNECTION_DETAILS,
		SERVICE_FINISHES
	}

	private final Type mType;
	private final Parameters mParameters;

	@SuppressWarnings("SameParameterValue")
	private SimlarServiceBroadcast(final Type type, final Parameters parameters)
	{
		this.mType = type;
		this.mParameters = parameters;
	}

	private void send(final Context context)
	{
		Intent intent = new Intent(BROADCAST_NAME);
		intent.putExtra(INTENT_EXTRA, this);
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	}

	@SuppressWarnings("unused")
	public static interface Parameters extends Serializable
	{
	}

	public Type getType()
	{
		return mType;
	}

	@SuppressWarnings("unused")
	public Parameters getParameters()
	{
		return mParameters;
	}

	public static void sendSimlarStatusChanged(final Context context)
	{
		new SimlarServiceBroadcast(Type.SIMLAR_STATUS, null).send(context);
	}

	public static void sendSimlarCallStateChanged(final Context context)
	{
		new SimlarServiceBroadcast(Type.SIMLAR_CALL_STATE, null).send(context);
	}

	public static void sendCallConnectionDetailsChanged(final Context context)
	{
		new SimlarServiceBroadcast(Type.CALL_CONNECTION_DETAILS, null).send(context);
	}

	public static void sendServiceFinishes(final Context context)
	{
		new SimlarServiceBroadcast(Type.SERVICE_FINISHES, null).send(context);
	}
}

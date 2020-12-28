/**
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
 */

package org.simlar.proximityscreenlocker;

public final class ProximityScreenOnceProxy implements ProximityScreenLocker
{
	private final ProximityScreenLocker mProximityScreenLocker;
	private boolean mAcquired;

	public ProximityScreenOnceProxy(final ProximityScreenLocker mProximityScreenLocker)
	{
		this.mProximityScreenLocker = mProximityScreenLocker;
		mAcquired = false;
	}

	@Override
	public void acquire()
	{
		if (mAcquired) {
			return;
		}

		mAcquired = true;
		mProximityScreenLocker.acquire();
	}

	@Override
	public void release(final boolean immediately)
	{
		if (!mAcquired) {
			return;
		}

		mAcquired = false;
		mProximityScreenLocker.release(immediately);
	}
}

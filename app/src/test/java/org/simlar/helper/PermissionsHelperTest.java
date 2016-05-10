/*
 * Copyright (C) 2013 - 2016 The Simlar Authors.
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

package org.simlar.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PermissionsHelperTest
{
	private void assertCreateRequestResultsLog(final String expected, final int requestCode, final String[] permissions, final int[] grantResults)
	{
		assertEquals(expected, PermissionsHelper.createRequestResultsLog(requestCode, permissions, grantResults).toString());
	}

	@Test
	public void createRequestResultsLog()
	{
		assertCreateRequestResultsLog("requestCode=0 p1=1 p2=2", 0, new String[]{"p1", "p2"}, new int[]{1, 2});
		assertCreateRequestResultsLog("requestCode=3 p1=2 p2=1", 3, new String[]{"p1", "p2"}, new int[]{2, 1});
		assertCreateRequestResultsLog("requestCode=0 p1=1 unknown=2", 0, new String[]{"p1"}, new int[]{1, 2});
		assertCreateRequestResultsLog("requestCode=0 unknown=1 unknown=2", 0, new String[]{}, new int[]{1, 2});
		assertCreateRequestResultsLog("requestCode=0 unknown=1 unknown=2", 0, null, new int[]{1, 2});
		assertCreateRequestResultsLog("requestCode=0 p1=1 p2=?", 0, new String[]{"p1", "p2"}, new int[]{1});
		assertCreateRequestResultsLog("requestCode=0 p1=? p2=?", 0, new String[]{"p1", "p2"}, new int[]{});
		assertCreateRequestResultsLog("requestCode=0 p1=? p2=?", 0, new String[]{"p1", "p2"}, null);
	}
}

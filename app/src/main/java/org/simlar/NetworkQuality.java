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

public enum NetworkQuality
{
	UNKNOWN,
	GOOD,
	AVERAGE,
	POOR,
	VERY_POOR,
	UNUSABLE;

	public static NetworkQuality fromFloat(final float quality)
	{
		if (4 <= quality && quality <= 5) {
			return GOOD;
		}

		if (3 <= quality && quality < 4) {
			return AVERAGE;
		}

		if (2 <= quality && quality < 3) {
			return POOR;
		}

		if (1 <= quality && quality < 2) {
			return VERY_POOR;
		}

		if (0 <= quality && quality < 1) {
			return UNUSABLE;
		}

		return UNKNOWN;
	}

	public int getDescription()
	{
		switch (this) {
		case GOOD:
			return R.string.network_quality_good;
		case AVERAGE:
			return R.string.network_quality_average;
		case POOR:
			return R.string.network_quality_poor;
		case VERY_POOR:
			return R.string.network_quality_very_poor;
		case UNUSABLE:
			return R.string.network_quality_unusable;
		case UNKNOWN:
		default:
			return R.string.network_quality_unknown;
		}
	}

	public boolean isKnown()
	{
		return this != UNKNOWN;
	}
}

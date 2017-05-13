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

package org.simlar.helper;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class SimlarNumber
{
	private static String mDefaultRegion = null;
	private final PhoneNumber mPhoneNumber;
	private final String mPlainSimlarId;

	public SimlarNumber(final String telephoneNumber)
	{
		if (hasSimlarIdFormat(telephoneNumber)) {
			mPhoneNumber = null;
			mPlainSimlarId = telephoneNumber;
		} else {
			mPhoneNumber = createPhoneNumber(telephoneNumber);
			mPlainSimlarId = null;
		}
	}

	private static PhoneNumber createPhoneNumber(final String telephoneNumber)
	{
		if (Util.isNullOrEmpty(telephoneNumber)) {
			Lg.e("createPhoneNumber: empty telephone number");
			return null;
		}

		if (Util.isNullOrEmpty(mDefaultRegion)) {
			Lg.e("no default region set, please initialize");
			return null;
		}

		try {
			final PhoneNumber pn = PhoneNumberUtil.getInstance().parse(telephoneNumber, mDefaultRegion);

			if (pn == null) {
				Lg.w("parsing number with LibPhoneNumber failed: pn is null");
				return null;
			}

			if (!pn.hasCountryCode()) {
				Lg.w("parsing number with LibPhoneNumber failed: no country code");
				return null;
			}

			if (!pn.hasNationalNumber()) {
				Lg.w("parsing number with LibPhoneNumber failed: no national number");
				return null;
			}

			return pn;
		} catch (final NumberParseException e) {
			// we do not want a stacktrace in the logs for each not parsable number
			Lg.i("NumberParseException (telephoneNumber=", telephoneNumber, " mDefaultRegion=", mDefaultRegion, "): ", e.getMessage());
			return null;
		}
	}

	public boolean isValid()
	{
		return (!Util.isNullOrEmpty(mPlainSimlarId) || mPhoneNumber != null);
	}

	public String getSimlarId()
	{
		if (!Util.isNullOrEmpty(mPlainSimlarId)) {
			return mPlainSimlarId;
		}

		if (mPhoneNumber == null) {
			return "";
		}

		return "*" + Long.toString(mPhoneNumber.getCountryCode()) + Long.toString(mPhoneNumber.getNationalNumber()) + "*";
	}

	public String getTelephoneNumber()
	{
		if (mPhoneNumber == null) {
			return "";
		}

		return PhoneNumberUtil.getInstance().format(mPhoneNumber, PhoneNumberFormat.E164);
	}

	public String getGuiTelephoneNumber()
	{
		if (!Util.isNullOrEmpty(mPlainSimlarId)) {
			return mPlainSimlarId;
		}

		if (mPhoneNumber == null) {
			return "";
		}

		return PhoneNumberUtil.getInstance().format(mPhoneNumber, PhoneNumberFormat.INTERNATIONAL);
	}

	public CharSequence getNationalOnly()
	{
		if (mPhoneNumber == null) {
			return "";
		}

		return Long.toString(mPhoneNumber.getNationalNumber());
	}

	public static int region2RegionCode(final String region)
	{
		// returns 0 if not found
		return PhoneNumberUtil.getInstance().getCountryCodeForRegion(region);
	}

	public static void setDefaultRegion(final int countryCallingCode)
	{
		mDefaultRegion = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCallingCode);
		Lg.i("for number parsing now using default region: ", mDefaultRegion);
	}

	public static int getDefaultRegion()
	{
		return PhoneNumberUtil.getInstance().getCountryCodeForRegion(mDefaultRegion);
	}

	public static String createSimlarId(final String telephoneNumber)
	{
		return new SimlarNumber(telephoneNumber).getSimlarId();
	}

	public static Collection<Integer> getSupportedCountryCodes()
	{
		final Collection<Integer> supportedCountryCodes = new HashSet<>();

		final PhoneNumberUtil pnUtil = PhoneNumberUtil.getInstance();
		for (final String region : pnUtil.getSupportedRegions()) {
			supportedCountryCodes.add(pnUtil.getCountryCodeForRegion(region));
		}

		return supportedCountryCodes;
	}

	private static boolean hasSimlarIdFormat(final String telephoneNumber)
	{
		return !Util.isNullOrEmpty(telephoneNumber) && telephoneNumber.matches("\\*\\d*\\*");
	}
}

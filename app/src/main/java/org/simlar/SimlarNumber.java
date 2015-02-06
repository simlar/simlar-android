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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.telephony.TelephonyManager;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

final class SimlarNumber
{
	private static final String LOGTAG = SimlarNumber.class.getSimpleName();
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
			Lg.e(LOGTAG, "createPhoneNumber: empty telephone number");
			return null;
		}

		if (Util.isNullOrEmpty(mDefaultRegion)) {
			Lg.e(LOGTAG, "no default region set, please initialize");
			return null;
		}

		try {
			final PhoneNumber pn = PhoneNumberUtil.getInstance().parse(telephoneNumber, mDefaultRegion);

			if (pn == null) {
				Lg.w(LOGTAG, "parsing number with LibPhoneNumber failed: pn is null");
				return null;
			}

			if (!pn.hasCountryCode()) {
				Lg.w(LOGTAG, "parsing number with LibPhoneNumber failed: no country code");
				return null;
			}

			if (!pn.hasNationalNumber()) {
				Lg.w(LOGTAG, "parsing number with LibPhoneNumber failed: no national number");
				return null;
			}

			return pn;
		} catch (final NumberParseException e) {
			// we do not want a stacktrace in the logs for each not parsable number
			Lg.i(LOGTAG, "NumberParseException (telephoneNumber=", telephoneNumber, " mDefaultRegion=", mDefaultRegion, "): ", e.getMessage());
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

	private String getNationalOnly()
	{
		if (mPhoneNumber == null) {
			return "";
		}

		return Long.toString(mPhoneNumber.getNationalNumber());
	}

	public static void setDefaultRegion(int countryCallingCode)
	{
		mDefaultRegion = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCallingCode);
		Lg.i(LOGTAG, "for number parsing now using default region: ", mDefaultRegion);
	}

	public static int getDefaultRegion()
	{
		return PhoneNumberUtil.getInstance().getCountryCodeForRegion(mDefaultRegion);
	}

	public static String createSimlarId(final String telephoneNumber)
	{
		return new SimlarNumber(telephoneNumber).getSimlarId();
	}

	private static String readRegionFromSimCardOrConfiguration(final Context c)
	{
		// try to read country code from sim
		final TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
		final String regionFromSim = tm.getSimCountryIso().toUpperCase(Locale.US);
		if (!Util.isNullOrEmpty(regionFromSim)) {
			mDefaultRegion = regionFromSim;
			return regionFromSim;
		}

		// read countryCode from configuration
		final String regionFromConfig = c.getResources().getConfiguration().locale.getCountry().toUpperCase(Locale.US);
		Lg.i(LOGTAG, "guessed region by android configuration: ", regionFromConfig);
		mDefaultRegion = regionFromConfig;
		return regionFromConfig;
	}

	public static int readRegionCodeFromSimCardOrConfiguration(final Context c)
	{
		// returns 0 if not found
		return PhoneNumberUtil.getInstance().getCountryCodeForRegion(readRegionFromSimCardOrConfiguration(c));
	}

	public static String readLocalPhoneNumberFromSimCard(final Context c)
	{
		if (Util.isNullOrEmpty(mDefaultRegion)) {
			readRegionFromSimCardOrConfiguration(c);
		}

		final String numberFromSim = ((TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();

		if (Util.isNullOrEmpty(numberFromSim)) {
			Lg.w(LOGTAG, "failed to read telephone number from sim card");
			return "";
		}

		return new SimlarNumber(numberFromSim).getNationalOnly();
	}

	public static Set<Integer> getSupportedCountryCodes()
	{
		final Set<Integer> supportedCountryCodes = new HashSet<>();

		final PhoneNumberUtil pnUtil = PhoneNumberUtil.getInstance();
		for (final String region : pnUtil.getSupportedRegions()) {
			supportedCountryCodes.add(Integer.valueOf(pnUtil.getCountryCodeForRegion(region)));
		}

		return supportedCountryCodes;
	}

	private static boolean hasSimlarIdFormat(final String telephoneNumber)
	{
		return !Util.isNullOrEmpty(telephoneNumber) && telephoneNumber.matches("\\*\\d*\\*");
	}
}

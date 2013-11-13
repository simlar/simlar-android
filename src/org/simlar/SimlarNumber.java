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

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

public class SimlarNumber
{
	private static final String LOGTAG = SimlarNumber.class.getSimpleName();
	private static String mDefaultRegion = null;

	public static String createMySimlarNumber(final Context c)
	{
		return createSimlarNumber(readLocalPhoneNumberFromSimCard(c));
	}

	public static String readRegionFromSimCardOrConfiguration(final Context c)
	{
		// try to read country code from sim
		final TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
		final String regionFromSim = tm.getSimCountryIso().toUpperCase();
		if (!Util.isNullOrEmpty(regionFromSim)) {
			mDefaultRegion = regionFromSim;
			return regionFromSim;
		}

		// read countryCode from configuration
		final String regionFromConfig = c.getResources().getConfiguration().locale.getCountry().toUpperCase();
		Log.i(LOGTAG, "guessed region by android configuration: " + regionFromConfig);
		mDefaultRegion = regionFromConfig;
		return regionFromConfig;
	}

	public static void setDefaultRegion(int countryCallingCode)
	{
		mDefaultRegion = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCallingCode);
		Log.i(LOGTAG, "for number parsing now using default region: " + mDefaultRegion);
	}

	public static int getDefaultRegion()
	{
		return PhoneNumberUtil.getInstance().getCountryCodeForRegion(mDefaultRegion);
	}

	public static int readRegionCodeFromSimCardOrConfiguration(final Context c)
	{
		// returns 0 if not found
		return PhoneNumberUtil.getInstance().getCountryCodeForRegion(readRegionFromSimCardOrConfiguration(c));
	}

	public static String readPhoneNumberFromSimCard(final Context c)
	{
		return ((TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
	}

	public static String readLocalPhoneNumberFromSimCard(final Context c)
	{
		if (Util.isNullOrEmpty(mDefaultRegion)) {
			readRegionFromSimCardOrConfiguration(c);
		}

		final String numberFromSim = readPhoneNumberFromSimCard(c);
		if (Util.isNullOrEmpty(numberFromSim)) {
			Log.w(LOGTAG, "failed to read telephone number from sim card");
			return "";
		}

		final String parsedNumber = parseNumberWithLibPhonenumber(numberFromSim, false);
		if (Util.isNullOrEmpty(parsedNumber)) {
			return "";
		}

		return parsedNumber;
	}

	public static Set<Integer> getSupportedCountryCodes()
	{
		Set<Integer> supportedCountryCodes = new HashSet<Integer>();

		final PhoneNumberUtil pnUtil = PhoneNumberUtil.getInstance();
		for (final String region : pnUtil.getSupportedRegions()) {
			supportedCountryCodes.add(Integer.valueOf(pnUtil.getCountryCodeForRegion(region)));
		}

		return supportedCountryCodes;
	}

	private static String parseNumberWithLibPhonenumber(final PhoneNumber pn, final boolean formatSimlar)
	{
		if (pn == null) {
			Log.w(LOGTAG, "parseNumberWithLibPhonenumber failed: pn is null");
			return null;
		}

		if (!pn.hasCountryCode()) {
			Log.w(LOGTAG, "parseNumberWithLibPhonenumber failed: no country code");
			return null;
		}

		if (!pn.hasNationalNumber()) {
			Log.w(LOGTAG, "parseNumberWithLibPhonenumber failed: no national number");
			return null;
		}

		if (formatSimlar) {
			return Long.toString(pn.getCountryCode()) + Long.toString(pn.getNationalNumber());
		}

		return Long.toString(pn.getNationalNumber());
	}

	private static String parseNumberWithLibPhonenumber(final String telephoneNumber, final boolean formatSimlar)
	{
		if (Util.isNullOrEmpty(mDefaultRegion)) {
			Log.e(LOGTAG, "parseNumberWithLibPhone: default region not initilializeds");
			return telephoneNumber;
		}

		try {
			PhoneNumberUtil pnUtil = PhoneNumberUtil.getInstance();
			PhoneNumber pn = pnUtil.parse(telephoneNumber, mDefaultRegion);
			final String tmpTelephoneNumber = parseNumberWithLibPhonenumber(pn, formatSimlar);

			if (Util.isNullOrEmpty(tmpTelephoneNumber)) {
				Log.e(LOGTAG, "parseNumberWithLibPhone: failed");
				return "";
			}

			Log.d(LOGTAG, "parseNumberWithLibPhone converted: " + telephoneNumber + " -> " + tmpTelephoneNumber);
			return tmpTelephoneNumber;
		} catch (NumberParseException e1) {
			Log.i(LOGTAG,
					"NumberParseException (telephoneNumber='" + telephoneNumber + " mDefaultRegion='" + mDefaultRegion + "'): " + e1.getMessage());
			return "";
		}
	}

	private static boolean hasSimlarIdFormat(final String telephoneNumber)
	{
		if (Util.isNullOrEmpty(telephoneNumber)) {
			return false;
		}

		return telephoneNumber.matches("\\*\\d*\\*");
	}

	public static String createSimlarNumber(final String telephoneNumber)
	{
		if (Util.isNullOrEmpty(telephoneNumber)) {
			Log.e(LOGTAG, "createSimlarNumber: empty telefone number");
			return "";
		}

		if (hasSimlarIdFormat(telephoneNumber)) {
			return telephoneNumber;
		}

		final String internationalTelephoneNumber = parseNumberWithLibPhonenumber(telephoneNumber, true);
		if (Util.isNullOrEmpty(internationalTelephoneNumber)) {
			Log.w(LOGTAG, "createSimlarNumber: parsing number='" + telephoneNumber + "' with libphonenumber failed");
			return "";
		}

		final String simlarId = "*" + internationalTelephoneNumber + "*";

		if (!hasSimlarIdFormat(simlarId)) {
			Log.e(LOGTAG, "WTF no frrech Id: " + simlarId);
			return "";
		}

		return simlarId;
	}
}

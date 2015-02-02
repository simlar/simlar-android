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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Xml;

public final class CreateAccount
{
	private static final String LOGTAG = CreateAccount.class.getSimpleName();

	private static final String URL_PATH = "create-account.php";

	private CreateAccount()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static class Result
	{
		public static final int SUCCESS = 0;

		private final int mErrorId;
		private final String mResult1;
		final String mResult2;

		public Result(final int errorId, final String result1, final String result2)
		{
			mErrorId = errorId;
			mResult1 = result1;
			mResult2 = result2;
		}

		public Result(final Result result)
		{
			if (result == null) {
				mErrorId = -1;
				mResult1 = null;
				mResult2 = null;
				return;
			}

			mErrorId = result.mErrorId;
			mResult1 = result.mResult1;
			mResult2 = result.mResult2;
		}

		public boolean isError()
		{
			return mErrorId != SUCCESS || Util.isNullOrEmpty(mResult1) || Util.isNullOrEmpty(mResult2);
		}

		public int getErrorMessage()
		{
			switch (mErrorId) {
			case 22:
				return R.string.create_account_activity_error_wrong_telephonenumber;
			case 23: // Too many requests
				return R.string.create_account_activity_error_not_possible;
			case 24:
				return R.string.create_account_activity_error_sms;
			case 25:
				return R.string.create_account_activity_error_too_many_confirms;
			case 26:
				return R.string.create_account_activity_error_registration_code;
			default:
				return R.string.create_account_activity_error_not_possible;
			}
		}

		public String getSimlarId()
		{
			return mResult1;
		}
	}

	public static final class RequestResult extends Result
	{
		public RequestResult(final Result result)
		{
			super(result);
		}

		public String getPassword()
		{
			return mResult2;
		}
	}

	public static final class ConfirmResult extends Result
	{
		public ConfirmResult(final Result result)
		{
			super(result);
		}

		public String getRegistrationCode()
		{
			return mResult2;
		}
	}

	public static RequestResult httpPostRequest(final String telephoneNumber, final String smsText)
	{
		Lg.i(LOGTAG, "httpPostRequest: ", new Lg.Anonymizer(telephoneNumber));

		final Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("command", "request");
		parameters.put("telephoneNumber", telephoneNumber);
		parameters.put("smsText", smsText);

		return new RequestResult(httpPost(parameters, "simlarId", "password"));
	}

	public static ConfirmResult httpPostConfirm(final String simlarId, final String registrationCode)
	{
		Lg.i(LOGTAG, "httpPostConfirm: simlarId=", new Lg.Anonymizer(simlarId), " registrationCode=", registrationCode);

		final Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("command", "confirm");
		parameters.put("simlarId", simlarId);
		parameters.put("registrationCode", registrationCode);

		return new ConfirmResult(httpPost(parameters, "simlarId", "registrationCode"));
	}

	private static Result httpPost(final Map<String, String> parameters,
			final String responseAttribute1, final String responseAttribute2)
	{
		final InputStream result = HttpsPost.post(URL_PATH, parameters);

		if (result == null) {
			return null;
		}

		Result parsedResult = null;
		try {
			parsedResult = parseXml(result, responseAttribute1, responseAttribute2);
		} catch (final XmlPullParserException e) {
			Lg.ex(LOGTAG, e, "parsing xml failed");
		} catch (final IOException e) {
			Lg.ex(LOGTAG, e, "IOException");
		}

		try {
			result.close();
		} catch (final IOException e) {
			Lg.ex(LOGTAG, e, "IOException");
		}

		return parsedResult;
	}

	private static Result parseXml(final InputStream inputStream, final String attribute1, final String attribute2)
			throws XmlPullParserException, IOException
	{
		final XmlPullParser parser = Xml.newPullParser();
		parser.setInput(inputStream, null);
		parser.nextTag();

		final String xmlRootElement = parser.getName();
		if (xmlRootElement.equalsIgnoreCase("error")
				&& parser.getAttributeCount() >= 2
				&& parser.getAttributeName(0).equalsIgnoreCase("id")
				&& parser.getAttributeName(1).equalsIgnoreCase("message"))
		{
			final int errorId = Integer.parseInt(parser.getAttributeValue(0));
			Lg.i(LOGTAG, "server returned error: ", parser.getAttributeValue(1), " (", Integer.valueOf(errorId), ")");
			return new Result(errorId, null, null);
		}

		if (xmlRootElement.equalsIgnoreCase("success")
				&& parser.getAttributeCount() >= 2
				&& parser.getAttributeName(0).equals(attribute1)
				&& parser.getAttributeName(1).equals(attribute2))
		{
			Lg.i(LOGTAG, "request success");
			return new Result(Result.SUCCESS, parser.getAttributeValue(0), parser.getAttributeValue(1));
		}

		Lg.e(LOGTAG, "unable to parse response: xmlRootElement=", xmlRootElement, " AttributeCount=", Integer.valueOf(parser.getAttributeCount()));
		return null;
	}
}

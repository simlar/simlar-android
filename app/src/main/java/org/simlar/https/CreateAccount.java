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

package org.simlar.https;

import android.util.Xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import org.simlar.R;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public final class CreateAccount
{
	private static final String URL_PATH = "create-account.xml";
	private static final String URL_PATH_CALL = "create-account-call.xml";

	private CreateAccount()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	private static class Result
	{
		static final int SUCCESS = 0;

		private final int mErrorId;
		private final String mResult1;
		final String mResult2;

		Result(final int errorId, final String result1, final String result2)
		{
			mErrorId = errorId;
			mResult1 = result1;
			mResult2 = result2;
		}

		Result(final Result result)
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

		public final boolean isError()
		{
			return mErrorId != SUCCESS || Util.isNullOrEmpty(mResult1) || Util.isNullOrEmpty(mResult2);
		}

		public final int getErrorMessage()
		{
			switch (mErrorId) {
			case 22:
				return R.string.create_account_activity_message_wrong_telephone_number;
			case 23: // Too many requests
				//noinspection DuplicateBranchesInSwitch
				return R.string.create_account_activity_message_not_possible;
			case 24:
				return R.string.create_account_activity_message_sms;
			case 25:
				return R.string.create_account_activity_message_too_many_confirms;
			case 26:
				return R.string.create_account_activity_message_registration_code;
			case 68:
				return R.string.create_account_activity_message_too_many_calls;
			default:
				return R.string.create_account_activity_message_not_possible;
			}
		}

		public final String getSimlarId()
		{
			return mResult1;
		}
	}

	public static final class RequestResult extends Result
	{
		private RequestResult(final Result result)
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
		private ConfirmResult(final Result result)
		{
			super(result);
		}
	}

	public static RequestResult httpPostRequest(final String telephoneNumber, final String smsText)
	{
		Lg.i("httpPostRequest: ", new Lg.Anonymizer(telephoneNumber));

		final Map<String, String> parameters = new HashMap<>();
		parameters.put("command", "request");
		parameters.put("telephoneNumber", telephoneNumber);
		parameters.put("smsText", smsText);

		return new RequestResult(httpPost(parameters, "simlarId", "password"));
	}

	public static RequestResult httpPostCall(final String telephoneNumber, final String password)
	{
		Lg.i("httpPostCall: ", new Lg.Anonymizer(telephoneNumber));

		final Map<String, String> parameters = new HashMap<>();
		parameters.put("telephoneNumber", telephoneNumber);
		parameters.put("password", password);

		return new RequestResult(httpPost(URL_PATH_CALL, parameters, "simlarId", "password"));
	}

	public static ConfirmResult httpPostConfirm(final String simlarId, final String registrationCode)
	{
		Lg.i("httpPostConfirm: simlarId=", new Lg.Anonymizer(simlarId), " registrationCode=", registrationCode);

		final Map<String, String> parameters = new HashMap<>();
		parameters.put("command", "confirm");
		parameters.put("simlarId", simlarId);
		parameters.put("registrationCode", registrationCode);

		return new ConfirmResult(httpPost(parameters, "simlarId", "registrationCode"));
	}

	private static Result httpPost(final String url, final Map<String, String> parameters,
	                               final String responseAttribute1, final String responseAttribute2)
	{
		final InputStream result = HttpsPost.post(url, parameters);

		if (result == null) {
			return null;
		}

		Result parsedResult = null;
		try {
			parsedResult = parseXml(result, responseAttribute1, responseAttribute2);
		} catch (final XmlPullParserException e) {
			Lg.ex(e, "parsing xml failed");
		} catch (final IOException e) {
			Lg.ex(e, "IOException");
		}

		try {
			result.close();
		} catch (final IOException e) {
			Lg.ex(e, "IOException");
		}

		return parsedResult;
	}

	@SuppressWarnings("SameParameterValue")
	private static Result httpPost(final Map<String, String> parameters,
	                               final String responseAttribute1, final String responseAttribute2)
	{
		return httpPost(URL_PATH, parameters, responseAttribute1, responseAttribute2);
	}

	private static Result parseXml(final InputStream inputStream, final String attribute1, final String attribute2)
			throws XmlPullParserException, IOException
	{
		final XmlPullParser parser = Xml.newPullParser();
		parser.setInput(inputStream, null);
		parser.nextTag();

		final String xmlRootElement = parser.getName();
		if ("error".equalsIgnoreCase(xmlRootElement)
				&& parser.getAttributeCount() >= 2
				&& "id".equalsIgnoreCase(parser.getAttributeName(0))
				&& "message".equalsIgnoreCase(parser.getAttributeName(1)))
		{
			final int errorId = Integer.parseInt(parser.getAttributeValue(0));
			Lg.i("server returned error: ", parser.getAttributeValue(1), " (", errorId, ")");
			return new Result(errorId, null, null);
		}

		if ("success".equalsIgnoreCase(xmlRootElement)
				&& parser.getAttributeCount() >= 2
				&& parser.getAttributeName(0).equals(attribute1)
				&& parser.getAttributeName(1).equals(attribute2))
		{
			Lg.i("request success");
			return new Result(Result.SUCCESS, parser.getAttributeValue(0), parser.getAttributeValue(1));
		}

		Lg.e("unable to parse response: xmlRootElement=", xmlRootElement, " AttributeCount=", parser.getAttributeCount());
		return null;
	}
}

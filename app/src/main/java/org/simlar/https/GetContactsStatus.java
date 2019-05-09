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

import android.text.TextUtils;
import android.util.Xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import org.simlar.helper.ContactStatus;
import org.simlar.helper.PreferencesHelper;
import org.simlar.helper.PreferencesHelper.NotInitedException;
import org.simlar.logging.Lg;

public final class GetContactsStatus
{
	private static final String URL_PATH = "get-contacts-status.php";

	private GetContactsStatus()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static Map<String, ContactStatus> httpPostGetContactsStatus(final Set<String> contacts)
	{
		Lg.i("httpPostGetContactsStatus requested");

		try {
			final Map<String, String> parameters = new HashMap<>();
			parameters.put("login", PreferencesHelper.getMySimlarId());
			parameters.put("password", PreferencesHelper.getPasswordHash());
			parameters.put("contacts", TextUtils.join("|", contacts));

			final InputStream result = HttpsPost.post(URL_PATH, parameters);

			if (result == null) {
				return null;
			}

			Map<String, ContactStatus> parsedResult = null;
			try {
				parsedResult = parseXml(result);
			} catch (final XmlPullParserException e) {
				Lg.ex(e, "parsing xml failed");
			} catch (final IOException e) {
				Lg.ex(e, "IOException: ");
			}

			try {
				result.close();
			} catch (final IOException e) {
				Lg.ex(e, "IOException: ");
			}

			return parsedResult;

		} catch (final NotInitedException e) {
			Lg.ex(e, "PreferencesHelper.NotInitedException");
			return null;
		}
	}

	private static Map<String, ContactStatus> parseXml(final InputStream inputStream) throws XmlPullParserException, IOException
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
			Lg.e("server returned error: ", parser.getAttributeValue(1));
			return null;
		}

		if (!"contacts".equalsIgnoreCase(xmlRootElement)) {
			Lg.e("unable to parse response");
			return null;
		}

		final Map<String, ContactStatus> parsedResult = new HashMap<>();
		while (parser.next() != XmlPullParser.END_DOCUMENT) {
			if (parser.getEventType() != XmlPullParser.START_TAG) {
				continue;
			}

			if (!"contact".equalsIgnoreCase(parser.getName())
					|| parser.getAttributeCount() < 2
					|| !"id".equalsIgnoreCase(parser.getAttributeName(0))
					|| !"status".equalsIgnoreCase(parser.getAttributeName(1)))
			{
				continue;
			}

			final String id = parser.getAttributeValue(0);
			final ContactStatus status = ContactStatus.fromInt(Integer.parseInt(parser.getAttributeValue(1)));

			parsedResult.put(id, status);
		}
		return parsedResult;
	}
}

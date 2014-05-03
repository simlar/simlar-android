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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.simlar.PreferencesHelper.NotInitedException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

public final class GetContactsStatus
{
	private static final String LOGTAG = GetContactsStatus.class.getSimpleName();

	private static final String URL_PATH = "get-contacts-status.php";

	public static Map<String, ContactStatus> httpPostGetContactsStatus(final Set<String> contacts)
	{
		Log.i(LOGTAG, "httpPostGetContactsStatus requested");

		try {
			final Map<String, String> parameters = new HashMap<String, String>();
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
				Log.e(LOGTAG, "parsing xml failed: " + e.getMessage(), e);
			} catch (final IOException e) {
				Log.e(LOGTAG, "IOException: " + e.getMessage(), e);
			}

			try {
				result.close();
			} catch (final IOException e) {
				Log.e(LOGTAG, "IOException: " + e.getMessage(), e);
			}

			return parsedResult;

		} catch (final NotInitedException e) {
			Log.e(LOGTAG, "PreferencesHelper.NotInitedException", e);
			return null;
		}
	}

	private static Map<String, ContactStatus> parseXml(final InputStream inputStream) throws XmlPullParserException, IOException
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
			Log.e(LOGTAG, "server returned error: " + parser.getAttributeValue(1));
			return null;
		}

		if (!xmlRootElement.equalsIgnoreCase("contacts")) {
			Log.e(LOGTAG, "unable to parse response");
			return null;
		}

		final Map<String, ContactStatus> parsedResult = new HashMap<String, ContactStatus>();
		while (parser.next() != XmlPullParser.END_DOCUMENT) {
			if (parser.getEventType() != XmlPullParser.START_TAG) {
				continue;
			}

			if (!parser.getName().equalsIgnoreCase("contact")
					|| parser.getAttributeCount() < 2
					|| !parser.getAttributeName(0).equalsIgnoreCase("id")
					|| !parser.getAttributeName(1).equalsIgnoreCase("status"))
			{
				continue;
			}

			final String id = parser.getAttributeValue(0);
			final ContactStatus status = ContactStatus.fromInt(Integer.parseInt(parser.getAttributeValue(1)));

			if (status.isRegistered()) {
				Log.i(LOGTAG, "id=" + id + " " + status);
			}
			parsedResult.put(id, status);
		}
		return parsedResult;
	}
}

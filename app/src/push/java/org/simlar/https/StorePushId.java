/**
 * Copyright (C) 2014 The Simlar Authors.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.simlar.helper.PreferencesHelper.NotInitedException;
import org.simlar.helper.PreferencesHelper;
import org.simlar.logging.Lg;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Xml;

public final class StorePushId
{
	private static final String URL_PATH = "store-push-id.php";
	private static final int DEVICE_TYPE_ANDROID = 1;

	private StorePushId()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean httpPostStorePushId(final String pushId)
	{
		Lg.i("httpPostStorePushId requested");

		try {
			final Map<String, String> parameters = new HashMap<>();
			parameters.put("login", PreferencesHelper.getMySimlarId());
			parameters.put("password", PreferencesHelper.getPasswordHash());
			parameters.put("deviceType", Integer.toString(DEVICE_TYPE_ANDROID));
			parameters.put("pushId", pushId);

			final InputStream result = HttpsPost.post(URL_PATH, parameters);

			if (result == null) {
				return false;
			}

			boolean success = false;
			try {
				success = parseXml(result, pushId);
			} catch (final XmlPullParserException e) {
				Lg.ex(e, "parsing xml failed");
			} catch (final IOException e) {
				Lg.ex(e, "IOException in InputStream of HttpsPost");
			}

			try {
				result.close();
			} catch (final IOException e) {
				Lg.ex(e, "IOException during close");
			}

			return success;

		} catch (final NotInitedException e) {
			Lg.ex(e, "PreferencesHelper.NotInitedException");
			return false;
		}
	}

	private static boolean parseXml(final InputStream inputStream, final String pushId) throws XmlPullParserException, IOException
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
			return false;
		}

		if ("success".equalsIgnoreCase(xmlRootElement)
				&& parser.getAttributeCount() >= 2
				&& "deviceType".equalsIgnoreCase(parser.getAttributeName(0))
				&& parser.getAttributeValue(0).equals(Integer.toString(DEVICE_TYPE_ANDROID))
				&& "pushId".equalsIgnoreCase(parser.getAttributeName(1))
				&& parser.getAttributeValue(1).equals(pushId))
		{
			return true;
		}

		Lg.e("parse error: ", parser.getPositionDescription());
		return false;
	}
}

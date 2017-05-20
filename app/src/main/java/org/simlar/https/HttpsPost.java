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

import org.simlar.helper.ServerSettings;
import org.simlar.logging.Lg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

final class HttpsPost
{
	private static final String SERVER_URL = "https://" + ServerSettings.DOMAIN + ":6161/";

	private static final int MAX_RETRIES = 5;

	private static final char PARAMETER_DELIMITER = '&';
	private static final char PARAMETER_EQUALS_CHAR = '=';

	public static final String DATA_BOUNDARY = "*****";

	private HttpsPost()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	private static String createQueryStringForParameters(final Map<String, String> parameters)
	{
		final StringBuilder parametersAsQueryString = new StringBuilder();
		if (parameters != null) {
			boolean firstParameter = true;

			for (final Map.Entry<String, String> parameter : parameters.entrySet()) {
				if (!firstParameter) {
					parametersAsQueryString.append(PARAMETER_DELIMITER);
				}

				try {
					parametersAsQueryString.append(parameter.getKey())
							.append(PARAMETER_EQUALS_CHAR)
							.append(URLEncoder.encode(parameter.getValue(), "UTF-8"));
				} catch (final UnsupportedEncodingException e) {
					Lg.ex(e, "UnsupportedEncodingException");
				}

				firstParameter = false;
			}
		}
		return parametersAsQueryString.toString();
	}

	public static HttpsURLConnection createConnection(final String urlPath, final boolean multiPart)
	{
		//noinspection OverlyBroadCatchBlock
		try {
			final URL url = new URL(SERVER_URL + "/" + urlPath);

			final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
			connection.setSSLSocketFactory(SimlarSSLSocketFactory.getInstance());
			connection.setDoInput(true); // default
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestMethod("POST");
			if (multiPart) {
				connection.setRequestProperty("Connection", "Keep-Alive");
				connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + DATA_BOUNDARY);
			} else {
				connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			}

			Lg.v("created connection for: ", urlPath);
			return connection;
		} catch (final IOException e) {
			Lg.ex(e);
		}

		Lg.e("failed to create connection for: ", urlPath);
		return null;
	}

	public static InputStream post(final String urlPath, final Map<String, String> parameters)
	{
		for (int i = 0; i <= MAX_RETRIES; ++i) {
			if (i != 0) {
				try {
					Lg.i("sleeping 500ms before retrying post: ", urlPath);
					Thread.sleep(500);
				} catch (final InterruptedException e) {
					Lg.ex(e, "sleep interrupted");
				}
			}

			final InputStream postResponse = postPrivate(urlPath, parameters);
			if (postResponse != null) {
				return postResponse;
			}
		}
		return null;
	}

	private static OutputStream getOutputStream(final HttpsURLConnection connection)
	{
		if (connection == null) {
			return null;
		}

		try {
			return connection.getOutputStream();
		} catch (final IOException e) {
			Lg.ex(e, "IOException while getting OutputStream");
			return null;
		}
	}

	private static InputStream postPrivate(final String urlPath, final Map<String, String> parameters)
	{
		final HttpsURLConnection connection = createConnection(urlPath, false);

		final OutputStream stream = getOutputStream(connection);
		if (stream == null) {
			return null;
		}

		final PrintWriter out = new PrintWriter(stream);
		try {
			out.print(createQueryStringForParameters(parameters));

			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				Lg.e("server response error(", connection.getResponseCode(), "): ", connection.getResponseMessage());
				return null;
			}

			Lg.i("used CipherSuite: ", connection.getCipherSuite());
			return connection.getInputStream();
		} catch (final IOException e) {
			Lg.ex(e, "IOException while posting");
			return null;
		} finally {
			out.close();
		}
	}
}

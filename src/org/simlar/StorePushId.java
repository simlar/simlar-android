package org.simlar;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.simlar.PreferencesHelper.NotInitedException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;
import android.util.Xml;

public final class StorePushId
{
	private static final String LOGTAG = StorePushId.class.getSimpleName();
	private static final String URL_PATH = "store-push-id.php";
	private static final int DEVICE_TYPE_ANDROID = 1;

	public static boolean httpPostStorePushId(final String pushId)
	{
		Log.i(LOGTAG, "httpPostStorePushId requested");

		try {
			final Map<String, String> parameters = new HashMap<String, String>();
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
				Log.e(LOGTAG, "parsing xml failed: " + e.getMessage(), e);
			} catch (final IOException e) {
				Log.e(LOGTAG, "IOException: " + e.getMessage(), e);
			}

			try {
				result.close();
			} catch (final IOException e) {
				Log.e(LOGTAG, "IOException: " + e.getMessage(), e);
			}

			return success;

		} catch (final NotInitedException e) {
			Log.e(LOGTAG, "PreferencesHelper.NotInitedException", e);
			return false;
		}
	}

	private static boolean parseXml(final InputStream inputStream, final String pushId) throws XmlPullParserException, IOException
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
			return false;
		}

		if (xmlRootElement.equalsIgnoreCase("success")
				&& parser.getAttributeCount() >= 2
				&& parser.getAttributeName(0).equalsIgnoreCase("devicetype")
				&& parser.getAttributeValue(0).equals(Integer.toString(DEVICE_TYPE_ANDROID))
				&& parser.getAttributeName(1).equalsIgnoreCase("pushid")
				&& parser.getAttributeValue(1).equals(pushId))
		{
			return true;
		}

		Log.e(LOGTAG, "parse error: " + parser.getPositionDescription());
		return false;
	}
}

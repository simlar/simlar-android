package org.simlar;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.simlar.PreferencesHelper.NotInitedException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Xml;

public class GetMissedCalls
{
	private static final String LOGTAG = GetMissedCalls.class.getSimpleName();
	private static final String URL_PATH = "get-missed-calls.php";
	private static final SimpleDateFormat mParser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

	public static final class Call
	{
		private final long mTime;
		private final String mSimlarId;

		public Call(final long time, final String simlarId)
		{
			mTime = time;
			mSimlarId = simlarId;
		}

		public long getTime()
		{
			return mTime;
		}

		public String getSimlarId()
		{
			return mSimlarId;
		}

		@Override
		public String toString()
		{
			return "Call [time=" + mTime + ", simlarId=" + mSimlarId + "]";
		}
	}

	private GetMissedCalls()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static List<Call> httpPostGetMissedCalls(final String time)
	{
		Lg.i(LOGTAG, "httpPostGetMissedCalls requested");

		try {
			final Map<String, String> parameters = new HashMap<String, String>();
			parameters.put("login", PreferencesHelper.getMySimlarId());
			parameters.put("password", PreferencesHelper.getPasswordHash());
			parameters.put("time", time);

			final InputStream result = HttpsPost.post(URL_PATH, parameters);

			if (result == null) {
				return null;
			}

			List<Call> parsedResult = null;
			try {
				parsedResult = parseXml(result);
			} catch (final XmlPullParserException e) {
				Lg.ex(LOGTAG, e, "parsing xml failed");
			} catch (final IOException e) {
				Lg.ex(LOGTAG, e, "IOException: ");
			}

			try {
				result.close();
			} catch (final IOException e) {
				Lg.ex(LOGTAG, e, "IOException: ");
			}

			return parsedResult;

		} catch (final NotInitedException e) {
			Lg.ex(LOGTAG, e, "PreferencesHelper.NotInitedException");
			return null;
		}
	}

	private static List<Call> parseXml(final InputStream inputStream) throws XmlPullParserException, IOException
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
			Lg.e(LOGTAG, "server returned error: ", parser.getAttributeValue(1));
			return null;
		}

		if (!xmlRootElement.equalsIgnoreCase("calls")) {
			Lg.e(LOGTAG, "unable to parse response");
			return null;
		}

		final List<Call> parsedResult = new ArrayList<Call>();
		while (parser.next() != XmlPullParser.END_DOCUMENT) {
			if (parser.getEventType() != XmlPullParser.START_TAG) {
				continue;
			}

			if (!parser.getName().equalsIgnoreCase("call")
					|| parser.getAttributeCount() < 2
					|| !parser.getAttributeName(0).equalsIgnoreCase("time")
					|| !parser.getAttributeName(1).equalsIgnoreCase("simlarId"))
			{
				continue;
			}

			parsedResult.add(new Call(parseMissedCallTime(parser.getAttributeValue(0)), parser.getAttributeValue(1)));
		}
		return parsedResult;
	}

	static long parseMissedCallTime(final String callTime)
	{
		try {
			final Date date = mParser.parse(callTime);
			if (date == null) {
				Lg.e(LOGTAG, "Error: parsing date='", callTime, "' failed");
				return -1;
			}
			return date.getTime();
		} catch (final ParseException e) {
			Lg.ex(LOGTAG, e, "Exception: parsing date='", callTime, "' failed");
			return -1;
		}
	}
}

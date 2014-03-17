package org.simlar;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public final class Version
{
	private static final String LOGTAG = CreateAccount.class.getSimpleName();
	private static final String SPECIAL_TAG = "";

	public static String getVersionName(final Context context)
	{
		if (context == null) {
			return "";
		}

		try {
			return SPECIAL_TAG + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			Log.e(LOGTAG, "NameNotFoundException in Util.getVersionName: " + e.getMessage(), e);
			return "";
		}
	}
}

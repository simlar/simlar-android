package org.simlar;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

public final class Version
{
	private static final String LOGTAG = CreateAccount.class.getSimpleName();
	private static final String SPECIAL_TAG = "";

	public static String getVersionName(final Context context)
	{
		final String versionName = getPackageInfo(context).versionName;
		if (Util.isNullOrEmpty(versionName)) {
			return "";
		}

		return SPECIAL_TAG + versionName;
	}

	public static int getVersionCode(final Context context)
	{
		return getPackageInfo(context).versionCode;
	}

	private static PackageInfo createEmptyPackageInfo()
	{
		final PackageInfo info = new PackageInfo();
		info.versionName = "";
		info.versionCode = -1;
		return info;
	}

	private static PackageInfo getPackageInfo(final Context context)
	{
		if (context == null) {
			return createEmptyPackageInfo();
		}

		try {
			return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		} catch (final NameNotFoundException e) {
			Log.e(LOGTAG, "NameNotFoundException in Version.getPackageInfo: " + e.getMessage(), e);
			return createEmptyPackageInfo();
		}
	}
}

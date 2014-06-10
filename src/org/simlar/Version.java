package org.simlar;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

public final class Version
{
	private static final String LOGTAG = CreateAccount.class.getSimpleName();
	private static final String DEBUG_TAG = "angry-smurf268: ";

	private Version()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static String getVersionName(final Context context)
	{
		final String versionName = getPackageInfo(context).versionName;
		if (Util.isNullOrEmpty(versionName)) {
			return "";
		}

		return DEBUG_TAG + versionName;
	}

	public static int getVersionCode(final Context context)
	{
		return getPackageInfo(context).versionCode;
	}

	public static boolean hasDebugTag()
	{
		return !Util.isNullOrEmpty(DEBUG_TAG);
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
			Lg.ex(LOGTAG, e, "NameNotFoundException in Version.getPackageInfo:");
			return createEmptyPackageInfo();
		}
	}
}

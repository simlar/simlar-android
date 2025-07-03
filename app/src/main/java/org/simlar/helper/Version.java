package org.simlar.helper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import org.simlar.logging.Lg;
import org.simlar.utils.Util;

public final class Version
{
	private static final boolean DEVELOPER_MENU = true;

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

		return versionName;
	}

	public static int getVersionCode(final Context context)
	{
		return getPackageInfo(context).versionCode;
	}

	public static boolean showDeveloperMenu()
	{
		return DEVELOPER_MENU;
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
			Lg.ex(e, "NameNotFoundException in Version.getPackageInfo:");
			return createEmptyPackageInfo();
		}
	}
}

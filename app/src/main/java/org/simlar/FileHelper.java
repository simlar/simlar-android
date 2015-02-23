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

package org.simlar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;

public final class FileHelper
{
	private static final String LOGTAG = FileHelper.class.getSimpleName();

	private static String mRootCaFileName = null;
	private static String mZrtpSecretsCacheFileName = null;
	private static String mLinphoneInitialConfigFile = null;
	private static String mFakePhoneBookPicture = null;
	private static String mPauseSoundFile = null;

	private FileHelper()
	{
		throw new AssertionError("This class was not meant to be instantiated");
	}

	public static void init(final Context context)
	{
		if (isInitialized()) {
			Lg.w(LOGTAG, "already inited => aborting");
			return;
		}

		final String basePath = context.getFilesDir().getAbsolutePath();
		Lg.i(LOGTAG, "using basePath: ", basePath);

		mRootCaFileName = basePath + "/rootca.pem";
		mZrtpSecretsCacheFileName = basePath + "/zrtp_secrets";
		mLinphoneInitialConfigFile = basePath + "/linphonerc";
		mFakePhoneBookPicture = basePath + "/fake_phone_book_picture.png";
		mPauseSoundFile = basePath + "/pause.wav";

		// Always overwrite to make updates of this file work
		copyFileFromPackage(context, R.raw.rootca, new File(mRootCaFileName).getName());
		copyFileFromPackage(context, R.raw.ringback, new File(basePath + "/ringback.wav").getName());
		copyFileFromPackage(context, R.raw.linphonerc, new File(mLinphoneInitialConfigFile).getName());
		copyFileFromPackage(context, R.raw.fake_phone_book_picture, new File(mFakePhoneBookPicture).getName());
		copyFileFromPackage(context, R.raw.pause, new File(mPauseSoundFile).getName());
	}

	public static boolean isInitialized()
	{
		return !Util.isNullOrEmpty(mRootCaFileName) &&
				!Util.isNullOrEmpty(mZrtpSecretsCacheFileName) &&
				!Util.isNullOrEmpty(mLinphoneInitialConfigFile) &&
				!Util.isNullOrEmpty(mFakePhoneBookPicture) &&
				!Util.isNullOrEmpty(mPauseSoundFile);
	}

	private static void copyFileFromPackage(final Context context, final int resourceId, final String target)
	{
		try {
			final FileOutputStream outputStream = context.openFileOutput(target, 0);
			final InputStream inputStream = context.getResources().openRawResource(resourceId);
			Util.copyStream(inputStream, outputStream);
			outputStream.flush();
			outputStream.close();
			inputStream.close();
			Lg.i(LOGTAG, "created ", target);
		} catch (final IOException e) {
			Lg.ex(LOGTAG, e, "IOException: failed to create: ", target);
		}
	}

	public static final class NotInitedException extends Exception
	{
		private static final long serialVersionUID = -6789607195211210408L;
	}

	public static String getRootCaFileName() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mRootCaFileName)) {
			throw new NotInitedException();
		}
		return mRootCaFileName;
	}

	public static String getZrtpSecretsCacheFileName() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mZrtpSecretsCacheFileName)) {
			throw new NotInitedException();
		}
		return mZrtpSecretsCacheFileName;
	}

	public static String getLinphoneInitialConfigFile() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mLinphoneInitialConfigFile)) {
			throw new NotInitedException();
		}
		return mLinphoneInitialConfigFile;
	}

	public static String getFakePhoneBookPicture() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mFakePhoneBookPicture)) {
			throw new NotInitedException();
		}
		return mFakePhoneBookPicture;
	}

	public static String getPauseSoundFile() throws NotInitedException
	{
		if (Util.isNullOrEmpty(mPauseSoundFile)) {
			throw new NotInitedException();
		}
		return mPauseSoundFile;
	}
}

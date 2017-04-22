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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;

import org.simlar.R;
import org.simlar.helper.ServerSettings;
import org.simlar.helper.Version;
import org.simlar.logging.Lg;
import org.simlar.utils.Util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

public final class UploadLogFile
{
	private static final String EMAIL_ADDRESS = "support@simlar.org";
	private static final String EMAIL_SUBJECT = "bug report to log file ";
	private static final String EMAIL_TEXT = "\r\n\r\n"
			+ "Please put in your bug description here. It may be in German or English"
			+ "\r\n\r\n"
			+ "Please do not delete the following link as it helps developers to identify your logfile"
			+ "\r\n";
	private static final String URL_PATH = "upload-logfile.php";
	private static final String UPLOAD_SFTP_LINK = "sftp://root@" + ServerSettings.DOMAIN + "/var/www/simlar/logfiles/";

	private static final String LINE_END = "\r\n";
	private static final String TWO_HYPHENS = "--";

	private Context mContext = null;
	private ProgressDialog mProgressDialog = null;

	private final class PostResult
	{
		final boolean success;
		final String fileName;
		final String errorMessage;

		PostResult(final boolean success, final String message)
		{
			this.success = success;
			if (success) {
				fileName = message;
				errorMessage = null;
			} else {
				fileName = null;
				errorMessage = message;
			}
		}
	}

	private PostResult postFile(final File file)
	{

		final HttpsURLConnection connection = HttpsPost.createConnection(URL_PATH, true);
		if (connection == null) {
			return new PostResult(false, "Building connection failed");
		}

		PostResult result;
		DataOutputStream outputStream = null;
		FileInputStream fileInputStream = null;
		InputStream inputStream = null;
		ByteArrayOutputStream responseStream = null;
		try {
			outputStream = new DataOutputStream(connection.getOutputStream());
			outputStream.writeBytes(TWO_HYPHENS + HttpsPost.DATA_BOUNDARY + LINE_END);
			outputStream.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\""
					+ file.getAbsolutePath() + "\"" + LINE_END);
			outputStream.writeBytes(LINE_END);

			// copy log file
			fileInputStream = new FileInputStream(file);
			Util.copyStream(fileInputStream, outputStream);

			outputStream.writeBytes(LINE_END);
			outputStream.writeBytes(TWO_HYPHENS + HttpsPost.DATA_BOUNDARY + TWO_HYPHENS + LINE_END);
			outputStream.flush();

			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
				inputStream = connection.getInputStream();
				responseStream = new ByteArrayOutputStream();
				Util.copyStream(inputStream, responseStream);
				final String response = new String(responseStream.toByteArray());
				Lg.i("used CipherSuite: ", connection.getCipherSuite());
				Lg.i("Response ", response);

				result = response.matches("OK \\d*") ? new PostResult(true, file.getName()) : new PostResult(false, response);
			} else {
				result = new PostResult(false, connection.getResponseMessage());
				Lg.w("posting file failed with error code ", connection.getResponseMessage(), " and message ",
						connection.getResponseMessage());
			}
		} catch (final Exception e) {
			result = new PostResult(false, "Posting log file failed");
			Lg.ex(e, "Exception during postFile");
		}

		try {
			if (outputStream != null) {
				outputStream.close();
			}
		} catch (final IOException e) {
			Lg.ex(e, "Exception during outputStream.close()");
		}
		try {
			if (fileInputStream != null) {
				fileInputStream.close();
			}
		} catch (final IOException e) {
			Lg.ex(e, "Exception during fileInputStream.close()");
		}
		try {
			if (inputStream != null) {
				inputStream.close();
			}
		} catch (final IOException e) {
			Lg.ex(e, "Exception during inputStream.close()");
		}
		try {
			if (responseStream != null) {
				responseStream.close();
			}
		} catch (final IOException e) {
			Lg.ex(e, "Exception during responseStream.close()");
		}

		return result;
	}

	public UploadLogFile(final Context context)
	{
		mContext = context;

		mProgressDialog = new ProgressDialog(mContext);
		mProgressDialog.setMessage(mContext.getString(R.string.upload_log_file_progress));
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
	}

	private static void deleteFile(final File file)
	{
		if (!file.delete()) {
			Lg.w("deleting file failed: ", file.getName());
		}
	}

	public void upload(final String fileName)
	{
		if (mContext == null) {
			Lg.w("no context");
			return;
		}

		Lg.i("uploading log file started: ", fileName);
		Lg.i("simlar version=", Version.getVersionName(mContext),
				" on device: ", Build.MANUFACTURER, " ", Build.MODEL, " (", Build.DEVICE, ") with android version=", Build.VERSION.RELEASE);

		new AsyncTask<File, Void, PostResult>()
		{
			@Override
			protected PostResult doInBackground(final File... logFiles)
			{
				final File logFile = logFiles[0];
				deleteFile(logFile);

				try {
					final Process p = Runtime.getRuntime().exec("logcat -d -v threadtime -f " + logFile.getAbsolutePath());
					p.waitFor();
					return postFile(logFile);
				} catch (final Exception e) {
					Lg.ex(e, "Exception during log file creation");
					return new PostResult(false, "Log file creation failed");
				} finally {
					deleteFile(logFile);
				}
			}

			@Override
			protected void onPreExecute()
			{
				if (mProgressDialog == null) {
					Lg.w("no progress dialog");
					return;
				}

				mProgressDialog.show();
			}

			@Override
			protected void onPostExecute(final PostResult result)
			{
				mProgressDialog.dismiss();
				if (!result.success) {
					Lg.e("aborting uploading log file: ", result.errorMessage);
					new AlertDialog.Builder(mContext)
							.setTitle(R.string.main_activity_alert_uploading_log_file_failed_title)
							.setMessage(mContext.getString(R.string.main_activity_alert_uploading_log_file_failed_text) + ": " + result.errorMessage)
							.create().show();
					return;
				}

				Lg.i("sending email for logfile: ", result.fileName);

				final Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("message/rfc822");
				sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { EMAIL_ADDRESS });
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT + result.fileName);
				sendIntent.putExtra(Intent.EXTRA_TEXT, EMAIL_TEXT + UPLOAD_SFTP_LINK + result.fileName);
				try {
					mContext.startActivity(Intent.createChooser(sendIntent, mContext.getString(R.string.upload_log_file_send_email_to_developer)));
				} catch (final ActivityNotFoundException e) {
					Lg.ex(e, "ActivityNotFoundException chooser_send_email");
				}
			}
		}.execute(new File(mContext.getCacheDir(), fileName));
	}
}

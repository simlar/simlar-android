/**
 * Copyright (C) 2013 The Simlar Authors.
 *
 * This file is part of Simlar. (http://www.simlar.org)
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

public final class UploadLogFile
{
	static final String LOGTAG = UploadLogFile.class.getSimpleName();
	private static final String EMAIL_ADDRESS = "mail@ben-sartor.de";
	private static final String EMAIL_SUBJECT = "bug report to log file ";
	private static final String EMAIL_TEXT = "\r\n\r\n"
			+ "Please put in your bug description here. It may be in German or English"
			+ "\r\n\r\n"
			+ "Please do not delete the following link as it helps developers to identify your logfile"
			+ "\r\n";
	private static final String URL_PATH = "upload-logfile.php";
	private static final String UPLOAD_SFTP_LINK = "sftp://root@sip.simlar.org/var/www/simlar/logfiles/";

	private static final String LINE_END = "\r\n";
	private static final String TWO_HYPHENS = "--";

	Context mContext = null;
	ProgressDialog mProgressDialog = null;

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

	PostResult postFile(final File file)
	{

		final HttpsURLConnection connection = HttpsPost.createConnection(URL_PATH, true);
		if (connection == null) {
			return new PostResult(false, "Building connection failed");
		}

		PostResult result = null;
		DataOutputStream outputStream = null;
		FileInputStream fileInputStream = null;
		InputStream inputStream = null;
		ByteArrayOutputStream baos = null;
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

			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				result = new PostResult(false, connection.getResponseMessage());
				Log.w(LOGTAG,
						"posting file failed with error code " + connection.getResponseMessage() + " and message " + connection.getResponseMessage());
			} else {
				inputStream = connection.getInputStream();
				baos = new ByteArrayOutputStream();
				Util.copyStream(inputStream, baos);
				final String response = new String(baos.toByteArray());
				Log.i(LOGTAG, "used CipherSuite: " + connection.getCipherSuite());
				Log.i(LOGTAG, "Response " + response);

				if (response.matches("OK \\d*")) {
					result = new PostResult(true, file.getName());
				} else {
					result = new PostResult(false, response);
				}
			}
		} catch (Exception e) {
			result = new PostResult(false, "Posting log file failed");
			Log.e(LOGTAG, "Exception during postFile: " + e.getMessage(), e);
		}

		try {
			if (outputStream != null) {
				outputStream.close();
			}
		} catch (IOException e) {
			Log.e(LOGTAG, "Exception during outputStream.close(): " + e.getMessage(), e);
		}
		try {
			if (fileInputStream != null) {
				fileInputStream.close();
			}
		} catch (IOException e) {
			Log.e(LOGTAG, "Exception during fileInputStream.close(): " + e.getMessage(), e);
		}
		try {
			if (inputStream != null) {
				inputStream.close();
			}
		} catch (IOException e) {
			Log.e(LOGTAG, "Exception during inputStream.close(): " + e.getMessage(), e);
		}
		try {
			if (baos != null) {
				baos.close();
			}
		} catch (IOException e) {
			Log.e(LOGTAG, "Exception during baos.close(): " + e.getMessage(), e);
		}

		return result;
	}

	public UploadLogFile(final Context context)
	{
		mContext = context;

		mProgressDialog = new ProgressDialog(mContext);
		mProgressDialog.setMessage(mContext.getString(R.string.progress_uploading_log_file));
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setCancelable(false);
	}

	public void upload(final String fileName)
	{
		if (mContext == null) {
			Log.w(LOGTAG, "no context");
			return;
		}

		Log.i(LOGTAG, "uploading log file started: " + fileName);
		Log.i(LOGTAG, "simlar version: " + Version.getVersionName(mContext));
		Log.i(LOGTAG, "running on device: " + Build.DEVICE);

		new AsyncTask<File, Void, PostResult>() {
			@Override
			protected PostResult doInBackground(File... logFiles)
			{
				final File logFile = logFiles[0];
				logFile.delete();

				try {
					final Process p = Runtime.getRuntime().exec("logcat -d -v threadtime -f " + logFile.getAbsolutePath());
					p.waitFor();
					return postFile(logFile);
				} catch (Exception e) {
					Log.e(LOGTAG, "Exception during log file creation: " + e.getMessage(), e);
					return new PostResult(false, "Log file creation failed");
				} finally {
					logFile.delete();
				}
			}

			@Override
			protected void onPreExecute()
			{
				if (mProgressDialog == null) {
					Log.w(LOGTAG, "no progress dialog");
					return;
				}

				mProgressDialog.show();
			}

			@Override
			protected void onPostExecute(PostResult result)
			{
				mProgressDialog.dismiss();
				if (!result.success) {
					Log.e(LOGTAG, "aborting uploading log file: " + result.errorMessage);
					(new AlertDialog.Builder(mContext))
							.setTitle(R.string.alert_title_uploading_log_file_failed)
							.setMessage(mContext.getString(R.string.alert_text_uploading_log_file_failed) + ": " + result.errorMessage)
							.create().show();
					return;
				}

				Log.i(LOGTAG, "sending email for logfile: " + result.fileName);

				final Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("message/rfc822");
				sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { EMAIL_ADDRESS });
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT + result.fileName);
				sendIntent.putExtra(Intent.EXTRA_TEXT, EMAIL_TEXT + UPLOAD_SFTP_LINK + result.fileName);
				try {
					mContext.startActivity(Intent.createChooser(sendIntent, mContext.getString(R.string.chooser_send_email)));
				} catch (android.content.ActivityNotFoundException e) {
					Log.e(LOGTAG, "ActivityNotFoundException chooser_send_email: " + e.getMessage(), e);
				}
			}
		}.execute(new File(mContext.getCacheDir(), fileName));
	}
}

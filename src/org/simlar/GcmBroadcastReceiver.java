package org.simlar;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

public final class GcmBroadcastReceiver extends WakefulBroadcastReceiver
{
	static final String LOGTAG = GcmBroadcastReceiver.class.getSimpleName();

	@Override
	public void onReceive(final Context context, final Intent intent)
	{
		final Bundle extras = intent.getExtras();
		if (extras.isEmpty()) {
			Log.e(LOGTAG, "received Google Cloud Messaging Event with empty extras");
			return;
		}

		final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
		if (gcm == null) {
			Log.e(LOGTAG, "unable to instantiate Google Cloud Messaging");
			return;
		}

		final String messageType = gcm.getMessageType(intent);

		if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
			Log.i(LOGTAG, "received: " + extras.toString());
			startWakefulService(context, intent.setComponent(new ComponentName(context.getPackageName(), SimlarService.class.getName())));
			setResultCode(Activity.RESULT_OK);
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
			Log.e(LOGTAG, "send error: " + extras.toString());
		} else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
			Log.e(LOGTAG, "deleted messages on server: " + extras.toString());
		} else {
			Log.e(LOGTAG, "received Google Cloud Messaging Event with unknown message type: " + messageType);
		}
	}
}


package de.pentabarf.cryptmessaging;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

@SuppressWarnings("static-access")
public class SmsStatusReceiver extends BroadcastReceiver {

    private static final Uri STATUS_URI = Uri.parse("content://sms/status");

    private static final String TAG = "SmsStatusReceiver";

    public static void handleSmsReceived(Context ctx, Uri uri,
            SmsMessage message) {
        // retrieve message id
        Cursor cursor = ctx.getContentResolver().query(uri,
                new String[] {
                    Telephony.Sms._ID
                }, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                int messageId = cursor.getInt(0);

                // create uri for message status entry
                Uri updateUri = ContentUris.withAppendedId(STATUS_URI,
                        messageId);

                int status = message.getStatus();

                Log.i(TAG, "handleSmsReceived=" + status);

                ContentValues contentValues = new ContentValues(2);
                contentValues.put(Telephony.Sms.STATUS, status);
                contentValues.put(Telephony.Sms.Inbox.DATE_SENT,
                        System.currentTimeMillis());

                // update message status
                ctx.getContentResolver().update(updateUri, contentValues, null,
                        null);

                // TODO toast
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public static void handleSmsSent(Context ctx, Uri uri, int result, int error) {
        Log.i(TAG, "handleSmsSent(" + result + "," + error + ")");
        // move message to SENT folder (and implicitly mark error=0) if
        // result=OK
        Telephony.Sms.moveMessageToFolder(ctx, uri,
                (result == Activity.RESULT_OK) ? Telephony.Sms.MESSAGE_TYPE_SENT
                        : Telephony.Sms.MESSAGE_TYPE_QUEUED, error);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int result = getResultCode();
        int error = intent.getIntExtra("errorCode", 0);
        Uri uri = intent.getData();

        if (CryptCompose.MESSAGE_SENT_ACTION.equals(intent.getAction())) {
            handleSmsSent(ctx, uri, result, error);
        } else if (CryptCompose.MESSAGE_STATUS_RECEIVED_ACTION.equals(intent
                .getAction())) {
            Log.i(TAG, "handleSmsReceived");
            byte[] pdu = intent.getByteArrayExtra("pdu");
            SmsMessage message = SmsMessage.createFromPdu(pdu);

            handleSmsReceived(ctx, uri, message);
        }
    }
}

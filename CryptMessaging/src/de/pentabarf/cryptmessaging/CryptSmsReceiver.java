
package de.pentabarf.cryptmessaging;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.telephony.SmsMessage;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.widget.Toast;

public class CryptSmsReceiver extends BroadcastReceiver {
    private static class ContactInfo {
        private final String name;
        private final String keyAlias;

        public ContactInfo(String name, String keyAlias) {
            this.name = name;
            this.keyAlias = keyAlias;
        }

        public String getName() {
            return name;
        }

        public String getKeyAlias() {
            return keyAlias;
        }
    }

    static final String ACTION = "android.provider.Telephony.SMS_RECEIVED";
    static final String ENCODED_MESSAGE_PREFIX = "@";

    private static final int NOTIFICATION_ID = 123;
    private static final String SMS_EXTRA_NAME = "pdus";
    private static final String TAG = "CryptSmsReceiver";

    /*
     * build notification ticker message for sms based on
     * com.android.mms.transaction.MessagingNotification
     */
    private static CharSequence buildTickerMessage(String displayAddress,
            String subject, String body) {
        StringBuilder buf = new StringBuilder(displayAddress == null ? ""
                : displayAddress.replace('\n', ' ').replace('\r', ' '));

        int offset = buf.length();
        if (!TextUtils.isEmpty(subject) || !TextUtils.isEmpty(body))
            buf.append(':').append(' ');
        if (!TextUtils.isEmpty(subject)) {
            subject = subject.replace('\n', ' ').replace('\r', ' ');
            buf.append(subject);
            buf.append(' ');
        }

        if (!TextUtils.isEmpty(body)) {
            body = body.replace('\n', ' ').replace('\r', ' ');
            buf.append(body);
        }

        SpannableString spanText = new SpannableString(buf.toString());
        spanText.setSpan(new StyleSpan(Typeface.BOLD), 0, offset,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spanText;
    }

    /*
     * format a message for BigText notifications based on
     * com.android.mms.transaction.MessagingNotification
     */
    public static CharSequence formatBigMessage(Context context,
            String mMessage, String mSubject) {
        final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                context, R.style.NotificationPrimaryText);

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        if (!TextUtils.isEmpty(mSubject)) {
            spannableStringBuilder.append(mSubject);
            spannableStringBuilder.setSpan(notificationSubjectSpan, 0,
                    mSubject.length(), 0);
        }
        if (mMessage != null) {
            if (spannableStringBuilder.length() > 0) {
                spannableStringBuilder.append('\n');
            }
            spannableStringBuilder.append(mMessage);
        }
        return spannableStringBuilder;
    }

    /**
     * retrieve contact for phone number, check if contact has key assigned.
     * 
     * @param ctx
     * @param address phone number used to look up contact
     * @return contact's display name if he has a key assigned, null otherwise
     */
    private static ContactInfo lookupContact(Context ctx, String address) {
        // prepare lookup URI
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address));

        // find contact
        Cursor c = ctx.getContentResolver().query(
                uri,
                new String[] {
                        ContactsContract.Contacts.LOOKUP_KEY,
                        ContactsContract.Contacts.DISPLAY_NAME
                }, null, null,
                null);

        String displayName = address;
        String contactId = null;

        try {
            if (c != null && c.moveToNext()) {
                // contact found
                contactId = c.getString(0);
                displayName = c.getString(1);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (contactId == null) {
            Log.w(TAG, "got text from unknown contact");
            return null;
        }
        Log.w(TAG, "sent by " + displayName);

        // retrieve contact's key
        Cursor keyCursor = ctx.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                new String[] {
                    Data.DATA1
                }, // the columns in the result
                Data.MIMETYPE + " = '" + CryptCompose.MIMETYPE + "' AND " + Data.DATA2 + " = '"
                        + CryptCompose.USAGE_TYPE + "' AND " + Data.LOOKUP_KEY + " = ?",
                new String[] {
                    contactId
                }, null);

        if (keyCursor != null && keyCursor.moveToFirst()) {
            Log.w(TAG, "contact " + displayName + " has key=" + keyCursor.getString(0));
            return new ContactInfo(displayName, keyCursor.getString(0));
        }

        Log.w(TAG, "contact " + displayName + " (" + contactId + ") has no key assigned");
        return null;
    }

    /**
     * stores incoming text message in system message storage useful for
     * modifying (eg. decrypting) incoming message text
     * 
     * @param ctx
     * @param msg single parsed message for metadata
     * @param pdus unparsed message fragments
     * @return assembled message body
     */
    private static String restoreMessage(Context ctx, String keyAlias, SmsMessage msg,
            Object[] pdus) {
        ContentValues values = new ContentValues();
        values.put("address", msg.getDisplayOriginatingAddress());
        values.put("date", System.currentTimeMillis());
        values.put("date_sent", msg.getTimestampMillis());
        values.put("read", Integer.valueOf(0));
        if (msg.getPseudoSubject().length() > 0) {
            values.put("subject", msg.getPseudoSubject());
        }
        values.put("reply_path_present", msg.isReplyPathPresent() ? 1 : 0);
        values.put("service_center", msg.getServiceCenterAddress());

        StringBuilder body = new StringBuilder();
        for (Object pdu : pdus) {
            body.append(SmsMessage.createFromPdu((byte[]) pdu)
                    .getDisplayMessageBody());
        }
        // TODO decrypt body

        values.put("body", body.toString());

        ctx.getContentResolver().insert(Uri.parse("content://sms/inbox"),
                values);

        return body.toString();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Intent received: " + intent.getAction());
        Toast.makeText(context, "CipherSMS received", Toast.LENGTH_SHORT).show();
        if (ACTION.equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle == null)
                return;

            // parse incoming message
            Object[] pdus = (Object[]) bundle.get(SMS_EXTRA_NAME);

            // use first message part for metadata
            byte[] mainPdu = (byte[]) pdus[0];
            SmsMessage msg = SmsMessage.createFromPdu(mainPdu);

            String source = msg.getDisplayOriginatingAddress();

            // retrieve contact info / check key assignment
            ContactInfo contact = lookupContact(context, source);

            if (contact != null) {

                // check for encrypted message
                String text = msg.getDisplayMessageBody();
                if (text.startsWith(ENCODED_MESSAGE_PREFIX)
                        && text.endsWith(ENCODED_MESSAGE_PREFIX) && text.length() > 2) {

                    // message is encrypted and the sender is a known crypto
                    // contact, don't handle as normal message
                    abortBroadcast();

                    // assemble, decrypt and store message
                    String body = restoreMessage(context, contact.getKeyAlias(), msg, pdus);

                    // collect notification info
                    String subject = msg.getPseudoSubject();
                    CharSequence formatted = formatBigMessage(context, body,
                            subject);

                    Intent contentIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.fromParts("sms",
                                    msg.getDisplayOriginatingAddress(), null));

                    contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    // based on
                    // com.android.mms.transaction.MessagingNotification
                    // prepare notification
                    Notification.Builder builder = new Notification.Builder(context);
                    builder.setTicker(buildTickerMessage(contact.getName(), subject,
                            msg.getDisplayMessageBody()));
                    builder.setContentTitle(buildTickerMessage(contact.getName(), null,
                            null));
                    builder.setContentIntent(PendingIntent.getActivity(context, 0,
                            contentIntent, 0));
                    builder.setSmallIcon(android.R.drawable.stat_notify_chat);
                    builder.setDefaults(Notification.DEFAULT_ALL);
                    builder.setPriority(Notification.PRIORITY_DEFAULT);
                    builder.setContentText(formatted);
                    builder.setAutoCancel(true);
                    // TODO avatar

                    Notification notification = new Notification.BigTextStyle(
                            builder).bigText(formatted).build();

                    // show notification
                    ((NotificationManager) context
                            .getSystemService(Context.NOTIFICATION_SERVICE))
                            .notify(NOTIFICATION_ID, notification);

                }
            }

        }
    }
}


package de.pentabarf.cryptmessaging;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.security.CryptOracle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.widget.Toast;

import javax.crypto.spec.IvParameterSpec;

public class SmsDecryptorActivity extends Activity {
    private static final int DECRYPT_ACTION = 12;

    private static final int NOTIFICATION_ID = 123;
    protected static final String TAG = "SmsDecryptorActivity";

    /*
     * build notification ticker message for sms based on
     * com.android.mms.transaction.MessagingNotification
     */
    private static CharSequence buildTickerMessage(String displayAddress, String subject,
            String body) {
        StringBuilder buf = new StringBuilder(displayAddress == null ? "" : displayAddress.replace(
                '\n', ' ').replace('\r', ' '));

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
        spanText.setSpan(new StyleSpan(Typeface.BOLD), 0, offset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spanText;
    }

    /*
     * format a message for BigText notifications based on
     * com.android.mms.transaction.MessagingNotification
     */
    private static CharSequence formatBigMessage(Context context, String mMessage, String mSubject) {
        final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(context,
                R.style.NotificationPrimaryText);

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        if (!TextUtils.isEmpty(mSubject)) {
            spannableStringBuilder.append(mSubject);
            spannableStringBuilder.setSpan(notificationSubjectSpan, 0, mSubject.length(), 0);
        }
        if (mMessage != null) {
            if (spannableStringBuilder.length() > 0) {
                spannableStringBuilder.append('\n');
            }
            spannableStringBuilder.append(mMessage);
        }
        return spannableStringBuilder;
    }

    protected CryptSmsReceiver.ContactInfo contact;

    private final AsyncTask<byte[], Void, byte[]> decryptionTask = new AsyncTask<byte[], Void, byte[]>() {
        private ProgressDialog pd;

        @Override
        protected byte[] doInBackground(byte[]... params) {
            byte[] iv = params[0];
            byte[] ciphertext = params[1];
            try {
                IvParameterSpec ivSpec = new IvParameterSpec(iv);
                byte[] plaintext = CryptOracle.decryptData(SmsDecryptorActivity.this,
                        contact.getKeyAlias(), "AES", "CBC/PKCS7PADDING", ciphertext,
                        ivSpec);
                return plaintext;
            } catch (Exception e) {
                Toast.makeText(SmsDecryptorActivity.this, "Can't decrypt sms", Toast.LENGTH_SHORT)
                        .show();
                Log.e(TAG, "error while decrypting data:", e);
                return null;
            }
        };

        @Override
        protected void onPostExecute(byte[] result) {
            pd.dismiss();
            if (result != null) {
                Log.d(TAG, "decrypted Result: " + new String(result));
                restoreMessage(new String(result));
            }
            finish();
        }

        @Override
        protected void onPreExecute() {
            pd = new ProgressDialog(SmsDecryptorActivity.this);
            pd.setMessage("Encrypting...");
            pd.setIndeterminate(true);
            pd.setCancelable(false);
            pd.show();
        };
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DECRYPT_ACTION && resultCode == RESULT_OK) {
            byte[] iv = getIntent().getByteArrayExtra(CryptSmsReceiver.EXTRA_IV);
            byte[] ciphertext = getIntent().getByteArrayExtra(CryptSmsReceiver.EXTRA_CIPHERTEXT);
            decryptionTask.execute(iv, ciphertext);
            return;
        } else {
            Toast.makeText(this, "Can't decrypt sms", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        contact = getIntent().getParcelableExtra(CryptSmsReceiver.EXTRA_CONTACT);

        Intent i = CryptOracle.createCheckAccessIntent(this, contact.getKeyAlias(),
                CryptOracle.UsageType.SECRET);
        startActivityForResult(i, DECRYPT_ACTION);
    }

    protected void restoreMessage(String body) {
        ContentValues values = getIntent().getParcelableExtra(CryptSmsReceiver.EXTRA_STORE);

        values.put("body", body);
        getContentResolver().insert(Uri.parse("content://sms/inbox"), values);

        // collect notification info
        CharSequence formatted = formatBigMessage(this, body, "");

        Intent contentIntent = new Intent(Intent.ACTION_VIEW,
                Uri.fromParts("sms", values.getAsString("address"), null));

        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // based on
        // com.android.mms.transaction.MessagingNotification
        // prepare notification
        Notification.Builder builder = new Notification.Builder(this);
        builder.setTicker(buildTickerMessage(contact.getName(), "", body));
        builder.setContentTitle(buildTickerMessage(contact.getName(), null, null));
        builder.setContentIntent(PendingIntent
                .getActivity(this, 0, contentIntent, 0));
        builder.setSmallIcon(android.R.drawable.stat_notify_chat);
        builder.setDefaults(Notification.DEFAULT_ALL);
        builder.setPriority(Notification.PRIORITY_DEFAULT);
        builder.setContentText(formatted);
        builder.setAutoCancel(true);
        // TODO avatar

        Notification notification = new Notification.BigTextStyle(
                builder).bigText(formatted).build();

        // show notification
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, notification);
    }
}

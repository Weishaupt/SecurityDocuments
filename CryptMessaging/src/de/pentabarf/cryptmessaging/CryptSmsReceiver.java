
package de.pentabarf.cryptmessaging;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

public class CryptSmsReceiver extends BroadcastReceiver {
    static class ContactInfo implements Parcelable {
        private final String keyAlias;
        private final String name;

        public ContactInfo(String name, String keyAlias) {
            this.name = name;
            this.keyAlias = keyAlias;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public String getName() {
            return name;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeString(keyAlias);
        }
        
        public static final Parcelable.Creator<ContactInfo> CREATOR = new Creator<CryptSmsReceiver.ContactInfo>() {
            
            @Override
            public ContactInfo[] newArray(int size) {
                return new ContactInfo[size];
            }
            
            @Override
            public ContactInfo createFromParcel(Parcel source) {
                return new ContactInfo(source.readString(), source.readString());
            }
        };
    }

    static final String ACTION = "android.provider.Telephony.SMS_RECEIVED";
    static final char ENCODED_MESSAGE_PREFIX = '@';
    static final char ENCODED_MESSAGE_SUFFIX = '@';

    private static final String SMS_EXTRA_NAME = "pdus";
    private static final String TAG = "CryptSmsReceiver";
    static final String EXTRA_IV = "iv";
    static final String EXTRA_CIPHERTEXT = "ciphertext";
    static final String EXTRA_CONTACT = "contact";
    static final String EXTRA_STORE = "storevalues";

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
        Cursor c = ctx.getContentResolver().query(uri, new String[] {
                ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME
        }, null, null, null);

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
        Cursor keyCursor = ctx.getContentResolver().query(
                ContactsContract.Data.CONTENT_URI,
                new String[] {
                    Data.DATA1
                },
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
    private static boolean restoreMessage(Context ctx, ContactInfo contact, SmsMessage msg, Object[] pdus) {
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
        Log.i(TAG, "got " + pdus.length + " pdus from " + contact.getName());
        for (Object pdu : pdus) {
            body.append(SmsMessage.createFromPdu((byte[]) pdu).getDisplayMessageBody());
        }
        
        int endIdx = body.length()-1;
        
        if (body.charAt(0) != ENCODED_MESSAGE_PREFIX || body.charAt(endIdx) != ENCODED_MESSAGE_SUFFIX)
            return false;
        
        byte[] ivCiphertext = Base64.decode(body.substring(1, endIdx), Base64.NO_WRAP);
        if (ivCiphertext.length < CryptCompose.IV_SIZE)
            return false;
        
        byte[] ciphertext = new byte[ivCiphertext.length - CryptCompose.IV_SIZE];
        byte[] iv = new byte[CryptCompose.IV_SIZE];
        System.arraycopy(ivCiphertext, 0, iv, 0, CryptCompose.IV_SIZE);
        System.arraycopy(ivCiphertext, CryptCompose.IV_SIZE, ciphertext, 0, ivCiphertext.length - CryptCompose.IV_SIZE);
        
        Intent i = new Intent(ctx, SmsDecryptorActivity.class);
        i.putExtra(EXTRA_IV, iv);
        i.putExtra(EXTRA_CIPHERTEXT, ciphertext);
        i.putExtra(EXTRA_CONTACT, contact);
        i.putExtra(EXTRA_STORE, values);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        
        ctx.startActivity(i);
        
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Intent received: " + intent.getAction());
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
                if (restoreMessage(context, contact, msg, pdus)) {
                    Toast.makeText(context, "CipherSMS received", Toast.LENGTH_SHORT).show();

                    // message is encrypted and the sender is a known crypto
                    // contact, don't handle as normal message
                    abortBroadcast();
                }
            }

        }
    }
}

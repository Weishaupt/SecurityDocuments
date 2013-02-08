
package de.pentabarf.cryptmessaging;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Telephony;
import android.security.CryptOracle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.util.ArrayList;

import javax.crypto.spec.IvParameterSpec;

@SuppressWarnings("static-access")
public class CryptCompose extends Activity {

    static final int IV_SIZE = 16;
    private static final int ENCRYPT_ACTION = 2;
    static final String MESSAGE_SENT_ACTION = "de.pentabarf.cryptmessaging.MESSAGE_SENT";

    static final String MESSAGE_STATUS_RECEIVED_ACTION = "de.pentabarf.cryptmessaging.MESSAGE_STATUS_RECEIVED";
    public static final String MIMETYPE = "vnd.android.cursor.item/key";

    private static final String TAG = "CryptCompose";

    public static final String USAGE_TYPE = "sms-symmetric";

    protected CharSequence[] aliasItems = null;
    protected String contactNumber = null;
    protected String contactAlias = null;

    protected CharSequence encryptedMessage;
    protected CharSequence message;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENCRYPT_ACTION) {
            new AsyncTask<String, Void, byte[]>() {

                private String msg;
                private ProgressDialog pd;

                @Override
                protected byte[] doInBackground(String... params) {
                    msg = params[0];
                    Log.d(TAG, msg);
                    try {
                        SecureRandom rng = SecureRandom.getInstance("SHA1PRNG");
                        byte[] iv = new byte[IV_SIZE];
                        rng.nextBytes(iv);
                        IvParameterSpec ivSpec = new IvParameterSpec(iv);

                        byte[] ciphertext = CryptOracle.encryptData(CryptCompose.this,
                                contactAlias, "AES", "CBC/PKCS7PADDING", params[0].getBytes(),
                                ivSpec);
                        byte[] ivCiphertext = new byte[ciphertext.length + IV_SIZE];
                        System.arraycopy(iv, 0, ivCiphertext, 0, IV_SIZE);
                        System.arraycopy(ciphertext, 0, ivCiphertext, IV_SIZE, ciphertext.length);
                        return ivCiphertext;
                    } catch (Exception e) {
                        Log.e(TAG, "error while encrypting data:", e);
                        return null;
                    }
                };

                @Override
                protected void onPostExecute(byte[] result) {
                    Log.d(TAG, "Crypted Result: " + new String(result));
                    String crypt = CryptSmsReceiver.ENCODED_MESSAGE_PREFIX +
                            Base64.encodeToString(result, Base64.NO_WRAP) +
                            CryptSmsReceiver.ENCODED_MESSAGE_SUFFIX;
                    pd.dismiss();
                    CryptCompose.this.sendEncryptedMessage(crypt, msg);
                }

                @Override
                protected void onPreExecute() {
                    pd = new ProgressDialog(CryptCompose.this);
                    pd.setMessage("Encrypting...");
                    pd.setIndeterminate(true);
                    pd.setCancelable(false);
                    pd.show();
                };
            }.execute(message.toString());
            return;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.compose);

        final RecipientsAdapter adapter = new RecipientsAdapter(this);

        AutoCompleteTextView tv = (AutoCompleteTextView) findViewById(R.id.contacts);
        // clear selected number on text change (we only want autocompleted
        // contacts)
        tv.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                contactNumber = null;
                contactAlias = null;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
            }
        });

        // set recipient number to autocompleted contact
        tv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                contactNumber = adapter.getContactNumber(arg2);
                contactAlias = adapter.getContactAlias(arg2);
            }
        });

        tv.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.send_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_send:
                if (contactNumber != null)
                    sendMessage();
                else
                    // no autocompleted contact selected
                    Toast.makeText(this, R.string.pick_recipient_toast,
                            Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void sendEncryptedMessage(String encMessage, String normMessage) {

        Log.d(TAG, "Encrypted Message:\n" + encMessage);

        SmsManager sms = SmsManager.getDefault();
        // store message in queue storage (unencrypted)
        Uri uri = Telephony.Sms
                .addMessageToUri(getContentResolver(),
                        Uri.parse("content://sms/queued"),
                        contactNumber /* recipient */,
                        normMessage /* message */,
                        null /* subjcet */,
                        System.currentTimeMillis() /* date */, true /* read */,
                        true /* deliveryReport */);

        // prepare for message sending
        ArrayList<String> message = sms.divideMessage(encMessage);
        int messageCount = message.size();
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>(
                messageCount);
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>(
                messageCount);

        for (int i = 0; i < messageCount; i++) {
            // add a pending intent for delivery notification only for the last
            // chunk
            if (i == messageCount - 1)
                deliveryIntents.add(PendingIntent.getBroadcast(this, 0,
                        new Intent(MESSAGE_STATUS_RECEIVED_ACTION, uri, this,
                                SmsStatusReceiver.class), 0));
            else
                deliveryIntents.add(null);

            // add pending intent for sending status notification
            sentIntents.add(PendingIntent
                    .getBroadcast(this, 0, new Intent(MESSAGE_SENT_ACTION, uri,
                            this, SmsStatusReceiver.class), 0));
            Log.d(TAG, "sms part " + i + "=" + message.get(i));
        }

        // move message to outgoing folder
        Telephony.Sms.moveMessageToFolder(this, uri,
                Telephony.Sms.MESSAGE_TYPE_OUTBOX, 0);
        // actual send message
        sms.sendMultipartTextMessage(contactNumber, null, message, sentIntents,
                deliveryIntents);
        finish();
    }

    private void sendMessage() {
        TextView textView = ((TextView) findViewById(R.id.text));

        CharSequence body = textView.getText();

        if (body.toString().trim() == "")
            return;

        message = body;

        Intent i = CryptOracle.createCheckAccessIntent(CryptCompose.this, contactAlias,
                CryptOracle.UsageType.SECRET);
        startActivityForResult(i, ENCRYPT_ACTION);
    }

}

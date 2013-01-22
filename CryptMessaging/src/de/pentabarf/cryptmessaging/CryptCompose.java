package de.pentabarf.cryptmessaging;

import java.security.SecureRandom;
import java.util.ArrayList;

import javax.crypto.spec.IvParameterSpec;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.Telephony;
import android.security.CryptOracle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("static-access")
public class CryptCompose extends Activity {

    static final String MESSAGE_SENT_ACTION = "de.pentabarf.cryptmessaging.MESSAGE_SENT";
    static final String MESSAGE_STATUS_RECEIVED_ACTION = "de.pentabarf.cryptmessaging.MESSAGE_STATUS_RECEIVED";

    private static final String TAG = "CryptCompose";

    protected String contactNumber = null;
    protected String contactId = null;
    protected CharSequence[] aliasItems = null;
    protected int selectedItem = -1;
    
	protected String selectedAlias;
	protected CharSequence message;
	protected CharSequence encryptedMessage;
	
	private static final int ENCRYPT_ACTION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);

	setContentView(R.layout.compose);

	final RecipientsAdapter adapter = new RecipientsAdapter(this);

	AutoCompleteTextView tv = (AutoCompleteTextView) findViewById(R.id.contacts);
	// clear selected number on text change (we only want autocompleted contacts)
	// XXX this UI would be better if we had a picker that would only allow us to select contacts with assigned keys and phone numbers
	tv.addTextChangedListener(new TextWatcher() {

	    @Override
	    public void afterTextChanged(Editable s) {
	    }

	    @Override
	    public void beforeTextChanged(CharSequence s, int start, int count,
		    int after) {
		CryptCompose.this.contactNumber = null;
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
		CryptCompose.this.contactNumber = adapter.getContactNumber(arg2);
		CryptCompose.this.contactId = adapter.getContactId(arg2);
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
		    if (this.contactNumber != null)
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
    
    private void sendMessage() {	
		TextView textView = ((TextView) findViewById(R.id.text));
	
		CharSequence body = textView.getText();
		
		if (body.toString().trim() == "")
			return;
		
		this.message = body;
		
		//Prepare the Alias selection dialog
		this.aliasItems = this.getContactAliases();
		this.selectedItem = -1;
		
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Provide Key alias");
        alert.setSingleChoiceItems(this.aliasItems, this.selectedItem, new DialogInterface.OnClickListener() {
        	@Override
        	public void onClick(DialogInterface dialog, int item) {
        		Log.d(TAG, "Selected index:" + item);
        		CryptCompose.this.selectedItem = item;
        	}
        });  
        
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            	if (CryptCompose.this.selectedItem < 0)
                	return;            	
            	
                CryptCompose.this.selectedAlias = CryptCompose.this.aliasItems[CryptCompose.this.selectedItem].toString(); 
                
                Log.d(TAG, "alias:" + CryptCompose.this.selectedAlias);
                
                if (CryptCompose.this.selectedAlias == null)
                	return;
                
                Intent i = CryptOracle.createCheckAccessIntent(CryptCompose.this, CryptCompose.this.selectedAlias, CryptOracle.UsageType.SECRET);
                startActivityForResult(i, ENCRYPT_ACTION);
            }
        });
        alert.setNeutralButton("Set other alias", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				AlertDialog.Builder alert = new AlertDialog.Builder(CryptCompose.this);
				alert.setTitle("Provide Key alias");
				alert.setMessage("Please provide the key alias with which the message should be encrypted.");
				//Set an EditText view to get user input
		        final EditText input = new EditText(CryptCompose.this);
		        alert.setView(input);
		        
		        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		            public void onClick(DialogInterface dialog, int whichButton) {
		                String response = ((TextView) input).getText().toString();
		                CryptCompose.this.selectedAlias = response;
		                
		                if (response == null)
		                	return;
		                
		                Intent i = CryptOracle.createCheckAccessIntent(CryptCompose.this, response, CryptOracle.UsageType.SECRET);
		                startActivityForResult(i, ENCRYPT_ACTION);
		            }
		        });
		        alert.setNegativeButton("Cancel",
		                new DialogInterface.OnClickListener() {
		                    public void onClick(DialogInterface dialog, int whichButton) {
		                    	CryptCompose.this.selectedAlias = null;
		                    }
		                });
		        
		        AlertDialog ad = alert.create();
		        ad.show();
			}
		});
        alert.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    	CryptCompose.this.selectedAlias = null;
                    }
                });
        
        AlertDialog ad = alert.create();
        ad.show();
    }
    
    protected CharSequence[] getContactAliases() {
    	ArrayList<String> list = new ArrayList<String>(3);
    	
    	// Iterate through all aliases of a contact
        Cursor c = getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                new String[] {
                        ContactsContract.Data.DATA1, ContactsContract.Data.DATA2
                },
                ContactsContract.Data.MIMETYPE + " = 'vnd.android.cursor.item/key' AND " + ContactsContract.Data.CONTACT_ID + " = ?",
                new String[] {
                        CryptCompose.this.contactId // _ID aus RawContacts die auf den Kontakt zutrifft
                },
                ContactsContract.Data.DATA1 + " ASC");
                
        try {
        	while (c.moveToNext()) {
            	String alias = c.getString(c.getColumnIndex(ContactsContract.Data.DATA1));
            	String type = c.getString(c.getColumnIndex(ContactsContract.Data.DATA2));
            	Log.d(TAG, "alias: " + alias);
            	Log.d(TAG, "type:" + type);
            	list.add(alias);
        	}
        }
    	finally {
        		c.close();
    	}
        
        return list.toArray(new CharSequence[list.size()]);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENCRYPT_ACTION) {
        	new AsyncTask<String, Void, byte[]>() {

        		private ProgressDialog pd;
        		private String msg;

                protected void onPreExecute() {
                    this.pd = new ProgressDialog(CryptCompose.this);
                    this.pd.setMessage("Encrypting...");
                    this.pd.setIndeterminate(true);
                    this.pd.setCancelable(false);
                    this.pd.show();
                };

                @Override
                protected byte[] doInBackground(String... params) {
                    this.msg = params[0];
                    Log.d(TAG, this.msg);
                	try {
                		SecureRandom rng = SecureRandom.getInstance("SHA1PRNG");
                        byte[] iv = new byte[16];
                        rng.nextBytes(iv);
                        IvParameterSpec ivSpec = new IvParameterSpec(iv); 
                        
                        return CryptOracle.encryptData(CryptCompose.this,
                                CryptCompose.this.selectedAlias, "AES",
                                "CBC/PKCS7PADDING", params[0].getBytes(), ivSpec);
                    } catch (Exception e) {
                        Log.e(TAG, "error while encrypting data:", e);
                        return null;
                    }
                }

                protected void onPostExecute(byte[] result) {
                    Log.d(TAG, "Crypted Result: " + new String(result));
                    String crypt = CryptSmsReceiver.ENCODED_MESSAGE_PREFIX + 
                    		Base64.encodeToString(result, Base64.NO_WRAP) + 
                    		CryptSmsReceiver.ENCODED_MESSAGE_PREFIX;
                    this.pd.dismiss();
    		    	CryptCompose.this.sendEncryptedMessage(crypt, this.msg);
                };
        	}.execute(this.message.toString());
            return;
        }
    }
    
    
	void sendEncryptedMessage(String encMessage, String normMessage) {
		
		Log.d(TAG, "Encrypted Message:\n" + encMessage);
		
		SmsManager sms = SmsManager.getDefault();
		// store message in queue storage (unencrypted)
		Uri uri = Telephony.Sms
			.addMessageToUri(getContentResolver(),
				Uri.parse("content://sms/queued"),
				this.contactNumber /* recipient */, 
				encMessage /* message */,
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
		    // add a pending intent for delivery notification only for the last chunk 
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
		sms.sendMultipartTextMessage(this.contactNumber, null, message, sentIntents,
			deliveryIntents);
    }
   
}

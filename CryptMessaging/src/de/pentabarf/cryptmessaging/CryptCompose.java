package de.pentabarf.cryptmessaging;

import java.util.ArrayList;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("static-access")
public class CryptCompose extends Activity {

    static final String MESSAGE_SENT_ACTION = "de.pentabarf.cryptmessaging.MESSAGE_SENT";
    static final String MESSAGE_STATUS_RECEIVED_ACTION = "de.pentabarf.cryptmessaging.MESSAGE_STATUS_RECEIVED";

    private static final String TAG = "CryptCompose";

    protected String number = null;

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
		CryptCompose.this.number = null;
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
		CryptCompose.this.number = adapter.getContactNumber(arg2);
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
	    if (this.number != null)
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
	SmsManager sms = SmsManager.getDefault();

	TextView textView = ((TextView) findViewById(R.id.text));

	CharSequence body = textView.getText();

	// TODO encrypt

	// store message in queue storage
	Uri uri = Telephony.Sms
		.addMessageToUri(getContentResolver(),
			Uri.parse("content://sms/queued"),
			this.number /* recipient */, body.toString(),
			null /* subjcet */,
			System.currentTimeMillis() /* date */, true /* read */,
			true /* deliveryReport */);

	// prepare for message sending
	ArrayList<String> message = sms.divideMessage(body.toString());
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
	sms.sendMultipartTextMessage(this.number, null, message, sentIntents,
		deliveryIntents);
    }

}

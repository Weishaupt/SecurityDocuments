package de.pentabarf.cryptoracletest;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.MessageFormat;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.security.CryptOracle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("unused")
public class MainActivity extends Activity {
    protected static final String TAG = MainActivity.class.getSimpleName();

    protected String selectedAlias;
    protected byte[] data;

    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.activity_main);

	Log.i(TAG, "available providers: ");
	for (Provider p : Security.getProviders()) {
	    Log.i(TAG, p.toString());
	    for (Provider.Service s : p.getServices())
		Log.i(TAG, "  service: " + s.getType() + "/" + s.getAlgorithm());
	}

	this.selectedAlias = null;
	this.data = null;
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//	// Inflate the menu; this adds items to the action bar if it is present.
//	getMenuInflater().inflate(R.menu.activity_main, menu);
//	return true;
//    }

    public void importClicked(View view) {
	Intent i = KeyChain.createInstallIntent();
	startActivity(i);
    }

    public void testClicked(View view) {

	KeyChain.choosePrivateKeyAlias(this, new KeyChainAliasCallback() {

	    @Override
	    public void alias(final String keyAlias) {
		runOnUiThread(new Runnable() {

		    @Override
		    public void run() {
			new AsyncTask<Void, String, Integer>() {
			    ProgressDialog pd;

			    private String ptxt = "foobarbaz";

			    protected void onPreExecute() {

				this.pd = new ProgressDialog(MainActivity.this);
				this.pd.setMessage("Loading...");
				this.pd.setIndeterminate(true);
				this.pd.setCancelable(false);
				this.pd.show();
			    };

			    @Override
			    protected Integer doInBackground(Void... params) {
				int status = 0;
				try {

				    PrivateKey prk = KeyChain.getPrivateKey(
					    MainActivity.this, keyAlias);
				    PublicKey puk = KeyChain
					    .getCertificateChain(
						    MainActivity.this, keyAlias)[0]
					    .getPublicKey();
				    
				    Log.i(TAG, "puk=" + puk.toString());
				    Log.i(TAG, "prk=" + prk.toString());

				    Signature sig = Signature.getInstance(
					    "SHA1withRSA", "AndroidOpenSSL");
				    publishProgress("signing...");
				    sig.initSign(prk);
				    sig.update(this.ptxt.getBytes());
				    byte[] sigData = sig.sign();

				    publishProgress("verifying...");
				    sig = Signature.getInstance(
					    "SHA1withRSA", "AndroidOpenSSL");
				    sig.initVerify(puk);
				    sig.update(this.ptxt.getBytes());
				    if (!sig.verify(sigData))
					return 1;
				    status = 2;

				    publishProgress("encrypting...");
				    byte[] ctxt = doCrypt(Cipher.ENCRYPT_MODE,
					    puk, "ECB/PKCS1Padding",
					    this.ptxt.getBytes());
				    publishProgress("decrypting...");
				    byte[] ptxtb = doCrypt(Cipher.DECRYPT_MODE,
					    prk, "ECB/PKCS1Padding", ctxt);
				    Log.e(TAG, "result: " + new String(ptxtb));
				    return this.ptxt.equals(new String(ptxtb)) ? 4
					    : 3;
				} catch (InterruptedException e) {
				    Log.e(TAG, "error!", e);
				} catch (KeyChainException e) {
				    Log.e(TAG, "error!", e);
				} catch (GeneralSecurityException e) {
				    Log.e(TAG, "error!", e);
				} catch (RuntimeException e) {
				    Log.e(TAG, "error!", e);
				}

				return status;
			    }

			    protected void onProgressUpdate(
				    final String... progress) {

				Log.e(TAG, "progress: " + progress[0]);
				runOnUiThread(new Runnable() {

				    @SuppressWarnings("unqualified-field-access")
				    @Override
				    public void run() {
					pd.setMessage(progress[0]);
				    }
				});
			    }

			    protected void onPostExecute(Integer result) {
				this.pd.dismiss();

				// 1. Instantiate an AlertDialog.Builder with
				// its constructor
				AlertDialog.Builder builder = new AlertDialog.Builder(
					MainActivity.this);

				// 2. Chain together various setter methods to
				// set the dialog
				// characteristics
				String message = "unknown status";

				switch (result) {
				case 0:
				    message = "Error";
				    break;
				case 1:
				    message = "bad signature";
				    break;
				case 2:
				    message = "good signature, error in decryption";
				    break;
				case 3:
				    message = "good signature, bad decryption";
				    break;
				case 4:
				    message = "all good";
				    break;
				}

				builder.setMessage(message).setTitle("Result");

				// 3. Get the AlertDialog from create()
				AlertDialog dialog = builder.create();
				dialog.show();
			    };
			}.execute();
		    }
		});
	    }
	}, null, null, null, 0, null);
    }

    protected static byte[] doCrypt(int mode, Key key, String padding,
	    byte[] data) throws NoSuchPaddingException,
	    IllegalBlockSizeException, BadPaddingException {

	Log.w(TAG, MessageFormat.format(
		"doCrypt({0}, keyformat={1}, padding={2}, datalen={3})", mode,
		key.getAlgorithm(), padding, data.length));
	String algorithm = key.getAlgorithm();

	if (algorithm == null)
	    throw new IllegalArgumentException(
		    "key unusable - unknown algorithm");
	if (padding != null)
	    algorithm += "/" + padding;

	try {
	    Log.e(TAG, "loading cipher");
	    Cipher c = Cipher.getInstance(algorithm, "AndroidOpenSSL");

	    Log.e(TAG, "init cipher");
	    c.init(mode, key);

	    Log.e(TAG, "running cipher, block size=" + c.getBlockSize());
	    byte[] processedData = c.doFinal(data);
	    Log.e(TAG, "returning result");
	    return processedData;
	} catch (NoSuchAlgorithmException e) {
	    throw new IllegalArgumentException("key unusable - algorithm \""
		    + key.getAlgorithm() + "\" not supported");
	} catch (InvalidKeyException e) {
	    throw new IllegalArgumentException("key unusable - algorithm \""
		    + key.getAlgorithm() + "\" not supported");
	} catch (NoSuchProviderException e) {
	    throw new IllegalArgumentException("android openssl provider not found");
	}
    }

    public void generateClicked(View view) {
	CryptOracle.generate(this, new KeyChainAliasCallback() {

	    @Override
	    public void alias(String alias) {
		MainActivity.this.selectedAlias = alias;
	    }
	});
    }

    public void selectClicked(View view) {
	KeyChain.choosePrivateKeyAlias(this, new KeyChainAliasCallback() {
	    @Override
	    public void alias(String alias) {
		MainActivity.this.selectedAlias = alias;
	    }
	}, null, null, null, 0, null);
    }

    public void encryptClicked(View view) {
	if (this.selectedAlias == null)
	    return;

	prompt("encrypt", "provide data:", new ResponseHandler<String>() {
	    @Override
	    public void handle(String response) {

		new AsyncTask<String, Void, byte[]>() {

		    private ProgressDialog pd;

		    protected void onPreExecute() {
			this.pd = new ProgressDialog(MainActivity.this);
			this.pd.setMessage("Encrypting...");
			this.pd.setIndeterminate(true);
			this.pd.setCancelable(false);
			this.pd.show();
		    };

		    @Override
		    protected byte[] doInBackground(String... params) {
			try {
			    return CryptOracle.encryptData(MainActivity.this,
				    MainActivity.this.selectedAlias,
				    "ECB/PKCS1Padding", params[0].getBytes());
			} catch (Exception e) {
			    Log.e(TAG, "error while encrypting data:", e);
			    return null;
			}
		    }

		    protected void onPostExecute(byte[] result) {
			this.pd.dismiss();
			MainActivity.this.data = result;
		    };
		}.execute(response);

	    }
	});
    }

    public void decryptClicked(View view) {
	if (this.selectedAlias == null || this.data == null)
	    return;

	new AsyncTask<Void, Void, byte[]>() {
	    private ProgressDialog pd;

	    protected void onPreExecute() {
		this.pd = new ProgressDialog(MainActivity.this);
		this.pd.setMessage("Decrypting...");
		this.pd.setIndeterminate(true);
		this.pd.setCancelable(false);
		this.pd.show();
	    };

	    @Override
	    protected byte[] doInBackground(Void... params) {
		try {
		    return CryptOracle.decryptData(MainActivity.this,
			    MainActivity.this.selectedAlias,
			    "ECB/PKCS1Padding", MainActivity.this.data);
		} catch (Exception e) {
		    Log.e(TAG, "error while decrypting data:", e);
		    return null;
		}
	    }

	    protected void onPostExecute(byte[] decrypted) {
		this.pd.dismiss();
		Toast.makeText(MainActivity.this, new String(decrypted),
			Toast.LENGTH_LONG).show();
	    };
	}.execute();
    }

    private void prompt(String title, String message,
	    final ResponseHandler<String> rh) {
	AlertDialog.Builder alert = new AlertDialog.Builder(this);

	alert.setTitle(title);
	alert.setMessage(message);

	// Set an EditText view to get user input
	final EditText input = new EditText(this);
	alert.setView(input);

	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
	    public void onClick(DialogInterface dialog, int whichButton) {
		String response = ((TextView) input).getText().toString();
		rh.handle(response);
	    }
	});

	alert.setNegativeButton("Cancel",
		new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int whichButton) {
		    }
		});

	alert.show();
    }

    private interface ResponseHandler<T> {
	public void handle(T response);
    }

    static String toHexString(byte[] bytes, String separator) {
	StringBuilder hexString = new StringBuilder();
	for (byte b : bytes) {
	    hexString.append(Integer.toHexString(0xFF & b)).append(separator);
	}
	return hexString.toString();
    }

}

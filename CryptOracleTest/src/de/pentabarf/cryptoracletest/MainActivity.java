
package de.pentabarf.cryptoracletest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
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
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.security.auth.x500.X500Principal;

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

@SuppressWarnings({
    "unused"
})
public class MainActivity extends Activity {
    private static final String testcert = "-----BEGIN CERTIFICATE-----\n"
            + "MIIHWTCCBUGgAwIBAgIDCkGKMA0GCSqGSIb3DQEBCwUAMHkxEDAOBgNVBAoTB1Jv\n"
            + "b3QgQ0ExHjAcBgNVBAsTFWh0dHA6Ly93d3cuY2FjZXJ0Lm9yZzEiMCAGA1UEAxMZ\n"
            + "Q0EgQ2VydCBTaWduaW5nIEF1dGhvcml0eTEhMB8GCSqGSIb3DQEJARYSc3VwcG9y\n"
            + "dEBjYWNlcnQub3JnMB4XDTExMDUyMzE3NDgwMloXDTIxMDUyMDE3NDgwMlowVDEU\n"
            + "MBIGA1UEChMLQ0FjZXJ0IEluYy4xHjAcBgNVBAsTFWh0dHA6Ly93d3cuQ0FjZXJ0\n"
            + "Lm9yZzEcMBoGA1UEAxMTQ0FjZXJ0IENsYXNzIDMgUm9vdDCCAiIwDQYJKoZIhvcN\n"
            + "AQEBBQADggIPADCCAgoCggIBAKtJNRFIfNImflOUz0Op3SjXQiqL84d4GVh8D57a\n"
            + "iX3h++tykA10oZZkq5+gJJlz2uJVdscXe/UErEa4w75/ZI0QbCTzYZzA8pD6Ueb1\n"
            + "aQFjww9W4kpCz+JEjCUoqMV5CX1GuYrz6fM0KQhF5Byfy5QEHIGoFLOYZcRD7E6C\n"
            + "jQnRvapbjZLQ7N6QxX8KwuPr5jFaXnQ+lzNZ6MMDPWAzv/fRb0fEze5ig1JuLgia\n"
            + "pNkVGJGmhZJHsK5I6223IeyFGmhyNav/8BBdwPSUp2rVO5J+TJAFfpPBLIukjmJ0\n"
            + "FXFuC3ED6q8VOJrU0gVyb4z5K+taciX5OUbjchs+BMNkJyIQKopPWKcDrb60LhPt\n"
            + "XapI19V91Cp7XPpGBFDkzA5CW4zt2/LP/JaT4NsRNlRiNDiPDGCbO5dWOK3z0luL\n"
            + "oFvqTpa4fNfVoIZwQNORKbeiPK31jLvPGpKK5DR7wNhsX+kKwsOnIJpa3yxdUly6\n"
            + "R9Wb7yQocDggL9V/KcCyQQNokszgnMyXS0XvOhAKq3A6mJVwrTWx6oUrpByAITGp\n"
            + "rmB6gCZIALgBwJNjVSKRPFbnr9s6JfOPMVTqJouBWfmh0VMRxXudA/Z0EeBtsSw/\n"
            + "LIaRmXGapneLNGDRFLQsrJ2vjBDTn8Rq+G8T/HNZ92ZCdB6K4/jc0m+YnMtHmJVA\n"
            + "BfvpAgMBAAGjggINMIICCTAdBgNVHQ4EFgQUdahxYEyIE/B42Yl3tW3Fid+8sXow\n"
            + "gaMGA1UdIwSBmzCBmIAUFrUyG9TH8+DmjvO90rA67rI5GNGhfaR7MHkxEDAOBgNV\n"
            + "BAoTB1Jvb3QgQ0ExHjAcBgNVBAsTFWh0dHA6Ly93d3cuY2FjZXJ0Lm9yZzEiMCAG\n"
            + "A1UEAxMZQ0EgQ2VydCBTaWduaW5nIEF1dGhvcml0eTEhMB8GCSqGSIb3DQEJARYS\n"
            + "c3VwcG9ydEBjYWNlcnQub3JnggEAMA8GA1UdEwEB/wQFMAMBAf8wXQYIKwYBBQUH\n"
            + "AQEEUTBPMCMGCCsGAQUFBzABhhdodHRwOi8vb2NzcC5DQWNlcnQub3JnLzAoBggr\n"
            + "BgEFBQcwAoYcaHR0cDovL3d3dy5DQWNlcnQub3JnL2NhLmNydDBKBgNVHSAEQzBB\n"
            + "MD8GCCsGAQQBgZBKMDMwMQYIKwYBBQUHAgEWJWh0dHA6Ly93d3cuQ0FjZXJ0Lm9y\n"
            + "Zy9pbmRleC5waHA/aWQ9MTAwNAYJYIZIAYb4QgEIBCcWJWh0dHA6Ly93d3cuQ0Fj\n"
            + "ZXJ0Lm9yZy9pbmRleC5waHA/aWQ9MTAwUAYJYIZIAYb4QgENBEMWQVRvIGdldCB5\n"
            + "b3VyIG93biBjZXJ0aWZpY2F0ZSBmb3IgRlJFRSwgZ28gdG8gaHR0cDovL3d3dy5D\n"
            + "QWNlcnQub3JnMA0GCSqGSIb3DQEBCwUAA4ICAQApKIWuRKm5r6R5E/CooyuXYPNc\n"
            + "7uMvwfbiZqARrjY3OnYVBFPqQvX56sAV2KaC2eRhrnILKVyQQ+hBsuF32wITRHhH\n"
            + "Va9Y/MyY9kW50SD42CEH/m2qc9SzxgfpCYXMO/K2viwcJdVxjDm1Luq+GIG6sJO4\n"
            + "D+Pm1yaMMVpyA4RS5qb1MyJFCsgLDYq4Nm+QCaGrvdfVTi5xotSu+qdUK+s1jVq3\n"
            + "VIgv7nSf7UgWyg1I0JTTrKSi9iTfkuO960NAkW4cGI5WtIIS86mTn9S8nK2cde5a\n"
            + "lxuV53QtHA+wLJef+6kzOXrnAzqSjiL2jA3k2X4Ndhj3AfnvlpaiVXPAPHG0HRpW\n"
            + "Q7fDCo1y/OIQCQtBzoyUoPkD/XFzS4pXM+WOdH4VAQDmzEoc53+VGS3FpQyLu7Xt\n"
            + "hbNc09+4ufLKxw0BFKxwWMWMjTPUnWajGlCVI/xI4AZDEtnNp4Y5LzZyo4AQ5OHz\n"
            + "0ctbGsDkgJp8E3MGT9ujayQKurMcvEp4u+XjdTilSKeiHq921F73OIZWWonO1sOn\n"
            + "ebJSoMbxhbQljPI/lrMQ2Y1sVzufb4Y6GIIiNsiwkTjbKqGTqoQ/9SdlrnPVyNXT\n"
            + "d+pLncdBu8fA46A/5H2kjXPmEkvfoXNzczqA6NXLji/L6hOn1kGLrPo8idck9U60\n"
            + "4GGSt/M3mMS+lqO3ig==\n" + "-----END CERTIFICATE-----\n";

    protected static final String TAG = MainActivity.class.getSimpleName();

    private static final int ENCRYPT_ACTION = 2;

    private static final int SIGN_ACTION = 3;

    private static final int VERIFY_ACTION = 4;

    private static final int DECRYPT_ACTION = 5;

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

    public void importClicked(View view) {
        Intent i = KeyChain.createInstallIntent();
        startActivity(i);
    }

    public static X509Certificate convertFromPem(String data) {
        try {
            CertificateFactory certFactory = CertificateFactory
                    .getInstance("X.509");
            Certificate cert = certFactory
                    .generateCertificate(new ByteArrayInputStream(data
                            .getBytes()));
            return (X509Certificate) cert;
        } catch (CertificateException e) {
            throw new AssertionError(e);
        }
    }

    public void accessPublicClicked(View view) {
        new AsyncTask<Void, String, X509Certificate>() {
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
            protected X509Certificate doInBackground(Void... params) {
                try {
                    X509Certificate cert = convertFromPem(testcert);

                    Log.i(TAG, "imported cert=" + cert.toString());

                    CryptOracle.storePublicCertificate(MainActivity.this,
                            "testimport", cert);

                    return KeyChain.getCertificateChain(MainActivity.this,
                            "testimport")[0];
                } catch (Throwable e) {
                    Log.e(TAG, "exception while testing import/export", e);
                    e.printStackTrace();
                    return null;
                }
            }

            protected void onProgressUpdate(final String... progress) {

                Log.e(TAG, "progress: " + progress[0]);
                runOnUiThread(new Runnable() {

                    @SuppressWarnings("unqualified-field-access")
                    @Override
                    public void run() {
                        pd.setMessage(progress[0]);
                    }
                });
            }

            protected void onPostExecute(X509Certificate result) {
                this.pd.dismiss();

                if (result == null)
                    return;

                AlertDialog.Builder builder = new AlertDialog.Builder(
                        MainActivity.this);

                builder.setMessage("exported cert=" + result.toString())
                        .setTitle("Result");

                AlertDialog dialog = builder.create();
                dialog.show();
            };
        }.execute();
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
                                    sig = Signature.getInstance("SHA1withRSA",
                                            "AndroidOpenSSL");
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
            throw new IllegalArgumentException(
                    "android openssl provider not found");
        }
    }

    public void selectClicked(View view) {
        prompt("alias", "select alias:", new ResponseHandler<String>() {
            @Override
            public void handle(String response) {   
                MainActivity.this.selectedAlias = response;
            }
        });
    }

    public void encryptClicked(View view) {
        if (this.selectedAlias == null)
            return;
        
        Intent i = CryptOracle.createCheckAccessIntent(this, this.selectedAlias, CryptOracle.UsageType.PUBLIC_ENCRYPT);
        startActivityForResult(i, ENCRYPT_ACTION);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENCRYPT_ACTION) {
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
                                        MainActivity.this.selectedAlias, "RSA",
                                        "ECB/PKCS1Padding", params[0].getBytes(), null);
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
            return;
        }
        if (requestCode == SIGN_ACTION) {
            prompt("sign", "provide data:", new ResponseHandler<String>() {
                @Override
                public void handle(String response) {

                    new AsyncTask<String, Void, byte[]>() {

                        private ProgressDialog pd;

                        protected void onPreExecute() {
                            this.pd = new ProgressDialog(MainActivity.this);
                            this.pd.setMessage("Signing...");
                            this.pd.setIndeterminate(true);
                            this.pd.setCancelable(false);
                            this.pd.show();
                        };

                        @Override
                        protected byte[] doInBackground(String... params) {
                            try {
                                return CryptOracle.sign(MainActivity.this,
                                        MainActivity.this.selectedAlias,
                                        "SHA1", params[0].getBytes());
                            } catch (Exception e) {
                                Log.e(TAG, "error while signing data:", e);
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
            return;
        }
        if (requestCode == VERIFY_ACTION) {
            prompt("verify", "provide data:", new ResponseHandler<String>() {
                @Override
                public void handle(String response) {

                    new AsyncTask<String, Void, Boolean>() {

                        private ProgressDialog pd;

                        protected void onPreExecute() {
                            this.pd = new ProgressDialog(MainActivity.this);
                            this.pd.setMessage("Verifying...");
                            this.pd.setIndeterminate(true);
                            this.pd.setCancelable(false);
                            this.pd.show();
                        };

                        @Override
                        protected Boolean doInBackground(String... params) {
                            try {
                                return CryptOracle.verify(MainActivity.this,
                                        MainActivity.this.selectedAlias,
                                        "SHA1", params[0].getBytes(),
                                        MainActivity.this.data);
                            } catch (Exception e) {
                                Log.e(TAG, "error while verifying data:", e);
                                return null;
                            }
                        }

                        protected void onPostExecute(Boolean result) {
                            this.pd.dismiss();
                            Toast.makeText(MainActivity.this,
                                    result ? "good signature" : "bad signature",
                                    Toast.LENGTH_LONG).show();
                        };
                    }.execute(response);

                }
            });
            return;
        }
        if (requestCode == DECRYPT_ACTION) {
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
                                MainActivity.this.selectedAlias, "RSA",
                                "ECB/PKCS1Padding", MainActivity.this.data, null);
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
            return;
        }
    }

    public void signClicked(View view) {
        if (this.selectedAlias == null)
            return;
        
        Intent i = CryptOracle.createCheckAccessIntent(this, this.selectedAlias, CryptOracle.UsageType.PRIVATE_SIGN);
        startActivityForResult(i, SIGN_ACTION);
    }

    public void verifyClicked(View view) {
        if (this.selectedAlias == null)
            return;

        Intent i = CryptOracle.createCheckAccessIntent(this, this.selectedAlias, CryptOracle.UsageType.PUBLIC_VERIFY);
        startActivityForResult(i, VERIFY_ACTION);        
    }

    public void decryptClicked(View view) {
        if (this.selectedAlias == null || this.data == null)
            return;

        Intent i = CryptOracle.createCheckAccessIntent(this, this.selectedAlias, CryptOracle.UsageType.PRIVATE_DECRYPT);
        startActivityForResult(i, DECRYPT_ACTION);
    }
    
    public void testSymmetricClicked(View view) {
        
        new AsyncTask<Void, Void, Boolean>() {
            private ProgressDialog pd;

            protected void onPreExecute() {
                this.pd = new ProgressDialog(MainActivity.this);
                this.pd.setMessage("Testing symmetric crypto...");
                this.pd.setIndeterminate(true);
                this.pd.setCancelable(false);
                this.pd.show();
            };

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    CryptOracle.generateSymmetricKey(MainActivity.this, "symtest", "AES", 128);

                    byte[] plaintext = new byte[] { (byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef,
                            (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe,
                            0x12, 0x34, 0x56, 0x78,
                            (byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0xf0 };
                    
                    byte[] iv = new byte[] { 0x42, 0x00, 0x13, 0x11, 0x22, 0x33, 0x44, 0x55 };
                    IvParameterSpec ivp = new IvParameterSpec(iv);
                    
                    byte[] ciphertext = CryptOracle.encryptData(MainActivity.this, "symtest", "AES", "GCM/NOPADDING", plaintext, ivp);
                    byte[] p2 = CryptOracle.decryptData(MainActivity.this, "symtest", "AES", "GCM/NOPADDING", ciphertext, ivp);
                    
                    Log.i(TAG, "correct ctext=" + toHexString(ciphertext, ":"));
                    ciphertext[0] ^= 0xff;
                    Log.i(TAG, "bad ctext=" + toHexString(ciphertext, ":"));
                    byte[] p3 = CryptOracle.decryptData(MainActivity.this, "symtest", "AES", "GCM/NOPADDING", ciphertext, ivp);
                    
                    Log.i(TAG, "correct output=" + toHexString(p2, ":"));
                    Log.i(TAG, "bad output=" + toHexString(p3, ":"));
                    
                    CryptOracle.deleteSymmetricKey(MainActivity.this, "symtest");
                    
                    return Arrays.equals(plaintext, p2);
                } catch (Exception e) {
                    Log.e(TAG, "error while testing symmetric crypto", e);
                    return false;
                }
            }

            protected void onPostExecute(Boolean result) {
                this.pd.dismiss();
                Toast.makeText(MainActivity.this, "plaintexts are equal=" + result, Toast.LENGTH_SHORT).show();
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

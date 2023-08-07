package ru.rutoken.demobank;



import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import ru.rutoken.demobank.pkcs11caller.Token;
import ru.rutoken.demobank.pkcs11caller.TokenManager;
import ru.rutoken.demobank.pkcs11caller.exception.Pkcs11Exception;
import ru.rutoken.demobank.ui.TokenManagerListener;
import ru.rutoken.demobank.ui.login.LoginActivity;
import ru.rutoken.demobank.ui.main.MainActivity;
import ru.rutoken.demobank.ui.payment.PaymentsActivity;
import ru.rutoken.demobank.ui.TokenManagerListener;
import ru.rutoken.demobank.utils.Pkcs11ErrorTranslator;
import javax.crypto.SecretKey;
import ru.rutoken.demobank.KeyUtils;





public class BiometricActivity extends LoginActivity {

    //UI Views
    private static final String SIGN_DATA = "sign me";
    private static final String KEY_NAME = "biometric_key";
    private static final String ENCRYPTED_PASSWORD_KEY = "ENCRYPTED_PASSWORD_KEY";

    private TextView authStatusTv;
    private Button authBtn;

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private Button exitBtn;
    private String mTokenSerial = TokenManagerListener.NO_TOKEN;
    private String mCertificateFingerprint = TokenManagerListener.NO_FINGERPRINT;
    private Token mToken = null;
    private boolean isAuthenticationSucceeded = false;
    private boolean exit = false;
    String encryptedPasswordString;
    String decryptedPassword;
    private SecretKey biometricKey;

    public String getActivityClassIdentifier() {
        return getClass().getName();
    }
    protected void manageTokenOperationError(@Nullable Pkcs11Exception exception) {
        mToken.clearPin();
        String message = (exception == null) ? getString(R.string.error)
                : Pkcs11ErrorTranslator.getInstance(this).messageForRV(exception.getErrorCode());
    }

    @Override
    protected void manageTokenOperationSucceed() {
        if (isAuthenticationSucceeded) {
            startActivity(new Intent(BiometricActivity.this, PaymentsActivity.class)
                    .putExtra(MainActivity.EXTRA_TOKEN_SERIAL, mTokenSerial)
                    .putExtra(MainActivity.EXTRA_CERTIFICATE_FINGERPRINT, mCertificateFingerprint));
            isAuthenticationSucceeded = false;
        }
        if(exit){ startActivity(new Intent(BiometricActivity.this, LoginActivity.class));}
        }
    private void saveEncryptedPassword(byte[] encryptedPassword) {
        SharedPreferences preferences = getSharedPreferences("YourPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(ENCRYPTED_PASSWORD_KEY, Base64.encodeToString(encryptedPassword, Base64.DEFAULT));
        editor.apply();
    }

    public byte[] getStoredEncryptedPassword() {
        SharedPreferences preferences = getSharedPreferences("YourPrefs", MODE_PRIVATE);
        String encryptedPasswordString = preferences.getString(ENCRYPTED_PASSWORD_KEY, null);
        return Base64.decode(encryptedPasswordString, Base64.DEFAULT);
    }


            @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biometric);


        //init UI views
        authStatusTv = findViewById(R.id.authStatusTv);
        authBtn = findViewById(R.id.authBtn);
        exitBtn = findViewById(R.id.exitBtn);
        Intent intent = getIntent();
        String savedPassword = getIntent().getStringExtra("savedPassword");
        mTokenSerial = intent.getStringExtra(MainActivity.EXTRA_TOKEN_SERIAL);
        mCertificateFingerprint = intent.getStringExtra(MainActivity.EXTRA_CERTIFICATE_FINGERPRINT);
        mToken = TokenManager.getInstance().getTokenBySerial(mTokenSerial);

        //init biometric
        executor = ContextCompat.getMainExecutor(this);


        biometricPrompt = new BiometricPrompt(BiometricActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                //error authenticating, stop tasks that requires auth
                authStatusTv.setText("Authentication error: " + errString);
                Toast.makeText(BiometricActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                    isAuthenticationSucceeded = true;
                boolean keyGenerated = KeyUtils.generateBiometricKey(BiometricActivity.this);
                //boolean keyGenerated = KeyUtils.generateBiometricKey(this);
                if (keyGenerated) {

                    // Получаем ключ из Android Keystore
                    biometricKey = KeyUtils.getBiometricKey();
                    if (biometricKey != null) {
                        try {
                            // Шифруем пароль с использованием ключа
                            byte[] encryptedPassword = KeyUtils.encryptData(savedPassword, biometricKey);
                            saveEncryptedPassword(encryptedPassword);
                            encryptedPasswordString = Base64.encodeToString(encryptedPassword, Base64.DEFAULT);
                            // Расшифровываем пароль
                            // decryptedPassword = KeyUtils.decryptData(encryptedPassword, biometricKey);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    Toast.makeText(BiometricActivity.this, savedPassword +"Зашифрованный пароль: " + encryptedPasswordString + "  Расшифрованный пароль" + decryptedPassword, Toast.LENGTH_SHORT).show();
                } else {
                }
                    authStatusTv.setText("Authentication succeed...!");
                    Toast.makeText(BiometricActivity.this, "Authentication succeed...!", Toast.LENGTH_SHORT).show();
                    manageTokenOperationSucceed();




            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                //failed authenticating, stop tasks that requires auth
                authStatusTv.setText("Authentication failed...!");
                Toast.makeText(BiometricActivity.this, "Authentication failed...!", Toast.LENGTH_SHORT).show();
            }
        });

        //setup title,description on auth dialog
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Login using fingerprint authentication")
                .setNegativeButtonText("User App Password")
                .build();

        //handle authBtn click, start authentication
        authBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //show auth dialog
                biometricPrompt.authenticate(promptInfo);
            }
        });
        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exit=true;
            }
        });

            }


}





/*
 * Copyright (c) 2018, JSC Aktiv-Soft. See https://download.rutoken.ru/License_Agreement.pdf
 * All Rights Reserved.
 */

package ru.rutoken.demobank.ui.login;

import static ru.rutoken.demobank.KeyUtils.decryptData;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

import javax.crypto.SecretKey;

import ru.rutoken.demobank.KeyUtils;
import ru.rutoken.demobank.R;
import ru.rutoken.demobank.BiometricActivity;
import ru.rutoken.demobank.pkcs11caller.Token;
import ru.rutoken.demobank.pkcs11caller.TokenManager;
import ru.rutoken.demobank.pkcs11caller.exception.Pkcs11Exception;
import ru.rutoken.demobank.ui.Pkcs11CallerActivity;
import ru.rutoken.demobank.ui.TokenManagerListener;
import ru.rutoken.demobank.ui.main.MainActivity;
import ru.rutoken.demobank.ui.payment.PaymentsActivity;
import ru.rutoken.demobank.utils.Pkcs11ErrorTranslator;
import ru.rutoken.demobank.BiometricActivity;

public class LoginActivity extends Pkcs11CallerActivity {
    /**
     * Data that we have received from the server to do a challenge-response authentication
     */
    private static final String SIGN_DATA = "sign me";

    // GUI
    private Button mLoginButton;
    private EditText mPinEditText;
    private TextView mAlertTextView;
    private ProgressBar mLoginProgressBar;
    private Dialog mOverlayDialog;

    private String mTokenSerial = TokenManagerListener.NO_TOKEN;
    private String mCertificateFingerprint = TokenManagerListener.NO_FINGERPRINT;
    private Token mToken = null;
    private SecretKey biometricKey;

    private CheckBox mCheckBox;
    private SharedPreferences sharedPreferences;
    private static final String PREF_FIRST_BIOMETRIC_USE = "first_biometric_use";
    private static final String ENCRYPTED_PASSWORD_KEY = "ENCRYPTED_PASSWORD_KEY";
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;


    @Override
    public String getActivityClassIdentifier() {
        return getClass().getName();
    }

    private void showLogonStarted() {
        mLoginProgressBar.setVisibility(View.VISIBLE);
        mLoginButton.setEnabled(false);
        mOverlayDialog.show();
    }

    private void showLogonFinished() {
        mLoginProgressBar.setVisibility(View.GONE);
        mLoginButton.setEnabled(true);
        mOverlayDialog.dismiss();
    }

    @Override
    protected void manageTokenOperationError(@Nullable Pkcs11Exception exception) {
        mToken.clearPin();
        String message = (exception == null) ? getString(R.string.error)
                : Pkcs11ErrorTranslator.getInstance(this).messageForRV(exception.getErrorCode());

        mAlertTextView.setText(message);
        showLogonFinished();
    }

    @Override
    protected void manageTokenOperationCanceled() {
        showLogonFinished();
    }

    @Override
    protected void manageTokenOperationSucceed() {
        showLogonFinished();
        if (mCheckBox.isChecked()) {
            startActivity(new Intent(LoginActivity.this, BiometricActivity.class)
                    .putExtra("savedPassword", mPinEditText.getText().toString())
                    .putExtra(MainActivity.EXTRA_TOKEN_SERIAL, mTokenSerial)
                    .putExtra(MainActivity.EXTRA_CERTIFICATE_FINGERPRINT, mCertificateFingerprint));
        } else {
            startActivity(new Intent(LoginActivity.this, PaymentsActivity.class)
                    .putExtra(MainActivity.EXTRA_TOKEN_SERIAL, mTokenSerial)
                    .putExtra(MainActivity.EXTRA_CERTIFICATE_FINGERPRINT, mCertificateFingerprint));
        }
    }

    private byte[] getStoredEncryptedPassword() {
        SharedPreferences preferences = getSharedPreferences("YourPrefs", MODE_PRIVATE);
        String encryptedPasswordString = preferences.getString(ENCRYPTED_PASSWORD_KEY, null);
        if (encryptedPasswordString == null)
            return null;
        return Base64.decode(encryptedPasswordString, Base64.DEFAULT);
    }

    protected void authenticateWithFingerprint() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(LoginActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                //error authenticating, stop tasks that requires auth
                Toast.makeText(LoginActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                byte[] encryptedPassword = getStoredEncryptedPassword();
                biometricKey = KeyUtils.getBiometricKey();
                String decryptedPassword = null;
                // try-catch обязателен для методов encryptData и decryptData
                if (encryptedPassword != null) {
                    try {
                        decryptedPassword = KeyUtils.decryptData(encryptedPassword, biometricKey);
                    } catch (Exception e) {
                        e.printStackTrace();

                    }
                    login(mToken, decryptedPassword, mCertificateFingerprint, SIGN_DATA.getBytes());
                    Toast.makeText(LoginActivity.this, "Authentication succeed...!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                //failed authenticating, stop tasks that requires auth
                Toast.makeText(LoginActivity.this, "Authentication failed...!", Toast.LENGTH_SHORT).show();
            }
        });

        //setup title,description on auth dialog
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Login using fingerprint authentication")
                .setNegativeButtonText("User App Password")
                .build();
        biometricPrompt.authenticate(promptInfo);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        byte[] encryptedPassword = getStoredEncryptedPassword();
        if (encryptedPassword != null) {
            authenticateWithFingerprint();
        }

        Intent intent = getIntent();
        mTokenSerial = intent.getStringExtra(MainActivity.EXTRA_TOKEN_SERIAL);
        mCertificateFingerprint = intent.getStringExtra(MainActivity.EXTRA_CERTIFICATE_FINGERPRINT);
        mToken = TokenManager.getInstance().getTokenBySerial(mTokenSerial);
        if (mToken == null) {
            Toast.makeText(this, R.string.rutoken_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mOverlayDialog = new Dialog(this, android.R.style.Theme_Panel);
        mOverlayDialog.setCancelable(false);

        setupActionBar();
        setupUI();

        BiometricManager biometricManager = BiometricManager.from(LoginActivity.this);
        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            mCheckBox.setVisibility(View.VISIBLE);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

    }

    private void setupActionBar() {
        View view = getLayoutInflater().inflate(R.layout.actionbar_layout, null);

        ActionBar.LayoutParams params = new ActionBar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);

        /* Custom actionbar */
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(view, params);
        }
    }

    private void setupUI() {
        mLoginButton = findViewById(R.id.loginB);
        mPinEditText = findViewById(R.id.pinET);
        mAlertTextView = findViewById(R.id.alertTV);
        mLoginProgressBar = findViewById(R.id.loginPB);

        mLoginProgressBar.setVisibility(View.GONE);

        mLoginButton.setEnabled(false);
        mCheckBox = findViewById(R.id.checkBox);

        mPinEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                if (mPinEditText.getText().toString().isEmpty()) {
                    mLoginButton.setEnabled(false);
                }
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (mPinEditText.getText().toString().isEmpty()) {
                    mLoginButton.setEnabled(false);
                } else {
                    mLoginButton.setEnabled(true);
                }
            }
        });
        mPinEditText.requestFocus();

        mLoginButton.setOnClickListener(view -> {
            TokenManagerListener.getInstance(this).resetWaitForToken();
            showLogonStarted();
            // Certificate and sign data are used for a challenge-response authentication.
            login(mToken, mPinEditText.getText().toString(), mCertificateFingerprint, SIGN_DATA.getBytes());

        });
    }
}

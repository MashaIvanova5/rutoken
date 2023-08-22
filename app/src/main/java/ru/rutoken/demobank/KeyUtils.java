package ru.rutoken.demobank;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class KeyUtils {

    private static final String KEY_NAME = "biometric_key";

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean generateBiometricKey(Context context) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            if (!keyStore.containsAlias(KEY_NAME)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true) // Запрашивать биометрическую аутентификацию при использовании ключа
                        .setUserAuthenticationValidityDurationSeconds(5 * 60); // Время действия аутентификации, 5 минут

                keyGenerator.init(builder.build());
                keyGenerator.generateKey();
                return true;
            } else {
                // Ключ уже существует
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static SecretKey getBiometricKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return (SecretKey) keyStore.getKey(KEY_NAME, null);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException |
                 UnrecoverableKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encryptData(String data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

        return combined;
    }

    public static String decryptData(byte[] encryptedDataWithIV, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");

        // Извлекаем IV из данных.
        int ivSize = cipher.getBlockSize();
        byte[] iv = new byte[ivSize];
        System.arraycopy(encryptedDataWithIV, 0, iv, 0, ivSize);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // Извлекаем зашифрованные данные, исключая IV.
        byte[] encryptedData = new byte[encryptedDataWithIV.length - ivSize];
        System.arraycopy(encryptedDataWithIV, ivSize, encryptedData, 0, encryptedData.length);

        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] decryptedData = cipher.doFinal(encryptedData);

        return new String(decryptedData, StandardCharsets.UTF_8);
    }


}
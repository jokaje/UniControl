package com.example.unicontrol.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

public class CryptoUtils {

    private static final String TAG = "CryptoUtils";
    private static final String PREFS_NAME = "OpenClawCryptoPrefs";
    private static final String KEY_PRIVATE = "ed25519_private_key";
    private static final String KEY_PUBLIC = "ed25519_public_key";
    private static final String KEY_DEVICE_ID = "openclaw_device_id";

    private final SharedPreferences prefs;

    public CryptoUtils(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // ACHTUNG: Keine automatische Generierung mehr auf Wunsch des Nutzers!
    }

    /**
     * Prüft ob eine VOLLSTÄNDIGE, manuell eingetragene Identität existiert.
     */
    public boolean hasValidIdentity() {
        String priv = prefs.getString(KEY_PRIVATE, "");
        String pub = prefs.getString(KEY_PUBLIC, "");
        String devId = prefs.getString(KEY_DEVICE_ID, "");
        return !priv.isEmpty() && !pub.isEmpty() && !devId.isEmpty();
    }

    public String getDeviceId() {
        return prefs.getString(KEY_DEVICE_ID, "");
    }

    public String getPublicKeyBase64() {
        return prefs.getString(KEY_PUBLIC, "");
    }

    public String getPrivateKeyBase64() {
        return prefs.getString(KEY_PRIVATE, "");
    }

    /**
     * Setzt eine manuelle Identität (Experten-Modus).
     */
    public void setIdentity(String deviceId, String privateKeyBase64, String publicKeyBase64) {
        prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_PRIVATE, privateKeyBase64)
                .putString(KEY_PUBLIC, publicKeyBase64)
                .apply();
    }

    public String signMessage(String message) {
        try {
            String privateKeyBase64 = prefs.getString(KEY_PRIVATE, "");
            if (privateKeyBase64.isEmpty()) return "";

            byte[] privateKeyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP);

            Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(privateKeyBytes, 0);

            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, privateKey);

            byte[] messageBytes = message.getBytes("UTF-8");
            signer.update(messageBytes, 0, messageBytes.length);
            byte[] signature = signer.generateSignature();

            return Base64.encodeToString(signature, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Fehler beim Signieren der Nachricht", e);
            return "";
        }
    }
}
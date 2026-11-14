package bj.planificateur;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Garde le code PIN du téléphone chiffré (clé AES dans l'Android Keystore) et les choix de déverrouillage. */
public class Vault {
    static final String ALIAS = "planificateur_pin";
    static final String PREFS = "coffre";
    static final String K_PIN = "pin";
    static final String K_ON = "deverrouiller";
    static final String K_RELOCK = "reverrouiller";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    public static boolean savePin(Context c, String pin) {
        try {
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = ci.getIV();
            byte[] enc = ci.doFinal(pin.getBytes("UTF-8"));
            byte[] all = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(enc, 0, all, iv.length, enc.length);
            sp(c).edit().putString(K_PIN, Base64.encodeToString(all, Base64.NO_WRAP)).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Le code en clair, ou null s'il n'y en a pas (ou s'il ne peut plus être lu). */
    public static String getPin(Context c) {
        try {
            String s = sp(c).getString(K_PIN, null);
            if (s == null) return null;
            byte[] all = Base64.decode(s, Base64.NO_WRAP);
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(ci.doFinal(all, 12, all.length - 12), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasPin(Context c) {
        return sp(c).getString(K_PIN, null) != null;
    }

    public static void clearPin(Context c) {
        sp(c).edit().remove(K_PIN).putBoolean(K_ON, false).apply();
    }

    public static boolean enabled(Context c) {
        return sp(c).getBoolean(K_ON, false);
    }

    public static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(K_ON, on).apply();
    }

    public static boolean relock(Context c) {
        return sp(c).getBoolean(K_RELOCK, true);
    }

    public static void setRelock(Context c, boolean on) {
        sp(c).edit().putBoolean(K_RELOCK, on).apply();
    }
}

package com.gideonsoftworks.gcore;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

public class GKeyringManager
{

    private static final String SERVICE_NAME = "RelaySFTP";
    private static final String ACCOUNT_NAME = "passwords";
    private static Keyring keyring = null;
    private static boolean keyringInitialized = false;
    private static Map<String, String> passwords = null;
    private static boolean loaded = false;

    /**
     * Lazily create the Keyring backend on first use.
     * Returns true if keyring is available.
     */
    private static boolean ensureKeyring()
    {
        if (keyringInitialized) return keyring != null;
        keyringInitialized = true;

        if (Boolean.getBoolean("relaysftp.keyring.disabled")) {
            keyring = null;
            return false;
        }

        try {
            keyring = Keyring.create();
        } catch (Exception e) {
            System.err.println("System keyring unavailable: " + e.getMessage());
            keyring = null;
        }
        return keyring != null;
    }

    /**
     * Load all passwords from keyring into memory (single prompt).
     */
    public static void loadAll()
    {
        if (loaded) return;
        loaded = true;

        if (!ensureKeyring())
        {
            passwords = new HashMap<>();
            return;
        }

        try {
            String json = keyring.getPassword(SERVICE_NAME, ACCOUNT_NAME);
            if (json != null && !json.isEmpty())
            {
                passwords = new Gson().fromJson(json,
                    new TypeToken<HashMap<String, String>>(){}.getType());
            }
        } catch (PasswordAccessException e) {
            // first run or no entry yet
        }

        if (passwords == null) passwords = new HashMap<>();
    }

    /**
     * Save all passwords from memory back to keyring.
     */
    public static void saveAll()
    {
        if (keyring == null || passwords == null || passwords.isEmpty()) return;

        try {
            String json = new GsonBuilder().create().toJson(passwords);
            keyring.setPassword(SERVICE_NAME, ACCOUNT_NAME, json);
        } catch (PasswordAccessException e) {
            // Never log the secret values; only the failure of the keyring write itself.
            GAuditLog.error( "", "keyring-save", null, e );
            GAtomicFileWriter.showSaveError( "Failed to save your saved passwords to the system keyring: " + e.getMessage() );
        }
    }

    public static String getPassword(String key)
    {
        if (!loaded) loadAll();
        return passwords.get(key);
    }

    public static void setPassword(String key, String password)
    {
        if (!loaded) loadAll();
        if (password == null || password.isEmpty())
        {
            if (passwords.remove(key) != null) saveAll();
        } else {
            // Skip the keychain write (and its auth prompt) when the secret hasn't changed —
            // e.g. re-saving a bookmark without touching its password.
            if (password.equals(passwords.get(key))) return;
            passwords.put(key, password);
            saveAll();
        }
    }

    public static void deletePassword(String key)
    {
        if (!loaded) loadAll();
        if (passwords.remove(key) != null) saveAll();
    }

    // Key a credential by its connection identity (not the bookmark's display name), so a
    // rename keeps the password and bookmarks for the same host:port:user share one secret.
    public static String passwordKey(String hostname, int port, String username)
    {
        return "pw:" + hostname + ":" + port + ":" + (username != null ? username : "");
    }
}

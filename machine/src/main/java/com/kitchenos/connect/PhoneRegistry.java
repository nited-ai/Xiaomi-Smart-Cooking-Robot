package com.kitchenos.connect;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Equal local capabilities per phone; only token hashes are stored on the machine. */
public final class PhoneRegistry {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    private String invitation = "";
    private long inviteUntil;
    public PhoneRegistry(Context context) { preferences = context.getSharedPreferences("paired-phones", 0); }
    public static String randomToken() {
        byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes);
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }
    private static String hash(String token) throws Exception {
        return com.kitchenos.common.Pairing.fingerprint(token.getBytes(StandardCharsets.US_ASCII));
    }
    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
    public synchronized String invitation() {
        if (SystemClock.elapsedRealtime() >= inviteUntil) {
            invitation = randomToken(); inviteUntil = SystemClock.elapsedRealtime() + 600000;
        }
        return invitation;
    }
    public synchronized boolean invited(String token) {
        return token != null && token.matches("[a-f0-9]{32}") && !invitation.isEmpty()
                && SystemClock.elapsedRealtime() < inviteUntil && same(invitation, token);
    }
    public synchronized void closeInvitation() { invitation = ""; inviteUntil = 0; }
    private JSONArray read() throws Exception { return new JSONArray(preferences.getString("phones", "[]")); }
    private void write(JSONArray values) throws IOException {
        if (!preferences.edit().putString("phones", values.toString()).commit()) throw new IOException("Kopplung konnte nicht gespeichert werden.");
    }
    public JSONObject enroll(JSONObject body) throws Exception {
        String id = body.getString("id"), token = body.getString("token");
        String name = body.optString("name", "Handy").trim().replaceAll("[\\p{Cntrl}]", "");
        if (!id.matches("[a-f0-9]{32}") || !token.matches("[a-f0-9]{32}") || name.isEmpty() || name.length() > 60)
            throw new IllegalArgumentException("Name oder Kopplungsdaten sind ungültig.");
        synchronized (LOCK) {
            JSONArray phones = read(); JSONObject found = null;
            for (int i = 0; i < phones.length(); i++) if (id.equals(phones.getJSONObject(i).getString("id"))) found = phones.getJSONObject(i);
            String digest = hash(token);
            if (found != null && !same(found.getString("hash"), digest)) throw new IOException("Für dieses Handy ist eine neue Kopplung erforderlich.");
            if (found == null) {
                if (phones.length() >= 32) throw new IOException("Bitte zuerst ein nicht mehr verwendetes Handy entfernen.");
                found = new JSONObject().put("id", id).put("hash", digest).put("addedAt", System.currentTimeMillis());
                phones.put(found);
            }
            found.put("name", name).put("lastSeen", System.currentTimeMillis()); write(phones);
            return new JSONObject().put("paired", true).put("id", id);
        }
    }
    public boolean authorized(String token) {
        if (token == null || !token.matches("[a-f0-9]{32}")) return false;
        synchronized (LOCK) {
            try {
                String digest = hash(token); JSONArray phones = read();
                for (int i = 0; i < phones.length(); i++) {
                    JSONObject phone = phones.getJSONObject(i);
                    if (same(phone.getString("hash"), digest)) {
                        if (System.currentTimeMillis() - phone.optLong("lastSeen") > 60000) {
                            phone.put("lastSeen", System.currentTimeMillis()); write(phones);
                        }
                        return true;
                    }
                }
            } catch (Exception ignored) { }
            return false;
        }
    }
    public JSONArray list() throws Exception {
        synchronized (LOCK) {
            JSONArray rows = read(), result = new JSONArray();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                result.put(new JSONObject().put("id", row.getString("id")).put("name", row.getString("name"))
                        .put("addedAt", row.getLong("addedAt")).put("lastSeen", row.optLong("lastSeen")));
            }
            return result;
        }
    }
    public void remove(String id) throws Exception {
        synchronized (LOCK) {
            JSONArray rows = read(), kept = new JSONArray();
            for (int i = 0; i < rows.length(); i++) if (!id.equals(rows.getJSONObject(i).getString("id"))) kept.put(rows.getJSONObject(i));
            write(kept);
        }
    }
    public void clear() throws Exception { synchronized (LOCK) { write(new JSONArray()); closeInvitation(); } }
}

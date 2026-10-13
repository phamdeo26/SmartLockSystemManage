/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** Password hashing: SHA-256 of (salt + password), stored as lowercase hex. */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /** Random 16-character hex salt (fits the salt column). */
    public static String newSalt() {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Hash stored for a PIN / card / fingerprint: SHA-256 of "<TYPE>:<lockId>:<value>" (same formula as 02_sample_data.sql). */
    public static String credentialHash(String type, int lockId, String value) {
        return hash(type + ":" + lockId + ":", value);
    }

    public static boolean matches(String salt, String password, String storedHash) {
        byte[] expected = storedHash.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(salt, password).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}

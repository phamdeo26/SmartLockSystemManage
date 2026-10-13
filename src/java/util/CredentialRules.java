/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;

/**
 *
 * @author acer
 */


/** Format rules for the three credential types. Used by the web forms and by the ESP32 endpoint. */
public final class CredentialRules {

    public static final String PIN = "PIN";
    public static final String FINGERPRINT = "FINGERPRINT";
    public static final String CARD = "CARD";

    private CredentialRules() {
    }

    public static boolean isType(String type) {
        return PIN.equals(type) || FINGERPRINT.equals(type) || CARD.equals(type);
    }

    /**
     * Returns the cleaned value, or null when the value is not valid for the type.
     * PIN: 4-8 digits. FINGERPRINT: sensor slot id 1-1000. CARD: UID as 8-20 hex characters (case and spaces ignored).
     */
    public static String normalize(String type, String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (PIN.equals(type)) {
            return v.matches("[0-9]{4,8}") ? v : null;
        }
        if (FINGERPRINT.equals(type)) {
            if (!v.matches("[0-9]{1,4}")) {
                return null;
            }
            int slot = Integer.parseInt(v);
            return slot >= 1 && slot <= 1000 ? String.valueOf(slot) : null;
        }
        if (CARD.equals(type)) {
            String uid = v.replace(" ", "").replace(":", "").toUpperCase();
            return uid.matches("[0-9A-F]{8,20}") ? uid : null;
        }
        return null;
    }

    public static String describe(String type) {
        return PIN.equals(type) ? "PIN (4 to 8 digits)"
                : FINGERPRINT.equals(type) ? "fingerprint sensor ID (1 to 1000)" : "card UID (8 to 20 hex characters)";
    }
}

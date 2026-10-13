/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 *
 * @author acer
 */

/** Small helpers for JSP pages (replaces JSTL): HTML escaping, date/number formatting, sort links. */
public final class Html {

    private Html() {
    }

    /** Escapes text so user input can never become HTML or script. Null becomes an empty string. */
    public static String esc(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static String date(Date date, String pattern) {
        return date == null ? "" : new SimpleDateFormat(pattern).format(date);
    }

    public static String num(double value, String pattern) {
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(value);
    }

    /** Error text of one form field, already escaped. */
    public static String err(Map<String, String> errors, String field) {
        return errors == null ? "" : esc(errors.get(field));
    }

    public static String selected(boolean condition) {
        return condition ? "selected" : "";
    }

    /** Link used in a sortable table header: clicking the current column flips asc/desc. */
    public static String sortHref(String base, String filterQuery, String currentSort, String currentDir, String column) {
        String dir = column.equals(currentSort) && "asc".equals(currentDir) ? "desc" : "asc";
        return base + "?" + (filterQuery == null || filterQuery.isEmpty() ? "" : filterQuery + "&") + "sort=" + column + "&dir=" + dir;
    }

    // ---- small helpers for the page layout (no JSTL, no include files) ----

    private static final Map<String, String> ICONS = new HashMap<>();

    static {
        ICONS.put("dashboard", "<rect x=\"3\" y=\"3\" width=\"7\" height=\"7\" rx=\"1\"/><rect x=\"14\" y=\"3\" width=\"7\" height=\"7\" rx=\"1\"/><rect x=\"3\" y=\"14\" width=\"7\" height=\"7\" rx=\"1\"/><rect x=\"14\" y=\"14\" width=\"7\" height=\"7\" rx=\"1\"/>");
        ICONS.put("lock", "<rect x=\"4\" y=\"11\" width=\"16\" height=\"10\" rx=\"2\"/><path d=\"M8 11V7a4 4 0 0 1 8 0v4\"/>");
        ICONS.put("unlock", "<rect x=\"4\" y=\"11\" width=\"16\" height=\"10\" rx=\"2\"/><path d=\"M8 11V7a4 4 0 0 1 7.5-2\"/>");
        ICONS.put("list", "<path d=\"M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01\"/>");
        ICONS.put("key", "<circle cx=\"8\" cy=\"15\" r=\"4\"/><path d=\"M11 12l9-9M16 7l3 3\"/>");
        ICONS.put("shield", "<path d=\"M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z\"/>");
        ICONS.put("users", "<circle cx=\"9\" cy=\"8\" r=\"3.5\"/><path d=\"M2 20c0-3.5 3-6 7-6s7 2.5 7 6\"/><circle cx=\"17\" cy=\"9\" r=\"2.5\"/><path d=\"M17 14c3 0 5 2 5 5\"/>");
        ICONS.put("logout", "<path d=\"M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9\"/>");
        ICONS.put("plus", "<path d=\"M12 5v14M5 12h14\"/>");
        ICONS.put("edit", "<path d=\"M4 20h4L19 9l-4-4L4 16z\"/>");
        ICONS.put("trash", "<path d=\"M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3\"/>");
        ICONS.put("globe", "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18\"/>");
        ICONS.put("card", "<rect x=\"3\" y=\"6\" width=\"18\" height=\"12\" rx=\"2\"/><path d=\"M3 10h18M7 15h4\"/>");
        ICONS.put("finger", "<path d=\"M5 12a7 7 0 0 1 14 0M8 14a4 4 0 0 1 8 0M12 12v8\"/>");
        ICONS.put("pin", "<circle cx=\"7\" cy=\"7\" r=\"1.3\"/><circle cx=\"12\" cy=\"7\" r=\"1.3\"/><circle cx=\"17\" cy=\"7\" r=\"1.3\"/><circle cx=\"7\" cy=\"12\" r=\"1.3\"/><circle cx=\"12\" cy=\"12\" r=\"1.3\"/><circle cx=\"17\" cy=\"12\" r=\"1.3\"/><circle cx=\"12\" cy=\"17\" r=\"1.3\"/>");
        ICONS.put("alert", "<path d=\"M12 4l9 16H3zM12 10v4M12 17h.01\"/>");
        ICONS.put("clock", "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 7v5l3 2\"/>");
        ICONS.put("inbox", "<path d=\"M5 4h14v16H5zM9 9h6M9 13h6\"/>");
    }

    /** Inline SVG icon (drawn with the current text color). Unknown names give an empty string. */
    public static String icon(String name) {
        String paths = ICONS.get(name);
        return paths == null ? "" : "<svg class=\"ic\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">" + paths + "</svg>";
    }

    /** Icon name for a log method or credential type: WEB, PIN, CARD, FINGERPRINT. */
    public static String methodIcon(String method) {
        if ("WEB".equals(method)) {
            return "globe";
        }
        if ("PIN".equals(method)) {
            return "pin";
        }
        if ("CARD".equals(method)) {
            return "card";
        }
        return "finger";
    }

    public static String active(boolean condition) {
        return condition ? "active" : "";
    }

    /** "Alice Johnson" -> "AJ", used in the round avatar. */
    public static String initials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "?";
        }
        String[] words = fullName.trim().split("\\s+");
        String first = words[0].substring(0, 1);
        String second = words.length > 1 ? words[words.length - 1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }
}


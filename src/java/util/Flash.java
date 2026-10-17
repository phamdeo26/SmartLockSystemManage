/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;
import javax.servlet.http.HttpSession;
/**
 *
 * @author acer
 */

public final class Flash {

    private Flash() {
    }

    public static void success(HttpSession session, String message) {
        session.setAttribute("flashSuccess", message);
    }

    public static void error(HttpSession session, String message) {
        session.setAttribute("flashError", message);
    }
}


/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import dto.User;
/**
 *
 * @author acer
 */
public abstract class BaseController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(BaseController.class.getName());
    protected static final int PAGE_SIZE = 10;

    protected void render(HttpServletRequest req, HttpServletResponse resp, String view)
            throws ServletException, IOException {
        req.getRequestDispatcher("/WEB-INF/views/" + view + ".jsp").forward(req, resp);
    }

    /** Logs the technical details and shows the user a short English message only. */
    protected void fail(HttpServletRequest req, HttpServletResponse resp, Exception e)
            throws ServletException, IOException {
        LOGGER.log(Level.SEVERE, "Request failed: " + req.getRequestURI(), e);
        req.setAttribute("errorMessage", "Something went wrong. Please try again later.");
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        render(req, resp, "error");
    }

    protected User loginUser(HttpServletRequest req) {
        return (User) req.getSession().getAttribute("loginUser");
    }

    protected String param(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        return value == null ? "" : value.trim();
    }

    protected int intParam(HttpServletRequest req, String name, int defaultValue) {
        try {
            return Integer.parseInt(param(req, name));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Builds the pager link prefix, e.g. /app/locks?keyword=a&page= */
    protected String pageUrl(HttpServletRequest req, String path, String query) {
        return req.getContextPath() + path + "?" + (query.isEmpty() ? "" : query + "&") + "page=";
    }
}

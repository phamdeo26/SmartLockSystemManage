/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package filter;

import dto.User;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 *
 * @author acer
 */


/** Runs before every servlet: blocks anonymous users and checks the role of the logged-in user. */
@WebFilter(filterName = "AuthFilter", urlPatterns = {"/*"})
public class AuthFilter implements Filter {

    /** Paths only the ADMIN role may open. */
    private static final Set<String> ADMIN_ONLY = new HashSet<>(Arrays.asList(
            "/locks/form", "/locks/save", "/locks/delete",
            "/users", "/users/form", "/users/save", "/users/delete",
            "/rights", "/rights/form", "/rights/save", "/rights/delete",
            "/credentials/add", "/credentials/approve", "/credentials/reject", "/credentials/revoke"));

    /** Paths used by the ESP32. They need the X-API-Key header and never use a browser session. */
    private static final Set<String> DEVICE_PATHS = new HashSet<>(Arrays.asList(
            "/api/device/verify", "/api/device/poll", "/api/device/state"));

    private String deviceApiKey;

    @Override
    public void init(FilterConfig config) {
        deviceApiKey = config.getServletContext().getInitParameter("deviceApiKey");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8");

        String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
        if (path.equals("/login") || path.equals("/register") || path.startsWith("/css/")) {
            chain.doFilter(request, response);
            return;
        }

        // Never let the browser cache a protected page, so the Back button after logout shows nothing.
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        // The ESP32 never uses a browser session: no valid API key means 401, nothing else.
        if (path.startsWith("/api/")) {
            if (DEVICE_PATHS.contains(path) && hasValidApiKey(req)) {
                chain.doFilter(request, response);
            } else {
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
            return;
        }

        HttpSession session = req.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("loginUser");
        if (user == null) {
            boolean expired = req.getRequestedSessionId() != null && !req.isRequestedSessionIdValid();
            resp.sendRedirect(req.getContextPath() + "/login" + (expired ? "?expired=1" : ""));
            return;
        }
        if (ADMIN_ONLY.contains(path) && !user.isAdmin()) {
            req.setAttribute("errorMessage", "You do not have permission to open this page.");
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            req.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasValidApiKey(HttpServletRequest req) {
        String sent = req.getHeader("X-API-Key");
        if (sent == null || deviceApiKey == null || deviceApiKey.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), deviceApiKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void destroy() {
    }
}

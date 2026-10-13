/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import dao.CredentialDAO;
import dao.LockDao;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.CredentialRules;

/**
 *
 * @author User
 */
@WebServlet(name = "IoTController", urlPatterns = {"/api/device/verify", "/api/device/poll", "/api/device/state"})
public class IoTController extends BaseController {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 5 * 60 * 1000L;
    /** lockId -> {failure count, start of window}. Kept in memory: slows down guessing at the keypad. */
    private static final Map<Integer, long[]> FAILURES = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        if (!"/api/device/poll".equals(req.getServletPath())) {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        int lockId = intParam(req, "lockId", 0);
        if (lockId <= 0) {
            bad(resp);
            return;
        }
        try {
            resp.getWriter().println(new LockDao().consumeOpenRequest(lockId) ? "OPEN" : "NONE");
        } catch (Exception e) {
            serverError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        if ("/api/device/verify".equals(req.getServletPath())) {
            verify(req, resp);
        } else if ("/api/device/state".equals(req.getServletPath())) {
            state(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void verify(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int lockId = intParam(req, "lockId", 0);
        String type = param(req, "type");
        String value = CredentialRules.isType(type) ? CredentialRules.normalize(type, param(req, "value")) : null;
        if (lockId <= 0 || value == null) {
            bad(resp);
            return;
        }
        if (isBlocked(lockId)) {
            resp.setStatus(429);
            resp.getWriter().println("TOO_MANY_ATTEMPTS");
            return;
        }
        try {
            if ("SUCCESS".equals(new CredentialDAO().verify(lockId, type, value))) {
                clearFailures(lockId);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println("OK");
            } else {
                recordFailure(lockId);
                // The device gets the same answer for every refusal, so it learns nothing about why.
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().println("DENIED");
            }
        } catch (Exception e) {
            serverError(resp, e);
        }
    }

    private void state(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int lockId = intParam(req, "lockId", 0);
        String state = param(req, "state");
        if (lockId <= 0 || (!"LOCKED".equals(state) && !"UNLOCKED".equals(state))) {
            bad(resp);
            return;
        }
        try {
            resp.getWriter().println(new LockDao().setStatus(lockId, state) ? "OK" : "UNKNOWN_LOCK");
        } catch (Exception e) {
            serverError(resp, e);
        }
    }

    private void bad(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().println("BAD_REQUEST");
    }

    private void serverError(HttpServletResponse resp, Exception e) throws IOException {
        log("Device request failed", e);
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        resp.getWriter().println("ERROR");
    }

    private static synchronized boolean isBlocked(int lockId) {
        long[] entry = FAILURES.get(lockId);
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() - entry[1] > WINDOW_MILLIS) {
            FAILURES.remove(lockId);
            return false;
        }
        return entry[0] >= MAX_FAILURES;
    }
    private static synchronized void recordFailure(int lockId) {
        long now = System.currentTimeMillis();
        long[] entry = FAILURES.get(lockId);
        if (entry == null || now - entry[1] > WINDOW_MILLIS) {
            FAILURES.put(lockId, new long[]{1, now});
        } else {
            entry[0]++;
        }
    }

    private static synchronized void clearFailures(int lockId) {
        FAILURES.remove(lockId);
    }
}
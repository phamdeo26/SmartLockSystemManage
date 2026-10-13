/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;
import static controller.BaseController.PAGE_SIZE;
import dao.CredentialDAO;
import dao.LockDao;
import dao.UserDAO;
import dto.SmartLock;
import dto.User;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.CredentialRules;
import util.Flash;
import util.QueryString;
/**
 *
 * @author User
 */
@WebServlet(name = "CredentialController", urlPatterns = {"/credentials", "/credentials/form", "/credentials/request",
    "/credentials/add", "/credentials/approve", "/credentials/reject", "/credentials/revoke"})
public class CredentialController extends BaseController {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            if ("/credentials/form".equals(req.getServletPath())) {
                showForm(req, resp, "", "", "PIN", "");
            } else if ("/credentials".equals(req.getServletPath())) {
                showList(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            switch (req.getServletPath()) {
                case "/credentials/request":
                    requestPin(req, resp);
                    break;
                case "/credentials/add":
                    add(req, resp);
                    break;
                case "/credentials/approve":
                    decide(req, resp, "PENDING", "APPROVED", "Approved. It can now open the door.");
                    break;
                case "/credentials/reject":
                    decide(req, resp, "PENDING", "REJECTED", "Request rejected.");
                    break;
                case "/credentials/revoke":
                    decide(req, resp, "APPROVED", "REVOKED", "Revoked. It can no longer open the door.");
                    break;
                default:
                    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    private void showList(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        User user = loginUser(req);
        String type = param(req, "type");
        if (!CredentialRules.isType(type)) {
            type = "";
        }
        String status = param(req, "status");
        if (!status.equals("PENDING") && !status.equals("APPROVED") && !status.equals("REJECTED") && !status.equals("REVOKED")) {
            status = "";
        }
        String keyword = param(req, "keyword");
        // A normal user only ever sees their own rows; the user id comes from the session, never from the request.
        int userId = user.isAdmin() ? 0 : user.getUserId();
        if (user.isAdmin()) {
            req.setAttribute("pendingCount", new CredentialDAO().countPending());
        }
        req.setAttribute("pageResult", new dao.CredentialDAO().search(userId, type, status, keyword, intParam(req, "page", 1), PAGE_SIZE));
        req.setAttribute("type", type);
        req.setAttribute("status", status);
        req.setAttribute("keyword", keyword);
        req.setAttribute("pageUrl", pageUrl(req, "/credentials", QueryString.build("type", type, "status", status, "keyword", keyword)));
        render(req, resp, "credentials");
    }

    private void showForm(HttpServletRequest req, HttpServletResponse resp, String userId, String lockId, String type, String value)
            throws Exception {
        User user = loginUser(req);
        req.setAttribute("locks", user.isAdmin() ? new LockDao().listAll() : new LockDao().listForUser(user.getUserId()));
        if (user.isAdmin()) {
            req.setAttribute("users", new UserDAO().listActive());
        }
        req.setAttribute("userId", userId);
        req.setAttribute("lockId", lockId);
        req.setAttribute("type", type);
        req.setAttribute("label", param(req, "label"));
        render(req, resp, "credential-form");
    }

    private void requestPin(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        User user = loginUser(req);
        int lockId = intParam(req, "lockId", 0);
        String pin = CredentialRules.normalize("PIN", param(req, "value"));
        String confirm = param(req, "confirmValue");

        Map<String, String> errors = new LinkedHashMap<>();
        if (lockId <= 0) {
            errors.put("lockId", "Choose a lock.");
        }
        if (pin == null) {
            errors.put("value", "The PIN must be 4 to 8 digits.");
        } else if (!pin.equals(confirm)) {
            errors.put("confirmValue", "The two PINs do not match.");
        }
        if (errors.isEmpty()) {
            // Only a PIN can be requested here, and the owner is always the logged-in user.
            switch (new CredentialDAO().requestPin(user.getUserId(), lockId, pin)) {
                case "OK":
                    Flash.success(req.getSession(), "PIN request sent. It cannot open the door until an admin approves it.");
                    resp.sendRedirect(req.getContextPath() + "/credentials");
                    return;
                case "NO_RIGHT":
                    errors.put("lockId", "You do not have a valid access right for this lock.");
                    break;
                case "DUPLICATE":
                    errors.put("value", "This PIN is already in use on this lock. Choose another one.");
                    break;
                default:
                    errors.put("lockId", "That lock no longer exists.");
            }
        }
        req.setAttribute("errors", errors);
        showForm(req, resp, "", String.valueOf(lockId), "PIN", "");
    }

    private void add(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        User admin = loginUser(req);
        if (!admin.isAdmin()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        int userId = intParam(req, "userId", 0);
        int lockId = intParam(req, "lockId", 0);
        String type = param(req, "type");
        String value = CredentialRules.isType(type) ? CredentialRules.normalize(type, param(req, "value")) : null;
        String label = param(req, "label");

        Map<String, String> errors = new LinkedHashMap<>();
        if (userId <= 0) {
            errors.put("userId", "Choose a user.");
        }
        if (lockId <= 0) {
            errors.put("lockId", "Choose a lock.");
        }
        if (!CredentialRules.isType(type)) {
            errors.put("type", "Choose a type.");
        } else if (value == null) {
            errors.put("value", "Enter a valid " + CredentialRules.describe(type) + ".");
        }
        if (label.length() > 50) {
            errors.put("label", "The label must be at most 50 characters.");
        }
        if (errors.isEmpty()) {
            switch (new CredentialDAO().enroll(admin.getUserId(), userId, lockId, type, value, label)) {
                case "OK":
                    Flash.success(req.getSession(), "Registered and approved.");
                    resp.sendRedirect(req.getContextPath() + "/credentials");
                    return;
                case "DUPLICATE":
                    errors.put("value", "This " + type.toLowerCase() + " is already registered on this lock.");
                    break;
                case "NO_USER":
                    errors.put("userId", "That user does not exist or is disabled.");
                    break;
                default:
                    errors.put("lockId", "That lock no longer exists.");
            }
        }
        req.setAttribute("errors", errors);
        showForm(req, resp, String.valueOf(userId), String.valueOf(lockId), type.isEmpty() ? "PIN" : type, "");
    }

    private void decide(HttpServletRequest req, HttpServletResponse resp, String from, String to, String okMessage) throws Exception {
        User admin = loginUser(req);
        if (!admin.isAdmin()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (new CredentialDAO().decide(intParam(req, "credentialId", 0), admin.getUserId(), from, to)) {
            Flash.success(req.getSession(), okMessage);
        } else {
            Flash.error(req.getSession(), "That entry is no longer " + from.toLowerCase() + ", so nothing was changed.");
        }
        resp.sendRedirect(req.getContextPath() + "/credentials");
    }
}

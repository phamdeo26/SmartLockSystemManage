/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;
import static controller.BaseController.PAGE_SIZE;
import dao.AccessRightDAO;
import dao.LockDao;
import dao.UserDAO;
import dto.SmartLock;
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
import util.Flash;
import util.QueryString;
/**
 *
 * @author acer
 */
@WebServlet(name = "AccessRightController", urlPatterns = {"/rights", "/rights/form", "/rights/save", "/rights/delete"})
public class AccessRightController extends BaseController {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            if ("/rights/form".equals(req.getServletPath())) {
                showForm(req, resp, "", "", LocalDateTime.now().format(INPUT_FORMAT), "");
            } else if ("/rights".equals(req.getServletPath())) {
                String keyword = param(req, "keyword");
                req.setAttribute("pageResult", new AccessRightDAO().search(keyword, intParam(req, "page", 1), PAGE_SIZE));
                req.setAttribute("keyword", keyword);
                req.setAttribute("pageUrl", pageUrl(req, "/rights", QueryString.build("keyword", keyword)));
                render(req, resp, "rights");
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
            if ("/rights/save".equals(req.getServletPath())) {
                save(req, resp);
            } else if ("/rights/delete".equals(req.getServletPath())) {
                if (new AccessRightDAO().delete(intParam(req, "rightId", 0))) {
                    Flash.success(req.getSession(), "Access right removed successfully.");
                } else {
                    Flash.error(req.getSession(), "That access right no longer exists.");
                }
                resp.sendRedirect(req.getContextPath() + "/rights");
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    private void showForm(HttpServletRequest req, HttpServletResponse resp, String userId, String lockId, String from, String to)
            throws Exception {
        req.setAttribute("users", new UserDAO().listActive());
        req.setAttribute("locks", new LockDao().listAll());
        req.setAttribute("userId", userId);
        req.setAttribute("lockId", lockId);
        req.setAttribute("validFrom", from);
        req.setAttribute("validTo", to);
        render(req, resp, "right-form");
    }

    private void save(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        int userId = intParam(req, "userId", 0);
        int lockId = intParam(req, "lockId", 0);
        Map<String, String> errors = new LinkedHashMap<>();
        if (userId <= 0 || new UserDAO().findById(userId) == null) {
            errors.put("userId", "Choose a user.");
        }
        SmartLock lock = lockId > 0 ? new LockDao().findById(lockId) : null;
        if (lock == null) {
            errors.put("lockId", "Choose a lock.");
        }
        LocalDateTime from = parse(param(req, "validFrom"));
        LocalDateTime to = param(req, "validTo").isEmpty() ? null : parse(param(req, "validTo"));
        if (from == null) {
            errors.put("validFrom", "Enter a valid start date and time.");
        }
        if (!param(req, "validTo").isEmpty() && to == null) {
            errors.put("validTo", "Enter a valid end date and time, or leave it empty.");
        } else if (from != null && to != null && !to.isAfter(from)) {
            errors.put("validTo", "The end must be after the start.");
        }
        if (!errors.isEmpty()) {
            req.setAttribute("errors", errors);
            showForm(req, resp, String.valueOf(userId), String.valueOf(lockId), param(req, "validFrom"), param(req, "validTo"));
            return;
        }
        new AccessRightDAO().insert(userId, lockId, Timestamp.valueOf(from), to == null ? null : Timestamp.valueOf(to));
        Flash.success(req.getSession(), "Access right added successfully.");
        resp.sendRedirect(req.getContextPath() + "/rights");
    }

    private LocalDateTime parse(String text) {
        try {
            return LocalDateTime.parse(text, INPUT_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

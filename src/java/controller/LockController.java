/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import static controller.BaseController.PAGE_SIZE;
import dao.LockDao;
import dto.SmartLock;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.Flash;
import util.PageResult;
import util.QueryString;

/**
 *
 * @author acer
 */
@WebServlet(name = "LockController", urlPatterns = {"/locks", "/locks/form", "/locks/save", "/locks/delete", "/locks/open"})
public class LockController extends BaseController {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("/locks/form".equals(req.getServletPath())) {
            showForm(req, resp);
        } else if ("/locks".equals(req.getServletPath())) {
            showList(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        switch (req.getServletPath()) {
            case "/locks/save":
                save(req, resp);
                break;
            case "/locks/delete":
                delete(req, resp);
                break;
            case "/locks/open":
                open(req, resp);
                break;
            default:
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void showList(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String keyword = param(req, "keyword");
        String status = param(req, "status");
        if (!"LOCKED".equals(status) && !"UNLOCKED".equals(status)) {
            status = "";
        }
        String sort = param(req, "sort").isEmpty() ? "lock_name" : param(req, "sort");
        boolean asc = !"desc".equals(param(req, "dir"));
        try {
            PageResult<SmartLock> result = new LockDao().search(keyword, status, sort, asc, intParam(req, "page", 1), PAGE_SIZE);
            String baseQuery = QueryString.build("keyword", keyword, "status", status, "sort", sort, "dir", asc ? "asc" : "desc");
            req.setAttribute("pageResult", result);
            req.setAttribute("keyword", keyword);
            req.setAttribute("status", status);
            req.setAttribute("sort", sort);
            req.setAttribute("dir", asc ? "asc" : "desc");
            req.setAttribute("filterQuery", QueryString.build("keyword", keyword, "status", status));
            req.setAttribute("pageUrl", pageUrl(req, "/locks", baseQuery));
            render(req, resp, "locks");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    private void showForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        int id = intParam(req, "id", 0);
        try {
            SmartLock lock = new SmartLock();
            if (id > 0) {
                lock = new LockDao().findById(id);
                if (lock == null) {
                    Flash.error(req.getSession(), "That lock no longer exists.");
                    resp.sendRedirect(req.getContextPath() + "/locks");
                    return;
                }
            }
            req.setAttribute("lock", lock);
            render(req, resp, "lock-form");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    /** Validation runs here again even though the browser also validates. */
    private void save(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        SmartLock lock = new SmartLock();
        lock.setLockId(intParam(req, "lockId", 0));
        lock.setLockName(param(req, "lockName"));
        lock.setLocation(param(req, "location"));

        Map<String, String> errors = new LinkedHashMap<>();
        if (lock.getLockName().isEmpty()) {
            errors.put("lockName", "Lock name is required.");
        } else if (lock.getLockName().length() > 100) {
            errors.put("lockName", "Lock name must be at most 100 characters.");
        }
        if (lock.getLocation().length() > 200) {
            errors.put("location", "Location must be at most 200 characters.");
        }
        if (!errors.isEmpty()) {
            req.setAttribute("lock", lock);
            req.setAttribute("errors", errors);
            render(req, resp, "lock-form");
            return;
        }
        try {
            LockDao dao = new LockDao();
            if (lock.getLockId() > 0) {
                if (dao.update(lock)) {
                    Flash.success(req.getSession(), "Lock updated successfully.");
                } else {
                    Flash.error(req.getSession(), "That lock no longer exists.");
                }
            } else {
                dao.insert(lock);
                Flash.success(req.getSession(), "Lock added successfully.");
            }
            resp.sendRedirect(req.getContextPath() + "/locks");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    private void delete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            if (new LockDao().delete(intParam(req, "lockId", 0))) {
                Flash.success(req.getSession(), "Lock deleted successfully.");
            } else {
                Flash.error(req.getSession(), "That lock no longer exists.");
            }
            resp.sendRedirect(req.getContextPath() + "/locks");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    /** Remote open from the web: needs a valid access right, is written to the entry log, and the ESP32 picks it up. */
    private void open(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            switch (new LockDao().remoteOpen(intParam(req, "lockId", 0), loginUser(req))) {
                case "SUCCESS":
                    Flash.success(req.getSession(), "Open command sent. The door will unlock in a few seconds.");
                    break;
                case "DENIED":
                    Flash.error(req.getSession(), "You do not have a valid access right for this lock.");
                    break;
                default:
                    Flash.error(req.getSession(), "That lock no longer exists.");
            }
            resp.sendRedirect(req.getContextPath() + "/locks");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }
}
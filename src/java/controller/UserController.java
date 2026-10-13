/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import static controller.BaseController.PAGE_SIZE;
import dao.UserDAO;
import dto.User;
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
 * @author User
 */
@WebServlet(name = "UserController", urlPatterns = {"/users", "/users/form", "/users/save", "/users/delete"})
public class UserController extends BaseController {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            if ("/users/form".equals(req.getServletPath())) {
                User user = new User();
                int id = intParam(req, "id", 0);
                if (id > 0) {
                    user = new UserDAO().findById(id);
                    if (user == null) {
                        Flash.error(req.getSession(), "That user no longer exists.");
                        resp.sendRedirect(req.getContextPath() + "/users");
                        return;
                    }
                }
                req.setAttribute("form", user);
                render(req, resp, "user-form");
            } else if ("/users".equals(req.getServletPath())) {
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
            if ("/users/save".equals(req.getServletPath())) {
                save(req, resp);
            } else if ("/users/delete".equals(req.getServletPath())) {
                int id = intParam(req, "userId", 0);
                if (id == loginUser(req).getUserId()) {
                    Flash.error(req.getSession(), "You cannot disable your own account.");
                } else if (new UserDAO().disable(id)) {
                    Flash.success(req.getSession(), "User disabled successfully.");
                } else {
                    Flash.error(req.getSession(), "That user does not exist or is already disabled.");
                }
                resp.sendRedirect(req.getContextPath() + "/users");
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }

    private void showList(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String keyword = param(req, "keyword");
        String role = param(req, "role");
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            role = "";
        }
        String sort = param(req, "sort").isEmpty() ? "username" : param(req, "sort");
        boolean asc = !"desc".equals(param(req, "dir"));
        PageResult<User> result = new UserDAO().search(keyword, role, sort, asc, intParam(req, "page", 1), PAGE_SIZE);
        req.setAttribute("pageResult", result);
        req.setAttribute("keyword", keyword);
        req.setAttribute("role", role);
        req.setAttribute("sort", sort);
        req.setAttribute("dir", asc ? "asc" : "desc");
        req.setAttribute("filterQuery", QueryString.build("keyword", keyword, "role", role));
        req.setAttribute("pageUrl", pageUrl(req, "/users",
                QueryString.build("keyword", keyword, "role", role, "sort", sort, "dir", asc ? "asc" : "desc")));
        render(req, resp, "users");
    }

    private void save(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        User user = new User();
        user.setUserId(intParam(req, "userId", 0));
        user.setUsername(param(req, "username"));
        user.setFullName(param(req, "fullName"));
        user.setRole(param(req, "role"));
        user.setActive(req.getParameter("active") != null);
        String password = req.getParameter("password") == null ? "" : req.getParameter("password");
        boolean creating = user.getUserId() == 0;

        Map<String, String> errors = new LinkedHashMap<>();
        UserDAO dao = new UserDAO();
        if (creating) {
            if (!user.getUsername().matches("[A-Za-z0-9_]{3,50}")) {
                errors.put("username", "Username must be 3-50 letters, digits or underscores.");
            } else if (dao.existsUsername(user.getUsername())) {
                errors.put("username", "This username is already taken.");
            }
        } else {
            User existing = dao.findById(user.getUserId());
            if (existing == null) {
                Flash.error(req.getSession(), "That user no longer exists.");
                resp.sendRedirect(req.getContextPath() + "/users");
                return;
            }
            user.setUsername(existing.getUsername());
        }
        if (user.getFullName().isEmpty() || user.getFullName().length() > 100) {
            errors.put("fullName", "Full name is required (at most 100 characters).");
        }
        if (!"ADMIN".equals(user.getRole()) && !"USER".equals(user.getRole())) {
            errors.put("role", "Choose a role.");
        }
        if ((creating || !password.isEmpty()) && (password.length() < 6 || password.length() > 100)) {
            errors.put("password", "Password must be 6-100 characters.");
        }
        if (user.getUserId() == loginUser(req).getUserId() && (!user.isAdmin() || !user.isActive())) {
            errors.put("role", "You cannot remove your own admin access or disable yourself.");
        }
        if (!errors.isEmpty()) {
            req.setAttribute("form", user);
            req.setAttribute("errors", errors);
            render(req, resp, "user-form");
            return;
        }
        if (creating) {
            dao.insert(user, password);
            Flash.success(req.getSession(), "User added successfully.");
        } else {
            dao.update(user, password);
            Flash.success(req.getSession(), "User updated successfully.");
        }
        resp.sendRedirect(req.getContextPath() + "/users");
    }
}

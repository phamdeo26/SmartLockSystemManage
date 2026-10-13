/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;
import dao.UserDAO;
import dto.User;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
/**
 *
 * @author User
 */
@WebServlet(name = "RegisterController", urlPatterns = {"/register"})
public class RegisterController extends BaseController {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        render(req, resp, "register");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = new User();
        user.setUsername(param(req, "username"));
        user.setFullName(param(req, "fullName"));
        user.setRole("USER");
        String password = req.getParameter("password") == null ? "" : req.getParameter("password");
        String confirm = req.getParameter("confirmPassword") == null ? "" : req.getParameter("confirmPassword");

        Map<String, String> errors = new LinkedHashMap<>();
        if (!user.getUsername().matches("[A-Za-z0-9_]{3,50}")) {
            errors.put("username", "Username must be 3-50 letters, digits or underscores.");
        }
        if (user.getFullName().isEmpty() || user.getFullName().length() > 100) {
            errors.put("fullName", "Full name is required (at most 100 characters).");
        }
        if (password.length() < 6 || password.length() > 100) {
            errors.put("password", "Password must be 6-100 characters.");
        } else if (!password.equals(confirm)) {
            errors.put("confirmPassword", "The two passwords do not match.");
        }
        try {
            UserDAO dao = new UserDAO();
            if (errors.isEmpty() && dao.existsUsername(user.getUsername())) {
                errors.put("username", "This username is already taken.");
            }
            if (!errors.isEmpty()) {
                req.setAttribute("form", user);
                req.setAttribute("errors", errors);
                render(req, resp, "register");
                return;
            }
            dao.insert(user, password);
            resp.sendRedirect(req.getContextPath() + "/login?registered=1");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }
}
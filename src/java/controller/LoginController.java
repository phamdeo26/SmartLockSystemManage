/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package controller;


import dao.UserDAO;
import dto.User;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import util.PasswordUtil;
/**
 *
 * @author User
 */

@WebServlet(name = "LoginController", urlPatterns = {"/login"})
public class LoginController extends BaseController {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getSession(false) != null && req.getSession().getAttribute("loginUser") != null) {
            resp.sendRedirect(req.getContextPath() + "/dashboard");
            return;
        }
        if ("1".equals(req.getParameter("expired"))) {
            req.setAttribute("info", "Your session has expired. Please sign in again.");
        } else if ("1".equals(req.getParameter("loggedOut"))) {
            req.setAttribute("info", "You have been signed out.");
        } else if ("1".equals(req.getParameter("registered"))) {
            req.setAttribute("info", "Account created. You can sign in now.");
        }
        render(req, resp, "login");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = param(req, "username");
        String password = req.getParameter("password") == null ? "" : req.getParameter("password");
        if (username.isEmpty() || password.isEmpty()) {
            req.setAttribute("error", "Please enter both username and password.");
            req.setAttribute("username", username);
            render(req, resp, "login");
            return;
        }
        try {
            User user = new UserDAO().authenticate(username, password);
            if (user == null) {
                req.setAttribute("error", "Invalid username or password.");
                req.setAttribute("username", username);
                render(req, resp, "login");
                return;
            }
            req.getSession().invalidate();
            req.getSession(true).setAttribute("loginUser", user);
            resp.sendRedirect(req.getContextPath() + "/dashboard");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }
}

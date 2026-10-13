/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;
import dao.AccessLogDAO;

import dao.CredentialDAO;
import dao.LockDao;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author acer
 */
@WebServlet(name = "DashboardController", urlPatterns = {"", "/dashboard"})
public class DashboardController extends BaseController {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            int[] stats = new LockDao().stats();
            req.setAttribute("totalLocks", stats[0]);
            req.setAttribute("unlockedLocks", stats[1]);
            if (loginUser(req).isAdmin()) {
                req.setAttribute("deniedCount", new AccessLogDAO().countDeniedLast24Hours());
                req.setAttribute("pendingCredentials", new CredentialDAO().countPending());
            }
            render(req, resp, "dashboard");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }
}

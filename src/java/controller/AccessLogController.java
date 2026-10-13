package controller;

import static controller.BaseController.PAGE_SIZE;
import dao.AccessLogDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.QueryString;

@WebServlet(name = "AccessLogController", urlPatterns = {"/logs"})
public class AccessLogController extends BaseController {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = param(req, "method");
        if (!method.equals("WEB") && !method.equals("PIN") && !method.equals("FINGERPRINT") && !method.equals("CARD")) {
            method = "";
        }
        String result = param(req, "result");
        if (!"SUCCESS".equals(result) && !"DENIED".equals(result)) {
            result = "";
        }
        try {
            int userId = loginUser(req).isAdmin() ? 0 : loginUser(req).getUserId();
            req.setAttribute("pageResult", new AccessLogDAO().search(userId, method, result, intParam(req, "page", 1), PAGE_SIZE));
            req.setAttribute("method", method);
            req.setAttribute("result", result);
            req.setAttribute("pageUrl", pageUrl(req, "/logs", QueryString.build("method", method, "result", result)));
            render(req, resp, "logs");
        } catch (Exception e) {
            fail(req, resp, e);
        }
    }
}
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="dto.User, util.Html" %>
<%@ page import="dto.AccessLog, util.PageResult" %>
<% String pageTitle = "Access Log"; %>
<%
    String ctx = request.getContextPath();
    User loginUser = (User) session.getAttribute("loginUser");
    String navPath = (String) request.getAttribute("javax.servlet.forward.servlet_path");
    if (navPath == null) {
        navPath = request.getServletPath();
    }
    boolean navDash = navPath.isEmpty() || navPath.startsWith("/dashboard");
    boolean navLocks = navPath.startsWith("/locks");
    boolean navLogs = navPath.startsWith("/logs");
    boolean navCred = navPath.startsWith("/credentials");
    boolean navRights = navPath.startsWith("/rights");
    boolean navUsers = navPath.startsWith("/users");
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%= Html.esc(pageTitle) %> - CyberLock OS</title>
    <link rel="stylesheet" href="<%= ctx %>/css/style.css">
</head>
<body>
<% if (loginUser != null) { %>
<div class="app">
    <aside class="sidebar">
        <div class="brand">
            <span class="brand-logo"><%= Html.icon("lock") %></span>
            <div><div class="brand-name">CyberLock OS</div><div class="brand-sub">SMART LOCK SYSTEM</div></div>
        </div>
        <nav class="side-nav">
            <a class="<%= Html.active(navDash) %>" href="<%= ctx %>/dashboard"><%= Html.icon("dashboard") %> Dashboard</a>
            <a class="<%= Html.active(navLocks) %>" href="<%= ctx %>/locks"><%= Html.icon("lock") %> Locks</a>
            <a class="<%= Html.active(navLogs) %>" href="<%= ctx %>/logs"><%= Html.icon("list") %> Access Log</a>
            <a class="<%= Html.active(navCred) %>" href="<%= ctx %>/credentials"><%= Html.icon("key") %> PIN / Card / Finger</a>
            <% if (loginUser.isAdmin()) { %>
                <a class="<%= Html.active(navRights) %>" href="<%= ctx %>/rights"><%= Html.icon("shield") %> Access Rights</a>
                <a class="<%= Html.active(navUsers) %>" href="<%= ctx %>/users"><%= Html.icon("users") %> Users</a>
            <% } %>
        </nav>
    </aside>
    <div class="content">
        <header class="topbar">
            <span class="status-pill"><span class="dot"></span> ONLINE</span>
            <span class="clock"><%= Html.icon("clock") %> <span id="utc-clock">--:--:-- UTC</span></span>
            <div class="top-right">
                <div class="user-chip">
                    <span class="avatar"><%= Html.initials(loginUser.getFullName()) %></span>
                    <div><div class="user-name"><%= Html.esc(loginUser.getFullName()) %></div><div class="user-role"><%= Html.esc(loginUser.getRole()) %></div></div>
                </div>
                <a class="icon-btn" href="<%= ctx %>/logout" title="Sign out"><%= Html.icon("logout") %></a>
            </div>
        </header>
        <main class="main">
<% } else { %>
<div class="auth-shell">
    <header class="auth-top">
        <span class="brand-logo"><%= Html.icon("lock") %></span>
        <div><div class="brand-name">CyberLock OS</div><div class="brand-sub">SMART LOCK SYSTEM</div></div>
    </header>
    <main class="auth-main">
<% } %>
<%
    String flashSuccess = (String) session.getAttribute("flashSuccess");
    if (flashSuccess != null) {
        session.removeAttribute("flashSuccess");
%>
    <div class="alert alert-success" role="status"><%= Html.esc(flashSuccess) %></div>
<%
    }
    String flashError = (String) session.getAttribute("flashError");
    if (flashError != null) {
        session.removeAttribute("flashError");
%>
    <div class="alert alert-error" role="alert"><%= Html.esc(flashError) %></div>
<% } %>
<%
    PageResult<AccessLog> pageResult = (PageResult<AccessLog>) request.getAttribute("pageResult");
    String method = (String) request.getAttribute("method");
    String result = (String) request.getAttribute("result");
%>
<h1>Access Log</h1>
<p class="muted">Who opened which door, when and how. The log is append-only: nobody can edit or delete it.
<% if (!loginUser.isAdmin()) { %>You can only see your own entries.<% } %></p>
<form method="get" action="<%= ctx %>/logs" class="toolbar">
    <div class="field">
        <label for="method">Method</label>
        <select id="method" name="method">
            <option value="">All</option>
            <option value="WEB" <%= Html.selected("WEB".equals(method)) %>>Web (remote)</option>
            <option value="PIN" <%= Html.selected("PIN".equals(method)) %>>PIN</option>
            <option value="FINGERPRINT" <%= Html.selected("FINGERPRINT".equals(method)) %>>Fingerprint</option>
            <option value="CARD" <%= Html.selected("CARD".equals(method)) %>>Card</option>
        </select>
    </div>
    <div class="field">
        <label for="result">Result</label>
        <select id="result" name="result">
            <option value="">All</option>
            <option value="SUCCESS" <%= Html.selected("SUCCESS".equals(result)) %>>Success</option>
            <option value="DENIED" <%= Html.selected("DENIED".equals(result)) %>>Denied</option>
        </select>
    </div>
    <button type="submit" class="btn">Filter</button>
    <a class="btn btn-secondary" href="<%= ctx %>/logs">Reset</a>
</form>
<% if (pageResult.getItems().isEmpty()) { %>
<div class="card empty">No log entries found.</div>
<% } else { %>
<table>
    <thead><tr><th>Time</th><th>Lock</th><th>Who</th><th>Method</th><th>Result</th><th>Detail</th></tr></thead>
    <tbody>
    <% for (AccessLog a : pageResult.getItems()) { %>
        <tr>
            <td data-label="Time"><%= Html.date(a.getLogTime(), "yyyy-MM-dd HH:mm:ss") %></td>
            <td data-label="Lock"><%= Html.esc(a.getLockName()) %></td>
            <td data-label="Who"><% if (a.getUsername() == null) { %><span class="who unknown">Unknown</span><% } else { %><span class="who"><%= Html.esc(a.getUsername()) %></span><% } %></td>
            <td data-label="Method"><span class="chip chip-<%= a.getMethod().toLowerCase() %>"><%= Html.icon(Html.methodIcon(a.getMethod())) %> <%= Html.esc(a.getMethod()) %></span></td>
            <td data-label="Result"><span class="badge <%= "SUCCESS".equals(a.getResult()) ? "badge-success" : "badge-failed" %>"><%= "SUCCESS".equals(a.getResult()) ? "Opened" : "Denied" %></span></td>
            <td data-label="Detail"><%= a.getDetail() == null ? "-" : Html.esc(a.getDetail()) %></td>
        </tr>
    <% } %>
    </tbody>
</table>
<%
    util.PageResult<?> pgResult = (util.PageResult<?>) request.getAttribute("pageResult");
    String pgUrl = (String) request.getAttribute("pageUrl");
    int pgCurrent = pgResult.getPage();
    int pgLast = pgResult.getTotalPages();
%>
<p class="muted">Page <%= pgCurrent %> of <%= pgLast %> (<%= pgResult.getTotal() %> rows)</p>
<% if (pgLast > 1) { %>
    <nav class="pagination" aria-label="Pagination">
        <% if (pgCurrent > 1) { %><a href="<%= Html.esc(pgUrl) %><%= pgCurrent - 1 %>">Previous</a><% } %>
        <% for (int pgNo = 1; pgNo <= pgLast; pgNo++) {
               if (pgNo == 1 || pgNo == pgLast || Math.abs(pgNo - pgCurrent) <= 2) { %>
            <a href="<%= Html.esc(pgUrl) %><%= pgNo %>" class="<%= pgNo == pgCurrent ? "current" : "" %>"><%= pgNo %></a>
        <%     }
           } %>
        <% if (pgCurrent < pgLast) { %><a href="<%= Html.esc(pgUrl) %><%= pgCurrent + 1 %>">Next</a><% } %>
    </nav>
<% } %>
<% } %>
<% if (loginUser != null) { %>
        </main>
    </div>
</div>
<% } else { %>
    </main>
    <footer class="auth-foot">CyberLock OS - Smart Lock System</footer>
</div>
<% } %>
<script>
(function () {
    var el = document.getElementById('utc-clock');
    if (!el) { return; }
    function tick() { el.textContent = new Date().toISOString().substr(11, 8) + ' UTC'; }
    tick();
    setInterval(tick, 1000);
})();
</script>
</body>
</html>

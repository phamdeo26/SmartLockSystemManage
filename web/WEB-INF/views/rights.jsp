<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="dto.User, util.Html" %>
<%@ page import="dto.AccessRight, util.PageResult" %>
<% String pageTitle = "Access rights"; %>
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
    PageResult<AccessRight> pageResult = (PageResult<AccessRight>) request.getAttribute("pageResult");
    String keyword = (String) request.getAttribute("keyword");
%>
<h1>Access rights</h1>
<p class="muted">An access right lets one user operate one lock during a time window. Users without a valid access right are denied.</p>
<form method="get" action="<%= ctx %>/rights" class="toolbar">
    <div class="field">
        <label for="keyword">Search</label>
        <input type="text" id="keyword" name="keyword" value="<%= Html.esc(keyword) %>" placeholder="User or lock" maxlength="100">
    </div>
    <button type="submit" class="btn">Search</button>
    <a class="btn btn-secondary" href="<%= ctx %>/rights">Reset</a>
    <a class="btn" href="<%= ctx %>/rights/form">Add access right</a>
</form>
<% if (pageResult.getItems().isEmpty()) { %>
<div class="card empty">No access rights found.</div>
<% } else { %>
<table>
    <thead><tr><th>User</th><th>Lock</th><th>Valid from</th><th>Valid to</th><th>Status</th><th>Actions</th></tr></thead>
    <tbody>
    <% for (AccessRight a : pageResult.getItems()) {
           boolean active = "ACTIVE".equals(a.getStatus());
           String label = active ? "Active" : ("EXPIRED".equals(a.getStatus()) ? "Expired" : "Not started"); %>
        <tr>
            <td data-label="User"><%= Html.esc(a.getUsername()) %></td>
            <td data-label="Lock"><%= Html.esc(a.getLockName()) %></td>
            <td data-label="Valid from"><%= Html.date(a.getValidFrom(), "yyyy-MM-dd HH:mm") %></td>
            <td data-label="Valid to"><%= a.getValidTo() == null ? "No expiry" : Html.date(a.getValidTo(), "yyyy-MM-dd HH:mm") %></td>
            <td data-label="Status"><span class="badge <%= active ? "badge-success" : "badge-denied" %>"><%= label %></span></td>
            <td data-label="Actions">
                <form method="post" action="<%= ctx %>/rights/delete" class="inline"
                      onsubmit="return confirm('Remove this access right?');">
                    <input type="hidden" name="rightId" value="<%= a.getRightId() %>">
                    <button type="submit" class="btn btn-small btn-danger">Remove</button>
                </form>
            </td>
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

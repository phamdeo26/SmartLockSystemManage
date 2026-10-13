<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="dto.User, util.Html" %>
<%@ page import="util.PageResult" %>
<% String pageTitle = "Users"; %>
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
    PageResult<User> pageResult = (PageResult<User>) request.getAttribute("pageResult");
    String keyword = (String) request.getAttribute("keyword");
    String role = (String) request.getAttribute("role");
    String sort = (String) request.getAttribute("sort");
    String dir = (String) request.getAttribute("dir");
    String filterQuery = (String) request.getAttribute("filterQuery");
    String base = ctx + "/users";
%>
<h1>Users</h1>
<form method="get" action="<%= ctx %>/users" class="toolbar">
    <div class="field">
        <label for="keyword">Search</label>
        <input type="text" id="keyword" name="keyword" value="<%= Html.esc(keyword) %>" placeholder="Username or full name" maxlength="100">
    </div>
    <div class="field">
        <label for="role">Role</label>
        <select id="role" name="role">
            <option value="">All</option>
            <option value="ADMIN" <%= Html.selected("ADMIN".equals(role)) %>>Admin</option>
            <option value="USER" <%= Html.selected("USER".equals(role)) %>>User</option>
        </select>
    </div>
    <input type="hidden" name="sort" value="<%= Html.esc(sort) %>">
    <input type="hidden" name="dir" value="<%= Html.esc(dir) %>">
    <button type="submit" class="btn">Search</button>
    <a class="btn btn-secondary" href="<%= ctx %>/users">Reset</a>
    <a class="btn" href="<%= ctx %>/users/form"><%= Html.icon("plus") %> Add user</a>
</form>
<% if (pageResult.getItems().isEmpty()) { %>
<div class="card empty">No users match your search.</div>
<% } else { %>
<table>
    <thead>
        <tr>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "username")) %>">Username</a></th>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "full_name")) %>">Full name</a></th>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "role")) %>">Role</a></th>
            <th>Status</th>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "created_at")) %>">Created</a></th>
            <th>Actions</th>
        </tr>
    </thead>
    <tbody>
    <% for (User u : pageResult.getItems()) { %>
        <tr>
            <td data-label="Username"><span class="name-cell"><span class="avatar sm"><%= Html.initials(u.getFullName()) %></span><%= Html.esc(u.getUsername()) %></span></td>
            <td data-label="Full name"><%= Html.esc(u.getFullName()) %></td>
            <td data-label="Role"><%= u.isAdmin() ? "Admin" : "User" %></td>
            <td data-label="Status"><span class="badge <%= u.isActive() ? "badge-success" : "badge-failed" %>"><%= u.isActive() ? "Active" : "Disabled" %></span></td>
            <td data-label="Created"><%= Html.date(u.getCreatedAt(), "yyyy-MM-dd") %></td>
            <td data-label="Actions">
                <div class="actions">
                    <a class="icon-btn" title="Edit" href="<%= ctx %>/users/form?id=<%= u.getUserId() %>"><%= Html.icon("edit") %></a>
                    <% if (u.isActive() && u.getUserId() != loginUser.getUserId()) { %>
                        <form method="post" action="<%= ctx %>/users/delete" class="inline"
                              onsubmit="return confirm('Disable this user? They will no longer be able to sign in.');">
                            <input type="hidden" name="userId" value="<%= u.getUserId() %>">
                            <button type="submit" class="btn btn-small btn-danger">Disable</button>
                        </form>
                    <% } %>
                </div>
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

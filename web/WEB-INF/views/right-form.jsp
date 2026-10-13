<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="dto.User, util.Html" %>
<%@ page import="dto.SmartLock, java.util.List, java.util.Map" %>
<% String pageTitle = "Add access right"; %>
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
    List<User> users = (List<User>) request.getAttribute("users");
    List<SmartLock> locks = (List<SmartLock>) request.getAttribute("locks");
    Map<String, String> errors = (Map<String, String>) request.getAttribute("errors");
    String userId = (String) request.getAttribute("userId");
    String lockId = (String) request.getAttribute("lockId");
    String validFrom = (String) request.getAttribute("validFrom");
    String validTo = (String) request.getAttribute("validTo");
%>
<h1>Add access right</h1>
<div class="card">
    <% if (errors != null && !errors.isEmpty()) { %><div class="alert alert-error" role="alert">Please fix the highlighted fields.</div><% } %>
    <form method="post" action="<%= ctx %>/rights/save" class="form-narrow">
        <div class="field">
            <label for="userId">User</label>
            <select id="userId" name="userId" required>
                <option value="">Choose a user</option>
                <% for (User u : users) { %>
                    <option value="<%= u.getUserId() %>" <%= Html.selected(String.valueOf(u.getUserId()).equals(userId)) %>><%= Html.esc(u.getUsername()) %> - <%= Html.esc(u.getFullName()) %></option>
                <% } %>
            </select>
            <span class="field-error"><%= Html.err(errors, "userId") %></span>
        </div>
        <div class="field">
            <label for="lockId">Lock</label>
            <select id="lockId" name="lockId" required>
                <option value="">Choose a lock</option>
                <% for (SmartLock l : locks) { %>
                    <option value="<%= l.getLockId() %>" <%= Html.selected(String.valueOf(l.getLockId()).equals(lockId)) %>><%= Html.esc(l.getLockName()) %></option>
                <% } %>
            </select>
            <span class="field-error"><%= Html.err(errors, "lockId") %></span>
        </div>
        <div class="field">
            <label for="validFrom">Valid from</label>
            <input type="datetime-local" id="validFrom" name="validFrom" value="<%= Html.esc(validFrom) %>" required>
            <span class="field-error"><%= Html.err(errors, "validFrom") %></span>
        </div>
        <div class="field">
            <label for="validTo">Valid to (leave empty for no expiry)</label>
            <input type="datetime-local" id="validTo" name="validTo" value="<%= Html.esc(validTo) %>">
            <span class="field-error"><%= Html.err(errors, "validTo") %></span>
        </div>
        <div class="actions">
            <button type="submit" class="btn">Save</button>
            <a class="btn btn-secondary" href="<%= ctx %>/rights">Cancel</a>
        </div>
    </form>
</div>
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

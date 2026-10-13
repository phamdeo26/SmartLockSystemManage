<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="dto.User, util.Html" %>
<% String pageTitle = "Dashboard"; %>
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
<h1>Dashboard</h1>
<p class="muted">Welcome back, <strong><%= Html.esc(loginUser.getFullName()) %></strong>. Every door, credential and log entry is managed from here.</p>
<div class="stats">
    <div class="stat">
        <div class="stat-head"><span class="stat-label">Total locks</span><span class="stat-icon"><%= Html.icon("lock") %></span></div>
        <div class="stat-number"><%= request.getAttribute("totalLocks") %></div>
    </div>
    <div class="stat">
        <div class="stat-head"><span class="stat-label">Currently unlocked</span><span class="stat-icon warn"><%= Html.icon("unlock") %></span></div>
        <div class="stat-number"><%= request.getAttribute("unlockedLocks") %></div>
    </div>
    <% if (loginUser.isAdmin()) { %>
    <div class="stat">
        <div class="stat-head"><span class="stat-label">Requests waiting</span><span class="stat-icon ok"><%= Html.icon("inbox") %></span></div>
        <div class="stat-number"><%= request.getAttribute("pendingCredentials") %></div>
    </div>
    <div class="stat">
        <div class="stat-head"><span class="stat-label">Denied (24 hours)</span><span class="stat-icon bad"><%= Html.icon("shield") %></span></div>
        <div class="stat-number"><%= request.getAttribute("deniedCount") %></div>
    </div>
    <% } %>
</div>
<h2 class="section-title">Quick access</h2>
<div class="tiles">
    <a class="tile" href="<%= ctx %>/locks"><span class="tile-icon"><%= Html.icon("lock") %></span>Manage locks</a>
    <a class="tile" href="<%= ctx %>/logs"><span class="tile-icon"><%= Html.icon("list") %></span>Access log</a>
    <a class="tile" href="<%= ctx %>/credentials"><span class="tile-icon"><%= Html.icon("key") %></span>PIN / Card / Finger</a>
    <% if (loginUser.isAdmin()) { %>
    <a class="tile" href="<%= ctx %>/rights"><span class="tile-icon"><%= Html.icon("shield") %></span>Access rights</a>
    <a class="tile" href="<%= ctx %>/users"><span class="tile-icon"><%= Html.icon("users") %></span>Users</a>
    <a class="tile" href="<%= ctx %>/locks/form"><span class="tile-icon"><%= Html.icon("plus") %></span>Add new lock</a>
    <% } %>
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

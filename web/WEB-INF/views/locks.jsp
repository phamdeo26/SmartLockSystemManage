<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="dto.User, util.Html" %>
<%@ page import="dto.SmartLock, util.PageResult" %>
<% String pageTitle = "Locks"; %>
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
    PageResult<SmartLock> pageResult = (PageResult<SmartLock>) request.getAttribute("pageResult");
    String keyword = (String) request.getAttribute("keyword");
    String status = (String) request.getAttribute("status");
    String sort = (String) request.getAttribute("sort");
    String dir = (String) request.getAttribute("dir");
    String filterQuery = (String) request.getAttribute("filterQuery");
    String base = ctx + "/locks";
%>
<h1>Locks</h1>
<form method="get" action="<%= ctx %>/locks" class="toolbar">
    <div class="field">
        <label for="keyword">Search</label>
        <input type="text" id="keyword" name="keyword" value="<%= Html.esc(keyword) %>" placeholder="Name or location" maxlength="100">
    </div>
    <div class="field">
        <label for="status">Status</label>
        <select id="status" name="status">
            <option value="">All</option>
            <option value="LOCKED" <%= Html.selected("LOCKED".equals(status)) %>>Locked</option>
            <option value="UNLOCKED" <%= Html.selected("UNLOCKED".equals(status)) %>>Unlocked</option>
        </select>
    </div>
    <input type="hidden" name="sort" value="<%= Html.esc(sort) %>">
    <input type="hidden" name="dir" value="<%= Html.esc(dir) %>">
    <button type="submit" class="btn">Search</button>
    <a class="btn btn-secondary" href="<%= ctx %>/locks">Reset</a>
    <% if (loginUser.isAdmin()) { %><a class="btn" href="<%= ctx %>/locks/form"><%= Html.icon("plus") %> Add lock</a><% } %>
</form>
<% if (pageResult.getItems().isEmpty()) { %>
<div class="card empty">No locks match your search.</div>
<% } else { %>
<table>
    <thead>
        <tr>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "lock_name")) %>">Name</a></th>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "location")) %>">Location</a></th>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "status")) %>">Status</a></th>
            <th><a href="<%= Html.esc(Html.sortHref(base, filterQuery, sort, dir, "created_at")) %>">Created</a></th>
            <th>Actions</th>
        </tr>
    </thead>
    <tbody>
    <% for (SmartLock l : pageResult.getItems()) {
           boolean locked = "LOCKED".equals(l.getStatus()); %>
        <tr>
            <td data-label="Name"><span class="name-cell"><span class="row-icon"><%= Html.icon("lock") %></span><%= Html.esc(l.getLockName()) %></span></td>
            <td data-label="Location"><%= Html.esc(l.getLocation()) %></td>
            <td data-label="Status"><span class="badge <%= locked ? "badge-locked" : "badge-unlocked" %>"><%= locked ? "Locked" : "Unlocked" %></span></td>
            <td data-label="Created"><%= Html.date(l.getCreatedAt(), "yyyy-MM-dd") %></td>
            <td data-label="Actions">
                <div class="actions">
                    <form method="post" action="<%= ctx %>/locks/open" class="inline"
                          onsubmit="return confirm('Open this door remotely?');">
                        <input type="hidden" name="lockId" value="<%= l.getLockId() %>">
                        <button type="submit" class="btn btn-small btn-outline"><%= Html.icon("unlock") %> Open remotely</button>
                    </form>
                    <% if (loginUser.isAdmin()) { %>
                        <a class="icon-btn" title="Edit" href="<%= ctx %>/locks/form?id=<%= l.getLockId() %>"><%= Html.icon("edit") %></a>
                        <form method="post" action="<%= ctx %>/locks/delete" class="inline"
                              onsubmit="return confirm('Delete this lock? This cannot be undone.');">
                            <input type="hidden" name="lockId" value="<%= l.getLockId() %>">
                            <button type="submit" class="icon-btn danger" title="Delete"><%= Html.icon("trash") %></button>
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

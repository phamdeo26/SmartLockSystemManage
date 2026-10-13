<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="dto.User, util.Html" %>
<%@ page import="dto.AccessCredential, util.PageResult" %>
<% String pageTitle = "PIN / Card / Fingerprint"; %>
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
    PageResult<AccessCredential> pageResult = (PageResult<AccessCredential>) request.getAttribute("pageResult");
    String type = (String) request.getAttribute("type");
    String status = (String) request.getAttribute("status");
    String keyword = (String) request.getAttribute("keyword");
%>
<h1>PIN / Card / Fingerprint</h1>
<% if (loginUser.isAdmin()) { %>
    <p class="muted">Only approved entries can open a door. Pending, rejected and revoked entries never do.</p>
<% } else { %>
    <p class="muted">You can ask for a PIN, but it cannot open the door until an admin approves it. Cards and fingerprints are registered by an admin.</p>
<% } %>
<% Integer pendingCount = (Integer) request.getAttribute("pendingCount");
   if (pendingCount != null && pendingCount > 0) { %>
<div class="notice">
    <span class="notice-icon"><%= Html.icon("alert") %></span>
    <div class="grow"><strong><%= pendingCount %> request(s) waiting for approval</strong>
        <p class="muted">Pending requests can never open a door until you approve them.</p></div>
    <a class="btn btn-small" href="<%= ctx %>/credentials?status=PENDING">Show pending</a>
</div>
<% } %>
<form method="get" action="<%= ctx %>/credentials" class="toolbar">
    <div class="field">
        <label for="keyword">Search</label>
        <input type="text" id="keyword" name="keyword" value="<%= Html.esc(keyword) %>" placeholder="<%= loginUser.isAdmin() ? "User, lock or label" : "Lock or label" %>" maxlength="100">
    </div>
    <div class="field">
        <label for="type">Type</label>
        <select id="type" name="type">
            <option value="">All</option>
            <option value="PIN" <%= Html.selected("PIN".equals(type)) %>>PIN</option>
            <option value="FINGERPRINT" <%= Html.selected("FINGERPRINT".equals(type)) %>>Fingerprint</option>
            <option value="CARD" <%= Html.selected("CARD".equals(type)) %>>Card</option>
        </select>
    </div>
    <div class="field">
        <label for="status">Status</label>
        <select id="status" name="status">
            <option value="">All</option>
            <option value="PENDING" <%= Html.selected("PENDING".equals(status)) %>>Pending</option>
            <option value="APPROVED" <%= Html.selected("APPROVED".equals(status)) %>>Approved</option>
            <option value="REJECTED" <%= Html.selected("REJECTED".equals(status)) %>>Rejected</option>
            <option value="REVOKED" <%= Html.selected("REVOKED".equals(status)) %>>Revoked</option>
        </select>
    </div>
    <button type="submit" class="btn">Filter</button>
    <a class="btn btn-secondary" href="<%= ctx %>/credentials">Reset</a>
    <a class="btn" href="<%= ctx %>/credentials/form"><%= loginUser.isAdmin() ? "Register" : "Request a PIN" %></a>
</form>
<% if (pageResult.getItems().isEmpty()) { %>
<div class="card empty">Nothing found.</div>
<% } else { %>
<table>
    <thead>
        <tr>
            <% if (loginUser.isAdmin()) { %><th>User</th><% } %>
            <th>Lock</th><th>Type</th><th>Label</th><th>Requested</th><th>Status</th><th>Decided by</th><th>Decided at</th>
            <% if (loginUser.isAdmin()) { %><th>Actions</th><% } %>
        </tr>
    </thead>
    <tbody>
    <% for (AccessCredential r : pageResult.getItems()) {
           String st = r.getStatus();
           String badge = "APPROVED".equals(st) ? "badge-success" : ("PENDING".equals(st) ? "badge-denied" : "badge-failed");
           String label = "APPROVED".equals(st) ? "Approved" : ("PENDING".equals(st) ? "Pending" : ("REJECTED".equals(st) ? "Rejected" : "Revoked"));
           String typeLabel = "PIN".equals(r.getType()) ? "PIN" : ("CARD".equals(r.getType()) ? "Card" : "Fingerprint"); %>
        <tr>
            <% if (loginUser.isAdmin()) { %><td data-label="User"><%= Html.esc(r.getUsername()) %></td><% } %>
            <td data-label="Lock"><%= Html.esc(r.getLockName()) %></td>
            <td data-label="Type"><span class="chip chip-<%= r.getType().toLowerCase() %>"><%= Html.icon(Html.methodIcon(r.getType())) %> <%= typeLabel %></span></td>
            <td data-label="Label"><%= r.getLabel() == null ? "-" : Html.esc(r.getLabel()) %></td>
            <td data-label="Requested"><%= Html.date(r.getRequestedAt(), "yyyy-MM-dd HH:mm") %></td>
            <td data-label="Status"><span class="badge <%= badge %>"><%= label %></span></td>
            <td data-label="Decided by"><%= r.getDecidedBy() == null ? "-" : Html.esc(r.getDecidedBy()) %></td>
            <td data-label="Decided at"><%= r.getDecidedAt() == null ? "-" : Html.date(r.getDecidedAt(), "yyyy-MM-dd HH:mm") %></td>
            <% if (loginUser.isAdmin()) { %>
            <td data-label="Actions">
                <div class="actions">
                    <% if ("PENDING".equals(st)) { %>
                        <form method="post" action="<%= ctx %>/credentials/approve" class="inline"
                              onsubmit="return confirm('Approve this request? It will be able to open the door.');">
                            <input type="hidden" name="credentialId" value="<%= r.getCredentialId() %>">
                            <button type="submit" class="btn btn-small">Approve</button>
                        </form>
                        <form method="post" action="<%= ctx %>/credentials/reject" class="inline"
                              onsubmit="return confirm('Reject this request?');">
                            <input type="hidden" name="credentialId" value="<%= r.getCredentialId() %>">
                            <button type="submit" class="btn btn-small btn-danger">Reject</button>
                        </form>
                    <% } else if ("APPROVED".equals(st)) { %>
                        <form method="post" action="<%= ctx %>/credentials/revoke" class="inline"
                              onsubmit="return confirm('Revoke this entry? It will stop working immediately.');">
                            <input type="hidden" name="credentialId" value="<%= r.getCredentialId() %>">
                            <button type="submit" class="btn btn-small btn-danger">Revoke</button>
                        </form>
                    <% } else { %>-<% } %>
                </div>
            </td>
            <% } %>
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

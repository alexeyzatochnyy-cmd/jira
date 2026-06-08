package com.company.jira.structuredash.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class DashboardServlet extends HttpServlet {

    @ComponentImport
    private final TemplateRenderer templateRenderer;

    @ComponentImport
    private final JiraAuthenticationContext authContext;

    @Autowired
    public DashboardServlet(TemplateRenderer templateRenderer,
                            JiraAuthenticationContext authContext) {
        this.templateRenderer = templateRenderer;
        this.authContext = authContext;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        ApplicationUser user = authContext.getLoggedInUser();
        if (user == null) {
            resp.sendRedirect(ComponentAccessor.getApplicationProperties()
                .getString("jira.baseurl") + "/login.jsp?os_destination=" +
                req.getRequestURI());
            return;
        }

        resp.setContentType("text/html;charset=UTF-8");
        resp.setHeader("X-Content-Type-Options", "nosniff");

        Map<String, Object> context = new HashMap<>();
        context.put("user", user);
        context.put("baseUrl", ComponentAccessor.getApplicationProperties()
            .getString("jira.baseurl"));
        context.put("pluginResourceUrl",
            "/download/resources/com.company.jira.structure-dashboard-plugin:structure-dashboard-resources");
        context.put("dashboardId", req.getParameter("dashboard"));

        templateRenderer.render("templates/dashboard.vm", context, resp.getWriter());
    }
}

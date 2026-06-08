package com.company.jira.structuredash.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.company.jira.structuredash.ao.DashboardLayout;
import com.company.jira.structuredash.ao.WidgetConfig;
import com.company.jira.structuredash.model.*;
import com.company.jira.structuredash.service.StructureApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.java.ao.Query;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.*;
import java.util.stream.Collectors;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    private final StructureApiService structureService;
    private final ActiveObjects ao;
    private final JiraAuthenticationContext authContext;
    private final ObjectMapper mapper = new ObjectMapper();

    @Context
    private HttpServletRequest request;

    @Autowired
    public DashboardResource(
            StructureApiService structureService,
            @ComponentImport ActiveObjects ao,
            @ComponentImport JiraAuthenticationContext authContext) {
        this.structureService = structureService;
        this.ao = ao;
        this.authContext = authContext;
    }

    // ─── Structure API proxy ──────────────────────────────────────

    /**
     * GET /rest/structuredash/1.0/structures
     * Список структур, доступных текущему пользователю.
     */
    @GET
    @Path("/structures")
    public Response getStructures() {
        try {
            requireAuth();
            String cookie = getSessionCookie();
            List<StructureInfo> structures = structureService.getStructures(cookie);
            return Response.ok(structures).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, "Failed to load structures: " + e.getMessage());
        }
    }

    /**
     * GET /rest/structuredash/1.0/structure/{id}/aggregates?columns=col1,col2
     * Агрегаты по всей структуре (суммы, прогресс).
     */
    @GET
    @Path("/structure/{id}/aggregates")
    public Response getAggregates(
            @PathParam("id") long structureId,
            @QueryParam("columns") @DefaultValue("progress") String columns) {
        try {
            requireAuth();
            List<String> columnKeys = Arrays.asList(columns.split(","));
            String cookie = getSessionCookie();
            StructureAggregates agg = structureService.getAggregates(structureId, columnKeys, cookie);
            return Response.ok(agg).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    /**
     * GET /rest/structuredash/1.0/structure/{id}/epic-progress?columns=col1,col2
     * Epic Progress — верхний уровень иерархии с прогрессом.
     */
    @GET
    @Path("/structure/{id}/epic-progress")
    public Response getEpicProgress(
            @PathParam("id") long structureId,
            @QueryParam("columns") @DefaultValue("progress,story_points") String columns) {
        try {
            requireAuth();
            List<String> columnKeys = Arrays.asList(columns.split(","));
            String cookie = getSessionCookie();
            List<EpicProgressItem> items = structureService.getEpicProgress(structureId, columnKeys, cookie);
            return Response.ok(items).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    /**
     * POST /rest/structuredash/1.0/structure/{id}/column-values
     * Body: { "issueIds": [123,456], "columnKeys": ["progress","sp_sum"] }
     * Значения колонок для конкретных задач.
     */
    @POST
    @Path("/structure/{id}/column-values")
    public Response getColumnValues(
            @PathParam("id") long structureId,
            Map<String, Object> body) {
        try {
            requireAuth();
            @SuppressWarnings("unchecked")
            List<Number> rawIds = (List<Number>) body.get("issueIds");
            @SuppressWarnings("unchecked")
            List<String> columnKeys = (List<String>) body.get("columnKeys");

            List<Long> issueIds = rawIds.stream()
                .map(Number::longValue)
                .collect(Collectors.toList());

            String cookie = getSessionCookie();
            Map<Long, Map<String, Object>> values =
                structureService.getColumnValues(structureId, issueIds, columnKeys, cookie);
            return Response.ok(values).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    // ─── Dashboard Layout CRUD ────────────────────────────────────

    /**
     * GET /rest/structuredash/1.0/dashboards
     * Дашборды текущего пользователя.
     */
    @GET
    @Path("/dashboards")
    public Response getDashboards() {
        try {
            ApplicationUser user = requireAuth();
            DashboardLayout[] layouts = ao.find(DashboardLayout.class,
                Query.select().where("OWNER_KEY = ?", user.getKey())
                     .order("ID DESC"));

            List<DashboardLayoutDto> dtos = Arrays.stream(layouts)
                .map(this::toDto)
                .collect(Collectors.toList());

            return Response.ok(dtos).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    /**
     * GET /rest/structuredash/1.0/dashboards/{dashboardId}
     * Конкретный дашборд с виджетами.
     */
    @GET
    @Path("/dashboards/{dashboardId}")
    public Response getDashboard(@PathParam("dashboardId") String dashboardId) {
        try {
            ApplicationUser user = requireAuth();
            DashboardLayout[] layouts = ao.find(DashboardLayout.class,
                Query.select().where("DASHBOARD_ID = ?", dashboardId));

            if (layouts.length == 0) return error(404, "Dashboard not found");

            DashboardLayout layout = layouts[0];
            // Проверяем доступ
            if (!layout.getOwnerKey().equals(user.getKey()) &&
                !"all".equals(layout.getSharedWith())) {
                return error(403, "Access denied");
            }

            return Response.ok(toDto(layout)).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    /**
     * POST /rest/structuredash/1.0/dashboards
     * Создать новый дашборд.
     */
    @POST
    @Path("/dashboards")
    public Response createDashboard(DashboardLayoutDto dto) {
        try {
            ApplicationUser user = requireAuth();
            final String dashId = UUID.randomUUID().toString();

            DashboardLayout layout = ao.executeInTransaction(() -> {
                DashboardLayout l = ao.create(DashboardLayout.class);
                l.setDashboardId(dashId);
                l.setName(dto.getName() != null ? dto.getName() : "My Dashboard");
                l.setOwnerKey(user.getKey());
                l.setSharedWith("none");
                l.save();
                return l;
            });

            // Сохраняем виджеты если переданы
            if (dto.getWidgets() != null) {
                saveWidgets(dashId, dto.getWidgets());
            }

            return Response.status(201).entity(toDto(layout)).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    /**
     * PUT /rest/structuredash/1.0/dashboards/{dashboardId}
     * Обновить layout (название + полный список виджетов).
     */
    @PUT
    @Path("/dashboards/{dashboardId}")
    public Response updateDashboard(
            @PathParam("dashboardId") String dashboardId,
            DashboardLayoutDto dto) {
        try {
            ApplicationUser user = requireAuth();
            DashboardLayout[] layouts = ao.find(DashboardLayout.class,
                Query.select().where("DASHBOARD_ID = ?", dashboardId));

            if (layouts.length == 0) return error(404, "Dashboard not found");
            DashboardLayout layout = layouts[0];
            if (!layout.getOwnerKey().equals(user.getKey())) return error(403, "Access denied");

            ao.executeInTransaction(() -> {
                if (dto.getName() != null) layout.setName(dto.getName());
                layout.save();
                return null;
            });

            // Пересохраняем виджеты: удаляем старые, создаём новые
            if (dto.getWidgets() != null) {
                WidgetConfig[] old = ao.find(WidgetConfig.class,
                    Query.select().where("DASHBOARD_ID = ?", dashboardId));
                ao.delete(old);
                saveWidgets(dashboardId, dto.getWidgets());
            }

            return Response.ok(toDto(layout)).build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    /**
     * DELETE /rest/structuredash/1.0/dashboards/{dashboardId}
     */
    @DELETE
    @Path("/dashboards/{dashboardId}")
    public Response deleteDashboard(@PathParam("dashboardId") String dashboardId) {
        try {
            ApplicationUser user = requireAuth();
            DashboardLayout[] layouts = ao.find(DashboardLayout.class,
                Query.select().where("DASHBOARD_ID = ?", dashboardId));
            if (layouts.length == 0) return error(404, "Not found");
            if (!layouts[0].getOwnerKey().equals(user.getKey())) return error(403, "Access denied");

            ao.executeInTransaction(() -> {
                WidgetConfig[] widgets = ao.find(WidgetConfig.class,
                    Query.select().where("DASHBOARD_ID = ?", dashboardId));
                ao.delete(widgets);
                ao.delete(layouts[0]);
                return null;
            });
            return Response.noContent().build();
        } catch (SecurityException e) {
            return error(401, e.getMessage());
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    // ─── Private helpers ──────────────────────────────────────────

    private void saveWidgets(String dashboardId, List<WidgetConfigDto> widgets) throws Exception {
        ao.executeInTransaction(() -> {
            int order = 0;
            for (WidgetConfigDto w : widgets) {
                WidgetConfig wc = ao.create(WidgetConfig.class);
                wc.setWidgetId(w.getId() != null ? w.getId() : UUID.randomUUID().toString());
                wc.setDashboardId(dashboardId);
                wc.setType(w.getType() != null ? w.getType() : "kpi");
                wc.setTitle(w.getTitle());
                wc.setCols(w.getCols() > 0 ? w.getCols() : 4);
                wc.setWidgetOrder(order++);
                wc.setStructureId(w.getStructureId());
                try {
                    wc.setColumnKeys(mapper.writeValueAsString(w.getColumnKeys()));
                    wc.setOptions(mapper.writeValueAsString(w.getOptions()));
                } catch (Exception ignore) {}
                wc.save();
            }
            return null;
        });
    }

    private DashboardLayoutDto toDto(DashboardLayout layout) {
        DashboardLayoutDto dto = new DashboardLayoutDto();
        dto.setId(layout.getDashboardId());
        dto.setName(layout.getName());
        dto.setOwnerKey(layout.getOwnerKey());

        WidgetConfig[] widgets = ao.find(WidgetConfig.class,
            Query.select()
                 .where("DASHBOARD_ID = ?", layout.getDashboardId())
                 .order("WIDGET_ORDER ASC"));

        List<WidgetConfigDto> wDtos = new ArrayList<>();
        for (WidgetConfig w : widgets) {
            WidgetConfigDto wd = new WidgetConfigDto();
            wd.setId(w.getWidgetId());
            wd.setType(w.getType());
            wd.setTitle(w.getTitle());
            wd.setCols(w.getCols());
            wd.setOrder(w.getWidgetOrder());
            wd.setStructureId(w.getStructureId());
            try {
                if (w.getColumnKeys() != null) {
                    wd.setColumnKeys(Arrays.asList(
                        mapper.readValue(w.getColumnKeys(), String[].class)));
                }
                if (w.getOptions() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> opts = mapper.readValue(w.getOptions(), Map.class);
                    wd.setOptions(opts);
                }
            } catch (Exception ignore) {}
            wDtos.add(wd);
        }
        dto.setWidgets(wDtos);
        return dto;
    }

    private ApplicationUser requireAuth() {
        ApplicationUser user = authContext.getLoggedInUser();
        if (user == null) throw new SecurityException("Authentication required");
        return user;
    }

    private String getSessionCookie() {
        String cookie = request.getHeader("Cookie");
        return cookie != null ? cookie : "";
    }

    private Response error(int status, String message) {
        return Response.status(status)
            .entity(new ApiError(status, message))
            .build();
    }
}

package com.company.jira.structuredash.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardLayoutDto {
    private String id;
    private String name;
    private String ownerKey;
    private List<WidgetConfigDto> widgets = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getOwnerKey() { return ownerKey; }
    public void setOwnerKey(String v) { this.ownerKey = v; }
    public List<WidgetConfigDto> getWidgets() { return widgets; }
    public void setWidgets(List<WidgetConfigDto> v) { this.widgets = v; }
}

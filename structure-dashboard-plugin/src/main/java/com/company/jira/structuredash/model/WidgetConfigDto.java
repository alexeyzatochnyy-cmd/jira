package com.company.jira.structuredash.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WidgetConfigDto {
    private String id;
    private String type;
    private String title;
    private int cols;
    private int order;
    private long structureId;
    private List<String> columnKeys = new ArrayList<>();
    private Map<String, Object> options = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getType() { return type; }
    public void setType(String v) { this.type = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public int getCols() { return cols; }
    public void setCols(int v) { this.cols = v; }
    public int getOrder() { return order; }
    public void setOrder(int v) { this.order = v; }
    public long getStructureId() { return structureId; }
    public void setStructureId(long v) { this.structureId = v; }
    public List<String> getColumnKeys() { return columnKeys; }
    public void setColumnKeys(List<String> v) { this.columnKeys = v; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> v) { this.options = v; }
}

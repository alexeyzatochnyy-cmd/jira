package com.company.jira.structuredash.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EpicProgressItem {
    private long issueId;
    private double progress;
    private int childCount;
    private Map<String, Object> columnValues = new LinkedHashMap<>();

    public long getIssueId() { return issueId; }
    public void setIssueId(long v) { this.issueId = v; }

    public double getProgress() { return progress; }
    public void setProgress(double v) { this.progress = v; }

    public int getChildCount() { return childCount; }
    public void setChildCount(int v) { this.childCount = v; }

    public Map<String, Object> getColumnValues() { return columnValues; }
    public void setColumnValues(Map<String, Object> v) { this.columnValues = v; }
}

package com.company.jira.structuredash.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructureAggregates {
    private int totalIssues;
    private double overallProgress;
    private Map<String, Double> sums   = new LinkedHashMap<>();
    private Map<String, Integer> counts = new LinkedHashMap<>();

    public int getTotalIssues() { return totalIssues; }
    public void setTotalIssues(int v) { this.totalIssues = v; }

    public double getOverallProgress() { return overallProgress; }
    public void setOverallProgress(double v) { this.overallProgress = v; }

    public Map<String, Double> getSums() { return sums; }
    public void addSum(String key, double val) { sums.put(key, val); }

    public Map<String, Integer> getCounts() { return counts; }
    public void addCount(String key, int val) { counts.put(key, val); }
}

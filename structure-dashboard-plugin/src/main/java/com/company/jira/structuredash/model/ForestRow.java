package com.company.jira.structuredash.model;

public class ForestRow {
    public final long issueId;
    public final int depth;
    public final int childCount;

    public ForestRow(long issueId, int depth, int childCount) {
        this.issueId = issueId;
        this.depth = depth;
        this.childCount = childCount;
    }
}

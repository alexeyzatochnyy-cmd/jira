package com.company.jira.structuredash.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForestResponse {

    private List<Long> issueIds = new ArrayList<>();

    @JsonIgnore
    private List<ForestRow> rows = new ArrayList<>();

    public void addIssueId(long id) { issueIds.add(id); }
    public List<Long> getIssueIds() { return issueIds; }

    public void setRows(List<ForestRow> rows) { this.rows = rows; }

    @JsonIgnore
    public List<Long> getTopLevelIds() {
        return rows.stream()
            .filter(r -> r.depth == 0)
            .map(r -> r.issueId)
            .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<Long> getChildrenOf(long parentId) {
        List<Long> children = new ArrayList<>();
        int parentIdx = -1, parentDepth = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).issueId == parentId) {
                parentIdx = i;
                parentDepth = rows.get(i).depth;
                break;
            }
        }
        if (parentIdx < 0) return children;
        for (int i = parentIdx + 1; i < rows.size(); i++) {
            int d = rows.get(i).depth;
            if (d <= parentDepth) break;
            if (d == parentDepth + 1) children.add(rows.get(i).issueId);
        }
        return children;
    }
}

package com.company.jira.structuredash.service;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.company.jira.structuredash.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StructureApiService {

    private static final Logger log = LoggerFactory.getLogger(StructureApiService.class);
    private static final String STRUCTURE_API = "/rest/structure/2.0";

    private final ObjectMapper mapper = new ObjectMapper();

    @ComponentImport
    private final JiraAuthenticationContext authContext;

    @Autowired
    public StructureApiService(JiraAuthenticationContext authContext) {
        this.authContext = authContext;
    }

    // ── Public API ────────────────────────────────────────────────

    public List<StructureInfo> getStructures(String sessionCookie) throws Exception {
        String json = get("/structure/", sessionCookie);
        JsonNode root = mapper.readTree(json);
        List<StructureInfo> result = new ArrayList<>();
        JsonNode structures = root.path("structures");
        if (structures.isArray()) {
            for (JsonNode s : structures) {
                StructureInfo info = new StructureInfo();
                info.setId(s.path("id").asLong());
                info.setName(s.path("name").asText());
                info.setDescription(s.path("description").asText(""));
                result.add(info);
            }
        }
        return result;
    }

    public ForestResponse getForest(long structureId, String query, String sessionCookie) throws Exception {
        StringBuilder path = new StringBuilder("/forest/latest?structureId=")
            .append(structureId).append("&expand=items");
        if (query != null && !query.isBlank()) {
            path.append("&jql=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        }
        String json = get(path.toString(), sessionCookie);
        return parseForest(mapper.readTree(json));
    }

    public Map<Long, Map<String, Object>> getColumnValues(
            long structureId, List<Long> issueIds,
            List<String> columnKeys, String sessionCookie) throws Exception {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("structureId", structureId);
        body.put("rows", issueIds.stream()
            .map(id -> Map.of("item", "issue:" + id))
            .collect(Collectors.toList()));
        body.put("columns", columnKeys.stream()
            .map(k -> Map.of("key", k))
            .collect(Collectors.toList()));

        String responseJson = post("/forest/value", mapper.writeValueAsString(body), sessionCookie);
        return parseColumnValues(mapper.readTree(responseJson), columnKeys);
    }

    public StructureAggregates getAggregates(
            long structureId, List<String> numericColumns,
            String sessionCookie) throws Exception {

        ForestResponse forest = getForest(structureId, null, sessionCookie);
        StructureAggregates agg = new StructureAggregates();
        agg.setTotalIssues(forest.getIssueIds().size());
        if (forest.getIssueIds().isEmpty()) return agg;

        Map<Long, Map<String, Object>> values =
            getColumnValues(structureId, forest.getIssueIds(), numericColumns, sessionCookie);

        for (String col : numericColumns) {
            double sum = 0; int count = 0;
            for (Map<String, Object> colMap : values.values()) {
                Object v = colMap.get(col);
                if (v instanceof Number) { sum += ((Number) v).doubleValue(); count++; }
            }
            agg.addSum(col, sum);
            agg.addCount(col, count);
        }

        String progressKey = numericColumns.stream()
            .filter(k -> k.equalsIgnoreCase("progress")).findFirst().orElse(null);
        if (progressKey != null) {
            double total = 0; int cnt = 0;
            for (Map<String, Object> cm : values.values()) {
                Object v = cm.get(progressKey);
                if (v instanceof Number) { total += ((Number) v).doubleValue(); cnt++; }
            }
            if (cnt > 0) agg.setOverallProgress(total / cnt);
        }
        return agg;
    }

    public List<EpicProgressItem> getEpicProgress(
            long structureId, List<String> columnKeys,
            String sessionCookie) throws Exception {

        ForestResponse forest = getForest(structureId, null, sessionCookie);
        List<Long> topLevelIds = forest.getTopLevelIds();
        if (topLevelIds.isEmpty()) return Collections.emptyList();

        Map<Long, Map<String, Object>> values =
            getColumnValues(structureId, forest.getIssueIds(), columnKeys, sessionCookie);

        List<EpicProgressItem> result = new ArrayList<>();
        for (Long epicId : topLevelIds) {
            EpicProgressItem item = new EpicProgressItem();
            item.setIssueId(epicId);
            item.setColumnValues(values.getOrDefault(epicId, Collections.emptyMap()));
            List<Long> children = forest.getChildrenOf(epicId);
            item.setChildCount(children.size());
            if (!children.isEmpty()) {
                boolean hasProgress = columnKeys.stream().anyMatch(k -> k.equalsIgnoreCase("progress"));
                if (hasProgress) {
                    String pk = columnKeys.stream().filter(k -> k.equalsIgnoreCase("progress")).findFirst().get();
                    double prog = children.stream().mapToDouble(cid -> {
                        Object v = values.getOrDefault(cid, Collections.emptyMap()).get(pk);
                        return v instanceof Number ? ((Number) v).doubleValue() : 0;
                    }).average().orElse(0);
                    item.setProgress(prog);
                }
            }
            result.add(item);
        }
        return result;
    }

    // ── HTTP helpers ──────────────────────────────────────────────

    private String get(String path, String sessionCookie) throws IOException {
        String url = getBaseUrl() + STRUCTURE_API + path;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet req = new HttpGet(url);
            req.setHeader("Cookie", sessionCookie != null ? sessionCookie : "");
            req.setHeader("Accept", "application/json");
            try (CloseableHttpResponse resp = client.execute(req)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (status < 200 || status >= 300) {
                    throw new IOException("Structure API GET [" + path + "] → HTTP " + status + ": " + body);
                }
                return body;
            }
        }
    }

    private String post(String path, String bodyJson, String sessionCookie) throws IOException {
        String url = getBaseUrl() + STRUCTURE_API + path;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost req = new HttpPost(url);
            req.setHeader("Cookie", sessionCookie != null ? sessionCookie : "");
            req.setHeader("Content-Type", "application/json");
            req.setHeader("Accept", "application/json");
            req.setEntity(new StringEntity(bodyJson, StandardCharsets.UTF_8));
            try (CloseableHttpResponse resp = client.execute(req)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (status < 200 || status >= 300) {
                    throw new IOException("Structure API POST [" + path + "] → HTTP " + status + ": " + body);
                }
                return body;
            }
        }
    }

    private String getBaseUrl() {
        return ComponentAccessor.getApplicationProperties().getString("jira.baseurl");
    }

    // ── Parsers ───────────────────────────────────────────────────

    private ForestResponse parseForest(JsonNode root) {
        ForestResponse forest = new ForestResponse();
        JsonNode rows = root.path("rows");
        if (!rows.isArray()) return forest;

        List<ForestRow> rowList = new ArrayList<>();
        for (JsonNode row : rows) {
            String item = row.path("item").asText("");
            if (!item.startsWith("issue:")) continue;
            long issueId = Long.parseLong(item.substring(6));
            int depth = row.path("depth").asInt(0);
            int childCount = row.path("childCount").asInt(0);
            rowList.add(new ForestRow(issueId, depth, childCount));
            forest.addIssueId(issueId);
        }
        forest.setRows(rowList);
        return forest;
    }

    private Map<Long, Map<String, Object>> parseColumnValues(JsonNode root, List<String> columnKeys) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        JsonNode values = root.path("values");
        if (!values.isArray()) return result;

        for (JsonNode entry : values) {
            String rowRef = entry.path("row").asText("");
            if (!rowRef.startsWith("issue:")) continue;
            long issueId = Long.parseLong(rowRef.substring(6));
            Map<String, Object> colValues = new LinkedHashMap<>();
            JsonNode cols = entry.path("columns");
            for (String key : columnKeys) {
                JsonNode col = cols.path(key);
                if (col.isMissingNode()) continue;
                JsonNode v = col.path("v");
                if      (v.isNumber())  colValues.put(key, v.asDouble());
                else if (v.isTextual()) colValues.put(key, v.asText());
                else if (v.isBoolean()) colValues.put(key, v.asBoolean());
                else if (!v.isMissingNode()) colValues.put(key, v.toString());
            }
            result.put(issueId, colValues);
        }
        return result;
    }
}

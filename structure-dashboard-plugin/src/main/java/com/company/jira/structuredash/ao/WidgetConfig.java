package com.company.jira.structuredash.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Table("SD_WIDGET")
@Preload
public interface WidgetConfig extends Entity {

    @NotNull
    @StringLength(36)
    String getWidgetId();
    void setWidgetId(String id);

    @NotNull
    @StringLength(36)
    String getDashboardId();
    void setDashboardId(String id);

    @NotNull
    @StringLength(64)
    String getType();
    void setType(String type);

    @StringLength(255)
    String getTitle();
    void setTitle(String title);

    int getCols();
    void setCols(int cols);

    int getWidgetOrder();
    void setWidgetOrder(int order);

    long getStructureId();
    void setStructureId(long id);

    @StringLength(StringLength.UNLIMITED)
    String getColumnKeys();
    void setColumnKeys(String json);

    @StringLength(StringLength.UNLIMITED)
    String getOptions();
    void setOptions(String json);
}

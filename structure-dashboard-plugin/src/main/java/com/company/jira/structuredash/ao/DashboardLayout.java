package com.company.jira.structuredash.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Table("SD_DASHBOARD")
@Preload
public interface DashboardLayout extends Entity {

    @NotNull
    @StringLength(36)
    String getDashboardId();
    void setDashboardId(String id);

    @NotNull
    @StringLength(255)
    String getName();
    void setName(String name);

    @NotNull
    @StringLength(255)
    String getOwnerKey();
    void setOwnerKey(String key);

    @StringLength(64)
    String getSharedWith();
    void setSharedWith(String s);
}

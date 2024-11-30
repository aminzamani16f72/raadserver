package org.traccar.model;

import org.traccar.storage.StorageName;

@StorageName("tc_imeis")
public class Imei {
    private int id;
    private String uniqueId;
    private String company;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId.trim();
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }
}

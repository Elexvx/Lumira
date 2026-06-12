package com.lumira.saas.modules.system.export;

public class ExportFieldVO {
    private String key;
    private String label;
    private boolean defaultSelected;
    private int orderNo;

    public ExportFieldVO() {
    }

    public ExportFieldVO(String key, String label, boolean defaultSelected, int orderNo) {
        this.key = key;
        this.label = label;
        this.defaultSelected = defaultSelected;
        this.orderNo = orderNo;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isDefaultSelected() { return defaultSelected; }
    public void setDefaultSelected(boolean defaultSelected) { this.defaultSelected = defaultSelected; }
    public int getOrderNo() { return orderNo; }
    public void setOrderNo(int orderNo) { this.orderNo = orderNo; }
}

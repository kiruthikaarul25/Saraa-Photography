package com.shop.model;

import java.util.List;

/** One topic block on the public Work page: label + its slides. Not a database table. */
public class TopicSection {
    private final String code;
    private final String label;
    private final List<PhotoGroup> groups;

    public TopicSection(String code, String label, List<PhotoGroup> groups) {
        this.code = code; this.label = label; this.groups = groups;
    }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public List<PhotoGroup> getGroups() { return groups; }
}

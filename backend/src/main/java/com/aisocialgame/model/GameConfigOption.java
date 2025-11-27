package com.aisocialgame.model;

import java.util.List;

public class GameConfigOption {
    private String id;
    private String label;
    private String type; // select | number | boolean | text
    private Object defaultValue;
    private List<Option> options;
    private Integer min;
    private Integer max;

    public record Option(String label, Object value) {}

    public GameConfigOption() {}

    public GameConfigOption(String id, String label, String type, Object defaultValue, List<Option> options, Integer min, Integer max) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.options = options;
        this.min = min;
        this.max = max;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getType() { return type; }
    public Object getDefaultValue() { return defaultValue; }
    public List<Option> getOptions() { return options; }
    public Integer getMin() { return min; }
    public Integer getMax() { return max; }
}

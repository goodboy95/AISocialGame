package com.aisocialgame.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class AiEmbeddingsRequest {
    @NotEmpty(message = "input 不能为空")
    private List<String> input;
    private String model;
    private Boolean normalize;

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Boolean getNormalize() {
        return normalize;
    }

    public void setNormalize(Boolean normalize) {
        this.normalize = normalize;
    }
}

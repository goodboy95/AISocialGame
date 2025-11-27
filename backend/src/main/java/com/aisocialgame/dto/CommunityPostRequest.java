package com.aisocialgame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CommunityPostRequest {
    @NotBlank
    @Size(max = 1024)
    private String content;
    private List<String> tags;

    public String getContent() {
        return content;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}

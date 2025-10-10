package com.aisocialgame.backend.controller;

import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aisocialgame.backend.config.AppProperties;
import com.aisocialgame.backend.dto.MetaDtos;

@RestController
@RequestMapping("/meta")
public class MetaController {

    private final AppProperties properties;

    public MetaController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/styles/")
    public MetaDtos.AiStyleResponse listStyles() {
        return new MetaDtos.AiStyleResponse(properties.getAiStyles().stream()
                .map(style -> new MetaDtos.AiStyle(style.getKey(), style.getLabel(), style.getDescription()))
                .collect(Collectors.toList()));
    }
}

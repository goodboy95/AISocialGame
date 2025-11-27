package com.aisocialgame.model.converter;

import com.aisocialgame.model.GameLogEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Converter
public class GameLogEntryListConverter implements AttributeConverter<List<GameLogEntry>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<List<GameLogEntry>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<GameLogEntry> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to convert game logs", e);
        }
    }

    @Override
    public List<GameLogEntry> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read game logs", e);
        }
    }
}

package com.aisocialgame.model.converter;

import com.aisocialgame.model.GamePlayerState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Converter
public class GamePlayerStateListConverter implements AttributeConverter<List<GamePlayerState>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<List<GamePlayerState>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<GamePlayerState> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to convert game player states", e);
        }
    }

    @Override
    public List<GamePlayerState> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read game player states", e);
        }
    }
}

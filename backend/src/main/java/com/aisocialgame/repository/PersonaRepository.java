package com.aisocialgame.repository;

import com.aisocialgame.model.Persona;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PersonaRepository {
    private final List<Persona> personas = List.of(
            new Persona("ai1", "福尔摩斯", "逻辑严密", "https://api.dicebear.com/7.x/avataaars/svg?seed=Sherlock"),
            new Persona("ai2", "小丑", "混乱邪恶", "https://api.dicebear.com/7.x/avataaars/svg?seed=Joker"),
            new Persona("ai3", "华生", "辅助型", "https://api.dicebear.com/7.x/avataaars/svg?seed=Watson"),
            new Persona("ai4", "露娜", "神秘莫测", "https://api.dicebear.com/7.x/avataaars/svg?seed=Luna")
    );

    public List<Persona> findAll() {
        return personas;
    }

    public Persona findById(String id) {
        return personas.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }
}

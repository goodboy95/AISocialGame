package com.aisocialgame.service;

import com.aisocialgame.model.Persona;
import com.aisocialgame.repository.PersonaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PersonaService {
    private final PersonaRepository personaRepository;

    public PersonaService(PersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    public List<Persona> list() {
        return personaRepository.findAll();
    }
}

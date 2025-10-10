package com.aisocialgame.backend.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aisocialgame.backend.dto.WordPairDtos;
import com.aisocialgame.backend.entity.WordPair;
import com.aisocialgame.backend.repository.WordPairRepository;

@Service
public class WordPairService {

    private final WordPairRepository wordPairRepository;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

    public WordPairService(WordPairRepository wordPairRepository) {
        this.wordPairRepository = wordPairRepository;
    }

    public List<WordPairDtos.WordPairView> list(String topic, String difficulty, String query) {
        return wordPairRepository.findAll().stream()
                .filter(pair -> topic == null || pair.getTopic().toLowerCase(Locale.ROOT).contains(topic.toLowerCase(Locale.ROOT)))
                .filter(pair -> difficulty == null || pair.getDifficulty().name().equalsIgnoreCase(difficulty))
                .filter(pair -> query == null || pair.getCivilianWord().contains(query) || pair.getUndercoverWord().contains(query))
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Transactional
    public WordPairDtos.WordPairView create(WordPairDtos.WordPairPayload payload) {
        WordPair pair = new WordPair();
        pair.setTopic(payload.topic());
        pair.setCivilianWord(payload.civilianWord());
        pair.setUndercoverWord(payload.undercoverWord());
        pair.setDifficulty(WordPair.Difficulty.valueOf(payload.difficulty().toUpperCase(Locale.ROOT)));
        pair.setCreatedAt(Instant.now());
        pair.setUpdatedAt(Instant.now());
        return toView(wordPairRepository.save(pair));
    }

    @Transactional
    public WordPairDtos.WordPairView update(long id, WordPairDtos.WordPairPayload payload) {
        WordPair pair = wordPairRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("词条不存在"));
        if (payload.topic() != null) {
            pair.setTopic(payload.topic());
        }
        if (payload.civilianWord() != null) {
            pair.setCivilianWord(payload.civilianWord());
        }
        if (payload.undercoverWord() != null) {
            pair.setUndercoverWord(payload.undercoverWord());
        }
        if (payload.difficulty() != null) {
            pair.setDifficulty(WordPair.Difficulty.valueOf(payload.difficulty().toUpperCase(Locale.ROOT)));
        }
        pair.setUpdatedAt(Instant.now());
        return toView(wordPairRepository.save(pair));
    }

    @Transactional
    public void delete(long id) {
        wordPairRepository.deleteById(id);
    }

    @Transactional
    public WordPairDtos.BulkImportResponse bulkImport(WordPairDtos.BulkImportPayload payload) {
        List<WordPair> saved = new ArrayList<>();
        for (WordPairDtos.WordPairPayload item : payload.items()) {
            WordPair pair = new WordPair();
            pair.setTopic(item.topic());
            pair.setCivilianWord(item.civilianWord());
            pair.setUndercoverWord(item.undercoverWord());
            pair.setDifficulty(WordPair.Difficulty.valueOf(item.difficulty().toUpperCase(Locale.ROOT)));
            pair.setCreatedAt(Instant.now());
            pair.setUpdatedAt(Instant.now());
            saved.add(wordPairRepository.save(pair));
        }
        List<WordPairDtos.WordPairView> views = saved.stream().map(this::toView).toList();
        return new WordPairDtos.BulkImportResponse(views, views.size());
    }

    public WordPairDtos.ExportResponse export(String topic, String difficulty, String query) {
        List<WordPairDtos.WordPairView> views = list(topic, difficulty, query);
        return new WordPairDtos.ExportResponse(views);
    }

    private WordPairDtos.WordPairView toView(WordPair pair) {
        return new WordPairDtos.WordPairView(
                pair.getId(),
                pair.getTopic(),
                pair.getCivilianWord(),
                pair.getUndercoverWord(),
                pair.getDifficulty().name().toLowerCase(Locale.ROOT),
                formatter.format(pair.getCreatedAt()),
                formatter.format(pair.getUpdatedAt()));
    }
}

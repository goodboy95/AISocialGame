package com.aisocialgame.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aisocialgame.backend.dto.WordPairDtos;
import com.aisocialgame.backend.service.AuthService;
import com.aisocialgame.backend.service.WordPairService;

@RestController
@RequestMapping("/games/word-pairs")
public class WordPairController {

    private final WordPairService wordPairService;
    private final AuthService authService;

    public WordPairController(WordPairService wordPairService, AuthService authService) {
        this.wordPairService = wordPairService;
        this.authService = authService;
    }

    @GetMapping("/")
    public List<WordPairDtos.WordPairView> list(
            @RequestParam(name = "topic", required = false) String topic,
            @RequestParam(name = "difficulty", required = false) String difficulty,
            @RequestParam(name = "q", required = false) String query) {
        return wordPairService.list(topic, difficulty, query);
    }

    @PostMapping("/")
    public ResponseEntity<WordPairDtos.WordPairView> create(@RequestBody WordPairDtos.WordPairPayload payload) {
        if (authService.currentUser() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(wordPairService.create(payload));
    }

    @PostMapping("/import/")
    public ResponseEntity<WordPairDtos.BulkImportResponse> bulkImport(@RequestBody WordPairDtos.BulkImportPayload payload) {
        if (authService.currentUser() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(wordPairService.bulkImport(payload));
    }

    @GetMapping("/export/")
    public WordPairDtos.ExportResponse export(
            @RequestParam(name = "topic", required = false) String topic,
            @RequestParam(name = "difficulty", required = false) String difficulty,
            @RequestParam(name = "q", required = false) String query) {
        return wordPairService.export(topic, difficulty, query);
    }

    @PatchMapping("/{id}/")
    public ResponseEntity<WordPairDtos.WordPairView> update(
            @PathVariable("id") long id, @RequestBody WordPairDtos.WordPairPayload payload) {
        if (authService.currentUser() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(wordPairService.update(id, payload));
    }

    @DeleteMapping("/{id}/")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        if (authService.currentUser() == null) {
            return ResponseEntity.status(401).build();
        }
        wordPairService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.aisocialgame.controller;

import com.aisocialgame.model.PlayerStats;
import com.aisocialgame.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {
    private final StatsService statsService;

    public RankingController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ResponseEntity<List<PlayerStats>> rankings(@RequestParam(name = "gameId", defaultValue = "total") String gameId) {
        return ResponseEntity.ok(statsService.top(gameId));
    }
}

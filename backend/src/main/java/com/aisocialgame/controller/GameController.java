package com.aisocialgame.controller;

import com.aisocialgame.exception.ApiException;
import com.aisocialgame.model.Game;
import com.aisocialgame.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public ResponseEntity<List<Game>> list() {
        return ResponseEntity.ok(gameService.listGames());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Game> detail(@PathVariable("id") String id) {
        Game game = gameService.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "游戏不存在"));
        return ResponseEntity.ok(game);
    }
}

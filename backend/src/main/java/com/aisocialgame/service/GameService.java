package com.aisocialgame.service;

import com.aisocialgame.model.Game;
import com.aisocialgame.repository.GameRepository;
import com.aisocialgame.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GameService {
    private final GameRepository gameRepository;
    private final RoomRepository roomRepository;

    public GameService(GameRepository gameRepository, RoomRepository roomRepository) {
        this.gameRepository = gameRepository;
        this.roomRepository = roomRepository;
    }

    public List<Game> listGames() {
        return gameRepository.findAll().stream().map(this::attachOnlineCount).toList();
    }

    public Optional<Game> findById(String id) {
        return gameRepository.findById(id).map(this::attachOnlineCount);
    }

    private Game attachOnlineCount(Game game) {
        int online = roomRepository.findByGameIdOrderByCreatedAtAsc(game.getId()).stream()
                .mapToInt(room -> room.getSeats() != null ? room.getSeats().size() : 0)
                .sum();
        game.setOnlineCount(online);
        return game;
    }
}

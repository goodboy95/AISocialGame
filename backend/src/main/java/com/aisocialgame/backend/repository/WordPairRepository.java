package com.aisocialgame.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aisocialgame.backend.entity.WordPair;

public interface WordPairRepository extends JpaRepository<WordPair, Long> {
    List<WordPair> findByTopicContainingIgnoreCase(String topic);

    List<WordPair> findByDifficulty(WordPair.Difficulty difficulty);
}

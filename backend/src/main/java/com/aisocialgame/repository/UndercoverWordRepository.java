package com.aisocialgame.repository;

import com.aisocialgame.model.UndercoverWordPair;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Random;

@Repository
public class UndercoverWordRepository {
    private final List<UndercoverWordPair> pairs = List.of(
            new UndercoverWordPair("可口可乐", "百事可乐"),
            new UndercoverWordPair("抹茶", "奶茶"),
            new UndercoverWordPair("火锅", "串串"),
            new UndercoverWordPair("苹果", "梨子"),
            new UndercoverWordPair("蓝牙耳机", "有线耳机"),
            new UndercoverWordPair("游戏机", "电脑"),
            new UndercoverWordPair("咖啡", "美式"),
            new UndercoverWordPair("外卖", "快递"),
            new UndercoverWordPair("地铁", "高铁"),
            new UndercoverWordPair("西瓜", "哈密瓜")
    );

    private final Random random = new Random();

    public UndercoverWordPair randomPair() {
        return pairs.get(random.nextInt(pairs.size()));
    }
}

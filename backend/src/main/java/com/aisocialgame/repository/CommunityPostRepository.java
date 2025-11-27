package com.aisocialgame.repository;

import com.aisocialgame.model.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, String> {
    List<CommunityPost> findTop50ByOrderByCreatedAtDesc();
}

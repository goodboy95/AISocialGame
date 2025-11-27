package com.aisocialgame.service;

import com.aisocialgame.dto.CommunityPostRequest;
import com.aisocialgame.exception.ApiException;
import com.aisocialgame.model.CommunityPost;
import com.aisocialgame.model.User;
import com.aisocialgame.repository.CommunityPostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CommunityService {
    private final CommunityPostRepository repository;

    public CommunityService(CommunityPostRepository repository) {
        this.repository = repository;
    }

    public List<CommunityPost> list() {
        return repository.findTop50ByOrderByCreatedAtDesc();
    }

    public CommunityPost create(CommunityPostRequest request, User user, String guestName) {
        CommunityPost post = new CommunityPost();
        post.setContent(request.getContent().trim());
        post.setTags(request.getTags() == null ? new ArrayList<>() : request.getTags());
        if (user != null) {
            post.setAuthorId(user.getId());
            post.setAuthorName(user.getNickname());
            post.setAvatar(user.getAvatar());
        } else {
            String name = (guestName != null && !guestName.isBlank()) ? guestName.trim() : "游客" + UUID.randomUUID().toString().substring(0, 6);
            post.setAuthorName(name);
            post.setAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + name.replace(" ", ""));
        }
        return repository.save(post);
    }

    public CommunityPost like(String id) {
        CommunityPost post = repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "帖子不存在"));
        post.setLikes(post.getLikes() + 1);
        return repository.save(post);
    }
}

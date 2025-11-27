package com.aisocialgame.model;

import com.aisocialgame.model.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "community_posts")
public class CommunityPost {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String authorName;

    @Column(length = 36)
    private String authorId;

    private String avatar;

    @Column(nullable = false, length = 1024)
    private String content;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private List<String> tags = new ArrayList<>();

    private int likes;
    private int comments;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getAuthorName() { return authorName; }
    public String getAuthorId() { return authorId; }
    public String getAvatar() { return avatar; }
    public String getContent() { return content; }
    public List<String> getTags() { return tags; }
    public int getLikes() { return likes; }
    public int getComments() { return comments; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public void setContent(String content) { this.content = content; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setLikes(int likes) { this.likes = likes; }
    public void setComments(int comments) { this.comments = comments; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

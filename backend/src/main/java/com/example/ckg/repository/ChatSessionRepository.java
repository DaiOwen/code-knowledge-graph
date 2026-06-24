package com.example.ckg.repository;

import com.example.ckg.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ChatSession> findByUserIdAndProjectIdOrderByCreatedAtDesc(Long userId, Long projectId);
}
package com.example.ckg.repository;

import com.example.ckg.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByPayloadHash(String payloadHash);

    List<WebhookEvent> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<WebhookEvent> findByStatusOrderByCreatedAtAsc(WebhookEvent.EventStatus status);

    List<WebhookEvent> findByStatusAndCreatedAtBefore(
        WebhookEvent.EventStatus status,
        LocalDateTime before
    );

    boolean existsByPayloadHashAndCreatedAtAfter(String payloadHash, LocalDateTime after);
}
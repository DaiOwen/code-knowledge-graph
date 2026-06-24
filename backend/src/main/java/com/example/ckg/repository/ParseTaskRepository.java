package com.example.ckg.repository;

import com.example.ckg.entity.ParseTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParseTaskRepository extends JpaRepository<ParseTask, Long> {

    List<ParseTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ParseTask> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<ParseTask> findByStatus(ParseTask.ParseStatus status);
}
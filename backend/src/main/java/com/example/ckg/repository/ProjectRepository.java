package com.example.ckg.repository;

import com.example.ckg.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByStatus(Project.ProjectStatus status);

    boolean existsByName(String name);

    boolean existsByGitUrl(String gitUrl);
}
package com.example.ckg.repository;

import com.example.ckg.entity.GitCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GitCredentialRepository extends JpaRepository<GitCredential, Long> {

    List<GitCredential> findByUserId(Long userId);
}
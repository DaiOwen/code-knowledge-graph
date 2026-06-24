package com.example.ckg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.neo4j.driver.Driver;

@Configuration
public class Neo4jConfig {

    @Bean
    public Neo4jClient neo4jClient(Driver driver) {
        return Neo4jClient.create(driver);
    }
}
package com.example.ckg;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base test class for service tests
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public abstract class BaseTest {
    // Common test utilities can be added here
}
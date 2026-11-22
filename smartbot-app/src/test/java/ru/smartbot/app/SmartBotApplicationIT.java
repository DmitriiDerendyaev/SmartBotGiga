package ru.smartbot.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.smartbot.application.api.user.EnsureUserUseCase;
import ru.smartbot.application.api.user.TelegramPrincipal;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-тест composition root: контекст поднимается с реальным PostgreSQL
 * (Flyway-миграции V1/V2), entity/repository scan находит adapter-persistence,
 * use case регистрации работает end-to-end.
 */
@SpringBootTest(classes = SmartBotApplication.class)
@Testcontainers
@DisplayName("SmartBotApplication — composition root поднимается")
class SmartBotApplicationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("регистрация через реальные порты и adapter-persistence")
    void registersUserThroughRealAdapter() {
        assertThat(Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.contains("smartbot") || name.contains("Ensure") || name.contains("User"))
                .toList())
                .as("smartbot beans")
                .isNotEmpty();

        EnsureUserUseCase ensureUser = context.getBean(EnsureUserUseCase.class);
        var result = ensureUser.ensureUser(
                new TelegramPrincipal(555_000_001L, "smoke", "Smoke", "ru"));

        assertThat(result.registered()).isTrue();
        assertThat(result.blocked()).isFalse();
        assertThat(result.user().telegramUserId()).isEqualTo(555_000_001L);
    }
}

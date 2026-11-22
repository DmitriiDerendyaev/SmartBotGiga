package ru.smartbot.adapter.persistence.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.smartbot.adapter.persistence.user.UserPersistenceTestConfig;
import ru.smartbot.application.api.telegram.PollingOffsetRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = UserPersistenceTestConfig.class)
@Testcontainers
@DisplayName("PollingOffsetRepository — монотонный offset (AD-23)")
class PollingOffsetRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private PollingOffsetRepository offsets;

    @Test
    @DisplayName("offset сохраняется монотонно и не откатывается")
    void monotonicOffset() {
        assertThat(offsets.lastUpdateId()).isPresent();

        offsets.save(100);
        assertThat(offsets.lastUpdateId()).hasValue(100);

        offsets.save(50); // устаревший/повторный offset не откатывает состояние
        assertThat(offsets.lastUpdateId()).hasValue(100);
    }
}

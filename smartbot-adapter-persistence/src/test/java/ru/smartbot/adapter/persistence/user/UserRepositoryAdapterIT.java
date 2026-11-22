package ru.smartbot.adapter.persistence.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.application.api.user.UserRepository;
import ru.smartbot.domain.user.User;
import ru.smartbot.domain.user.UserStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = UserPersistenceTestConfig.class)
@Testcontainers
@DisplayName("UserRepositoryAdapter — PostgreSQL (AD-17, I/O-матрица story 1.2)")
class UserRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private UserRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private static final long TELEGRAM_ID = 900_000_001L;

    @Test
    @DisplayName("первый update: User + UserSettings (history_enabled=false)")
    void firstUpdateCreatesUserAndSettings() {
        UserRepository.Registration registration = repository.getOrCreate(
                new TelegramPrincipal(TELEGRAM_ID, "dmitrii", "Dmitrii", "ru"));

        assertThat(registration.created()).isTrue();
        User user = registration.user();
        assertThat(user.telegramUserId()).isEqualTo(TELEGRAM_ID);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.username()).isEqualTo("dmitrii");

        Boolean historyEnabled = jdbc.queryForObject(
                "SELECT history_enabled FROM user_settings WHERE user_id = ?",
                Boolean.class, user.id());
        assertThat(historyEnabled).isFalse();
    }

    @Test
    @DisplayName("повторный update: только profile/last_seen_at; status и настройки не тронуты")
    void repeatUpdateTouchesOnlyProfileFields() {
        User first = repository.getOrCreate(
                new TelegramPrincipal(TELEGRAM_ID + 1, "old_name", "Old", "ru")).user();

        UUID settingsId = jdbc.queryForObject(
                "SELECT id FROM user_settings WHERE user_id = ?", UUID.class, first.id());
        jdbc.update("UPDATE users SET status = 'blocked' WHERE id = ?", first.id());
        jdbc.update("UPDATE user_settings SET history_enabled = true, updated_at = now() - interval '1 day' WHERE user_id = ?",
                first.id());
        var settingsBefore = jdbc.queryForMap("SELECT * FROM user_settings WHERE user_id = ?", first.id());

        UserRepository.Registration second = repository.getOrCreate(
                new TelegramPrincipal(TELEGRAM_ID + 1, "new_name", "New", "en"));

        assertThat(second.created()).isFalse();
        assertThat(second.user().id()).isEqualTo(first.id());
        assertThat(second.user().username()).isEqualTo("new_name");
        assertThat(second.user().firstName()).isEqualTo("New");
        assertThat(second.user().languageCode()).isEqualTo("en");
        // blocked не разблокируется upsert'ом (AD-17)
        assertThat(second.user().status()).isEqualTo(UserStatus.BLOCKED);
        // настройки не тронуты (AD-17/26)
        assertThat(jdbc.queryForMap("SELECT * FROM user_settings WHERE user_id = ?", first.id()))
                .isEqualTo(settingsBefore);
        assertThat(jdbc.queryForObject("SELECT id FROM user_settings WHERE user_id = ?",
                UUID.class, first.id())).isEqualTo(settingsId);
    }

    @Test
    @DisplayName("blocked пользователь: статус сохраняется, результат несёт blocked")
    void blockedUserStaysBlocked() {
        User user = repository.getOrCreate(
                new TelegramPrincipal(TELEGRAM_ID + 2, "blocked_user", null, null)).user();
        jdbc.update("UPDATE users SET status = 'blocked' WHERE id = ?", user.id());

        User again = repository.getOrCreate(
                new TelegramPrincipal(TELEGRAM_ID + 2, "blocked_user", null, null)).user();

        assertThat(again.status()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    @DisplayName("минимизация: principal без profile-полей валиден")
    void minimalPrincipal() {
        User user = repository.getOrCreate(
                new TelegramPrincipal(TELEGRAM_ID + 3, null, null, null)).user();

        assertThat(user.username()).isNull();
        assertThat(user.firstName()).isNull();
        assertThat(user.languageCode()).isNull();
    }

    @Test
    @DisplayName("гонка регистрации: 8 параллельных первых update → ровно один User")
    void concurrentFirstRegistrationCreatesExactlyOneUser() throws Exception {
        long telegramId = TELEGRAM_ID + 4;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<UUID>> tasks = new ArrayList<>();
            IntStream.range(0, 8).forEach(i -> tasks.add(() -> repository.getOrCreate(
                    new TelegramPrincipal(telegramId, "racer" + i, "Racer", "ru")).user().id()));

            List<Future<UUID>> futures = pool.invokeAll(tasks);
            Set<UUID> ids = new java.util.HashSet<>();
            for (Future<UUID> future : futures) {
                ids.add(future.get());
            }

            assertThat(ids).hasSize(1);
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM users WHERE telegram_user_id = ?",
                    Integer.class, telegramId);
            assertThat(count).isEqualTo(1);
            Integer settingsCount = jdbc.queryForObject(
                    "SELECT count(*) FROM user_settings WHERE user_id = ?",
                    Integer.class, ids.iterator().next());
            assertThat(settingsCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("поиск по UUID и Telegram id")
    void lookups() {
        User user = repository.getOrCreate(
                new TelegramPrincipal(TELEGRAM_ID + 5, "lookup", null, null)).user();

        assertThat(repository.findById(user.id())).contains(user);
        assertThat(repository.findByTelegramUserId(TELEGRAM_ID + 5)).contains(user);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        assertThat(repository.findByTelegramUserId(TELEGRAM_ID + 999)).isEmpty();
    }
}

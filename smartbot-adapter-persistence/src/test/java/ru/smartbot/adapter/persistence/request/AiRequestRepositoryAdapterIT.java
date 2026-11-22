package ru.smartbot.adapter.persistence.request;

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
import ru.smartbot.application.api.request.AiRequestRepository;
import ru.smartbot.application.api.request.ProcessedUpdateRepository;
import ru.smartbot.application.api.user.TelegramPrincipal;
import ru.smartbot.application.api.user.UserRepository;
import ru.smartbot.domain.request.AiRequest;
import ru.smartbot.domain.request.AiRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = UserPersistenceTestConfig.class)
@Testcontainers
@DisplayName("AiRequest/ProcessedUpdate репозитории — PostgreSQL (AD-13/14/15)")
class AiRequestRepositoryAdapterIT {

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
    private AiRequestRepository aiRequests;
    @Autowired
    private ProcessedUpdateRepository ledger;
    @Autowired
    private UserRepository users;

    @Test
    @DisplayName("create + findById/findByCorrelationToken + reuse")
    void createFindReuse() {
        UUID userId = user(900_000_101L);
        String token = "g:" + hex();
        AiRequest created = aiRequests.create(request(userId, token, "Query A"));

        AiRequest loaded = aiRequests.findById(created.id()).orElseThrow();
        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(loaded.correlationToken()).isEqualTo(token);
        assertThat(loaded.status()).isEqualTo(AiRequestStatus.CREATED);
        assertThat(aiRequests.findByCorrelationToken(token)).map(AiRequest::id).contains(created.id());
        assertThat(aiRequests.findActiveByUserAndQuery(userId, "Query A",
                Instant.now().minusSeconds(60))).isPresent();
    }

    @Test
    @DisplayName("TTL: запрос старше окна не переиспользуется")
    void reuseOutsideTtl() {
        UUID userId = user(900_000_102L);
        aiRequests.create(request(userId, "g:" + hex(), "Old"));

        assertThat(aiRequests.findActiveByUserAndQuery(userId, "Old",
                Instant.now().plusSeconds(60))).isEmpty();
    }

    @Test
    @DisplayName("partial unique: два активных на один inline_message_id невозможны (AD-14)")
    void onlyOneActivePerInlineMessage() {
        UUID userId = user(900_000_103L);
        String inlineMessageId = "im-" + UUID.randomUUID();
        aiRequests.create(request(userId, "g:" + hex(), "First", inlineMessageId));

        assertThatThrownBy(() -> aiRequests.create(
                request(userId, "g:" + hex(), "Second", inlineMessageId)))
                .hasMessageContaining("uq_ai_requests_active_inline_message");
    }

    @Test
    @DisplayName("после терминального статуса новый запрос на то же сообщение разрешён")
    void newRequestAfterTerminal() {
        UUID userId = user(900_000_104L);
        String inlineMessageId = "im-" + UUID.randomUUID();
        AiRequest first = aiRequests.create(request(userId, "g:" + hex(), "First", inlineMessageId));
        assertThat(aiRequests.transition(first.id(), AiRequestStatus.CREATED,
                AiRequestStatus.FAILED, Instant.now())).isTrue();

        AiRequest second = aiRequests.create(
                request(userId, "g:" + hex(), "Second", inlineMessageId));
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("CAS: гонка за один переход — ровно один победитель (AD-15)")
    void concurrentCasExactlyOneWinner() throws Exception {
        UUID userId = user(900_000_105L);
        AiRequest request = aiRequests.create(request(userId, "g:" + hex(), "Race"));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(i -> (Callable<Boolean>) () -> aiRequests.transition(
                            request.id(), AiRequestStatus.CREATED, AiRequestStatus.GENERATING,
                            Instant.now()))
                    .toList();
            List<Future<Boolean>> results = pool.invokeAll(tasks);

            long winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("attachInlineMessageId: конфликт с активным запросом → false (AD-14)")
    void attachConflictsWithActiveRequest() {
        UUID userId = user(900_000_106L);
        String inlineMessageId = "im-" + UUID.randomUUID();
        aiRequests.create(request(userId, "g:" + hex(), "First", inlineMessageId));
        AiRequest second = aiRequests.create(request(userId, "g:" + hex(), "Second"));

        assertThat(aiRequests.attachInlineMessageId(second.id(), inlineMessageId)).isFalse();
        assertThat(aiRequests.findActiveByInlineMessageId(inlineMessageId)).isPresent();
    }

    @Test
    @DisplayName("ledger: дубль update_id гасится, новый — пишется")
    void ledgerIdempotency() {
        long updateId = System.nanoTime();

        assertThat(ledger.tryRecord(updateId, "inline_query", Instant.now())).isTrue();
        assertThat(ledger.tryRecord(updateId, "inline_query", Instant.now())).isFalse();
    }

    private UUID user(long telegramId) {
        return users.getOrCreate(new TelegramPrincipal(telegramId, "u", "U", "ru")).user().id();
    }

    private static String hex() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static AiRequest request(UUID userId, String token, String query) {
        return request(userId, token, query, null);
    }

    private static AiRequest request(UUID userId, String token, String query, String inlineMessageId) {
        return new AiRequest(UUID.randomUUID(), userId, null, null, inlineMessageId, token, query,
                AiRequestStatus.CREATED, null, null, false, null, null, null, Instant.now(), null);
    }
}

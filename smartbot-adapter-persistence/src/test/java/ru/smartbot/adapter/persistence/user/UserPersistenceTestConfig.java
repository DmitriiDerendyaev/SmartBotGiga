package ru.smartbot.adapter.persistence.user;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import ru.smartbot.adapter.persistence.request.AiRequestRepositoryAdapter;
import ru.smartbot.adapter.persistence.request.ProcessedUpdateRepositoryAdapter;
import ru.smartbot.adapter.persistence.telegram.PollingOffsetRepositoryAdapter;

/**
 * Минимальный Spring-контекст для IT persistence-адаптеров (без composition root):
 * autoconfiguration (datasource, JPA, Flyway) + все сущности/репозитории модуля.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("ru.smartbot.adapter.persistence")
@EnableJpaRepositories("ru.smartbot.adapter.persistence")
@Import({UserRepositoryAdapter.class, AiRequestRepositoryAdapter.class,
        ProcessedUpdateRepositoryAdapter.class, PollingOffsetRepositoryAdapter.class})
public class UserPersistenceTestConfig {
}

package ru.smartbot.adapter.persistence.user;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Минимальный Spring-контекст для IT persistence-адаптера (без composition root):
 * autoconfiguration (datasource, JPA, Flyway) + репозитории и адаптер.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = UserJpaEntity.class)
@EnableJpaRepositories(basePackageClasses = SpringDataUserRepository.class)
@Import(UserRepositoryAdapter.class)
class UserPersistenceTestConfig {
}

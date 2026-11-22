package ru.smartbot.app;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Composition root приложения SmartBot.
 *
 * <p>Единственный модуль, которому разрешено видеть все адаптеры и слои:
 * здесь происходит wiring бинов и запуск Spring Boot.
 *
 * <p>Entities и репозитории живут в adapter-persistence (вне пакета app),
 * поэтому их сканирование задано явно: иначе context не поднимется.
 */
@SpringBootConfiguration
@ComponentScan("ru.smartbot")
@SpringBootApplication(scanBasePackages = "ru.smartbot")
@EntityScan("ru.smartbot")
@EnableJpaRepositories("ru.smartbot")
public class SmartBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBotApplication.class, args);
    }
}

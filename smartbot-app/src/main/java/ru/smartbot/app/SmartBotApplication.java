package ru.smartbot.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root приложения SmartBot.
 *
 * <p>Единственный модуль, которому разрешено видеть все адаптеры и слои:
 * здесь происходит wiring бинов и запуск Spring Boot.
 */
@SpringBootApplication(scanBasePackages = "ru.smartbot")
public class SmartBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBotApplication.class, args);
    }
}

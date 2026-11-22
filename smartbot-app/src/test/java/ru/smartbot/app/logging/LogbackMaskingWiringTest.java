package ru.smartbot.app.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring-проверка logback.xml и logback-spring.xml (Story 1.1, AD-4): реальные
 * конфиги загружаются в изолированный LoggerContext, секреты в message/stacktrace/MDC
 * маскируются до вывода в console appender. logback.xml покрывает pre-Spring окно,
 * logback-spring.xml — Spring-инициализацию; оба обязаны маскировать одинаково.
 * Все секреты — фиктивные.
 *
 * <p>Тест подменяет System.out, поэтому берёт глобальный лок
 * {@link Resources#SYSTEM_OUT} (несовместим с параллельным выводом других тестов).
 */
@ResourceLock(Resources.SYSTEM_OUT)
class LogbackMaskingWiringTest {

    private static final String FAKE_BOT_TOKEN = "bot123456:ABC-DEF_x";

    @ParameterizedTest
    @ValueSource(strings = {"/logback.xml", "/logback-spring.xml"})
    @DisplayName("Console appender маскирует message, stacktrace и MDC по обоим конфигам")
    void consoleAppenderMasksSecretsBeforeOutput(String configResource) throws Exception {
        LoggerContext context = new LoggerContext();
        // Как при проде-инициализации через SLF4J ServiceLoader (logback 1.3+ больше
        // не использует StaticLoggerBinder): изолированный контекст получает свой MDC-адаптер.
        context.setMDCAdapter(new LogbackMDCAdapter());
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            // ConsoleAppender привязывается к System.out на start(): подменяем до doConfigure.
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogbackMaskingWiringTest.class.getResource(configResource));

            // Осознанная проверка статусов Joran: ERROR — падение конфигурации;
            // WARN тоже недопустим — конфиги обязаны быть чистыми, любой новый WARN
            // требует разбора и явного решения, а не молчаливого игнора.
            assertThat(context.getStatusManager().getCopyOfStatusList())
                    .noneMatch(status -> status.getLevel() == Status.ERROR);
            assertThat(context.getStatusManager().getCopyOfStatusList())
                    .noneMatch(status -> status.getLevel() == Status.WARN);

            context.getMDCAdapter().put("botToken", FAKE_BOT_TOKEN);
            Logger logger = context.getLogger("ru.smartbot.wiring");
            logger.info("wire: Authorization: Bearer xyz-secret");
            logger.info("parameterized token {}", FAKE_BOT_TOKEN);
            logger.error("delivery failed",
                    new RuntimeException("url https://api.telegram.org/" + FAKE_BOT_TOKEN + "/sendMessage"));
        } finally {
            System.setOut(originalOut);
            context.stop();
        }

        String output = buffer.toString(StandardCharsets.UTF_8);
        assertThat(output)
                .contains("Authorization: [REDACTED]")          // message
                .contains("parameterized token bot[REDACTED]")  // форматированные аргументы
                .contains("java.lang.RuntimeException")          // stacktrace отрендерен
                .contains("botToken=[REDACTED]")                 // MDC отрендерен и замаскирован
                .contains("bot[REDACTED]")                       // stacktrace замаскирован
                .doesNotContain("xyz-secret", "ABC-DEF_x", "bot123456:");
    }
}

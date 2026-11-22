package ru.smartbot.application.impl.request;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Исполнитель асинхронной генерации (AD-15: in-process + lease/recovery).
 *
 * <p>Виртуальные потоки: LLM-вызов блокирующий, задач немного; брокер не нужен
 * на initial release (Deferred).
 */
@Configuration
public class GenerationConfiguration {

    @Bean(name = "generationExecutor", destroyMethod = "close")
    public ExecutorService generationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

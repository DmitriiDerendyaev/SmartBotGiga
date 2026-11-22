package ru.smartbot.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import ru.smartbot.adapter.telegram.model.TelegramUpdate;
import ru.smartbot.application.api.telegram.PollingOffsetRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long polling Telegram (AD-23): at-least-once — offset подтверждается только
 * после персональной обработки update и персистится монотонно.
 *
 * <p>Включается флагом {@code smartbot.telegram.polling-enabled} (после проверки
 * VPN-маршрута, Epic 5). Один bot token = ровно один активный poller.
 */
@Component
public class TelegramPollingService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingService.class);

    private final TelegramApiClient api;
    private final InlineQueryHandler inlineQueryHandler;
    private final PollingOffsetRepository offsets;
    private final TelegramProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    public TelegramPollingService(TelegramApiClient api,
                                  InlineQueryHandler inlineQueryHandler,
                                  PollingOffsetRepository offsets,
                                  TelegramProperties properties) {
        this.api = api;
        this.inlineQueryHandler = inlineQueryHandler;
        this.offsets = offsets;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isPollingEnabled()) {
            log.info("Telegram polling выключен (smartbot.telegram.polling-enabled=false)");
            return;
        }
        if (properties.getBotToken() == null || properties.getBotToken().isBlank()) {
            log.warn("Telegram polling не запущен: bot token не задан");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        worker = Thread.ofVirtual().name("telegram-polling").start(this::loop);
        log.info("Telegram long polling запущен (timeout={}s)", properties.getPollTimeoutSeconds());
    }

    @Override
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    private void loop() {
        while (running.get()) {
            try {
                long offset = offsets.lastUpdateId().orElse(0L);
                List<TelegramUpdate> updates = api.getUpdates(offset);
                for (TelegramUpdate update : updates) {
                    if (!running.get()) {
                        break;
                    }
                    dispatch(update);
                    // at-least-once: offset фиксируется после обработки (ledger уже записан)
                    offsets.save(update.updateId() + 1);
                }
            } catch (RuntimeException e) {
                if (!running.get()) {
                    break;
                }
                log.warn("Ошибка long polling: {} — повтор через {}",
                        e.getClass().getSimpleName(), properties.getPollErrorBackoff());
                sleep(properties.getPollErrorBackoff().toMillis());
            }
        }
        log.info("Telegram long polling остановлен");
    }

    private void dispatch(TelegramUpdate update) {
        try {
            if (update.inlineQuery() != null) {
                inlineQueryHandler.handle(update);
            }
            // callback_query обрабатывается в story 1.5
        } catch (RuntimeException e) {
            // Ledger уже мог записать update; повторная доставка не создаст дубль.
            log.warn("update_id={} завершился ошибкой {}",
                    update.updateId(), e.getClass().getSimpleName());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package hello.tradexserver.openApi.webSocket;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 모든 WebSocket 연결이 공유하는 스케줄러.
 * - lightExecutor: ping, reconnect 등 경량 태스크 (논블로킹)
 * - heavyExecutor: REST API 호출 등 블로킹 가능성이 있는 태스크 (Binance keepAlive 등)
 */
@Slf4j
@Component
public class WebSocketScheduler {

    private final ScheduledExecutorService lightExecutor;
    private final ScheduledExecutorService heavyExecutor;

    public WebSocketScheduler() {
        int cores = Runtime.getRuntime().availableProcessors();
        this.lightExecutor = Executors.newScheduledThreadPool(Math.max(cores, 4));
        this.heavyExecutor = Executors.newScheduledThreadPool(2);
        log.info("WebSocketScheduler 초기화 - lightExecutor: {}스레드, heavyExecutor: 2스레드", Math.max(cores, 4));
    }

    public ScheduledFuture<?> schedulePing(Runnable task, long periodSeconds) {
        return lightExecutor.scheduleAtFixedRate(task, periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    public ScheduledFuture<?> scheduleReconnect(Runnable task, long delayMs) {
        return lightExecutor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> scheduleHeavyTask(Runnable task, long initialMinutes, long periodMinutes) {
        return heavyExecutor.scheduleAtFixedRate(task, initialMinutes, periodMinutes, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        log.info("WebSocketScheduler 종료 중...");
        lightExecutor.shutdownNow();
        heavyExecutor.shutdownNow();
    }
}

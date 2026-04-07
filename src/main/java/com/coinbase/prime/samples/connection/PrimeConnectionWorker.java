/*
 * Copyright 2026-present Coinbase Global, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.coinbase.prime.samples.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coinbase.prime.samples.config.CoinbasePrimeProperties;
import com.coinbase.prime.samples.service.OrderQueueService;
import com.coinbase.prime.samples.service.PrimeMessageProcessor;
import com.coinbase.prime.samples.service.SignatureService;
import com.coinbase.prime.samples.websocket.PrimeWebSocketEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of a single Coinbase Prime WebSocket connection
 * for a specific subset of product IDs.
 *
 * <p>Not a Spring bean — instances are created and managed by
 * {@link PrimeConnectionManager}. Each worker owns its own
 * {@link PrimeMessageProcessor} (for independent sequence tracking),
 * connection thread, and ping scheduler, while sharing the
 * {@link OrderQueueService} with all other workers.
 */
class PrimeConnectionWorker {

    private static final Logger log = LoggerFactory.getLogger(PrimeConnectionWorker.class);

    private static final List<String> CHANNELS = List.of("heartbeats", "orders");
    private static final long PING_INTERVAL_SECONDS = 30;

    private final int workerId;
    private final List<String> productIds;
    private final CoinbasePrimeProperties props;
    private final ClientManager clientManager;
    private final PrimeMessageProcessor processor;
    private final SignatureService signatureService;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /** Session for the current connection; null when disconnected. */
    private volatile Session currentSession;

    /** Health record registered with {@link ConnectionRegistry}; updated throughout the lifecycle. */
    private final ConnectionHealth health;

    /**
     * Failure reason set by the processor's reconnect callback before it closes the session.
     * Read by the connection loop after the close future completes to attribute the failure.
     */
    private volatile String processorFailureReason;

    private Thread connectionThread;
    private final ScheduledThreadPoolExecutor pingScheduler;
    private volatile ScheduledFuture<?> pingTask;

    PrimeConnectionWorker(int workerId,
                          List<String> productIds,
                          CoinbasePrimeProperties props,
                          ClientManager clientManager,
                          SignatureService signatureService,
                          ObjectMapper objectMapper,
                          OrderQueueService orderQueueService,
                          ConnectionRegistry registry) {
        this.workerId         = workerId;
        this.productIds       = productIds;
        this.props            = props;
        this.clientManager    = clientManager;
        this.signatureService = signatureService;
        this.objectMapper     = objectMapper;
        this.processor        = new PrimeMessageProcessor(objectMapper, orderQueueService);
        this.health           = registry.register(workerId, productIds);
        this.pingScheduler    = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "ws-ping-" + workerId);
            t.setDaemon(true);
            return t;
        });

        // Close the current session from a fresh virtual thread — Tyrus discourages
        // session.close() from within an @OnMessage callback on the same session.
        // Record the reason before closing so the connection loop can attribute the failure.
        processor.setReconnectCallback(reason -> {
            if (shutdown.get()) return;
            processorFailureReason = reason;
            Session s = currentSession;
            if (s != null && s.isOpen()) {
                Thread.ofVirtual().name("ws-reconnect-trigger-" + workerId).start(() -> {
                    try {
                        log.info("[Worker {}] Closing session to trigger reconnect", workerId);
                        s.close(new CloseReason(
                                CloseReason.CloseCodes.NORMAL_CLOSURE,
                                "Reconnect requested by message processor"));
                    } catch (Exception e) {
                        log.warn("[Worker {}] Error closing session for reconnect: {}",
                                workerId, e.getMessage());
                    }
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void start() {
        connectionThread = Thread.ofVirtual()
                .name("ws-connection-loop-" + workerId)
                .start(this::connectionLoop);
    }

    void shutdown() throws InterruptedException {
        log.info("[Worker {}] Shutdown initiated — unsubscribing...", workerId);
        shutdown.set(true);
        cancelPing();

        Session s = currentSession;
        if (s != null && s.isOpen()) {
            unsubscribeFromChannels(s);
            Thread.sleep(300); // flush unsubscribe frames before closing
            try {
                s.close(new CloseReason(
                        CloseReason.CloseCodes.NORMAL_CLOSURE, "Application shutdown"));
            } catch (Exception e) {
                log.warn("[Worker {}] Error closing session during shutdown: {}",
                        workerId, e.getMessage());
            }
        }

        if (connectionThread != null) {
            connectionThread.interrupt();
            connectionThread.join(5_000);
        }

        pingScheduler.shutdownNow();
        log.info("[Worker {}] Shutdown complete", workerId);
    }

    // -------------------------------------------------------------------------
    // Connection loop with exponential back-off
    // -------------------------------------------------------------------------

    private void connectionLoop() {
        long delayMs     = props.getReconnectInitialDelayMs();
        int  attempts    = 0;
        int  maxAttempts = props.getMaxReconnectAttempts();

        while (!shutdown.get()) {
            if (maxAttempts > 0 && attempts >= maxAttempts) {
                log.error("[Worker {}] Exceeded max reconnect attempts ({}). Stopping.",
                        workerId, maxAttempts);
                break;
            }

            if (attempts > 0) {
                log.info("[Worker {}] Reconnecting in {} ms (attempt {})",
                        workerId, delayMs, attempts);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                delayMs = Math.min(delayMs * 2, props.getReconnectMaxDelayMs());
            }

            if (shutdown.get()) break;

            // Create a fresh endpoint per connection to get a clean close-future
            PrimeWebSocketEndpoint endpoint = new PrimeWebSocketEndpoint(processor);
            Session session = null;

            try {
                URI uri = URI.create(props.getWebsocketUri());
                health.setStatus(ConnectionStatus.CONNECTING);
                log.info("[Worker {}] Connecting to {} for products: {}",
                        workerId, uri, productIds);

                // Blocking call — returns once the WebSocket handshake is complete
                session        = clientManager.connectToServer(endpoint, uri);
                currentSession = session;
                health.setStatus(ConnectionStatus.CONNECTED);
                log.info("[Worker {}] Connected: sessionId={}", workerId, session.getId());

                processor.resetSequenceNumbers();
                subscribeToChannels(session);
                schedulePing(session, endpoint);

                // Block until the session closes (clean, error, or forced reconnect)
                endpoint.getCloseFuture().get();

                // Attribute the failure reason: processor-triggered (sequence gap / server error)
                // takes priority; otherwise check for a transport error, then fall back to a
                // generic "connection closed" label.
                String reason = processorFailureReason;
                processorFailureReason = null;
                if (reason == null && endpoint.getTransportError() != null) {
                    reason = "WebSocket error: " + endpoint.getTransportError().getMessage();
                }
                if (reason == null) {
                    reason = "connection closed";
                }
                health.recordFailure(FailureType.from(reason), reason);
                log.info("[Worker {}] Session closed (reason='{}') — will reconnect", workerId, reason);

                // Reset back-off after a successful connection
                delayMs  = props.getReconnectInitialDelayMs();
                attempts = 0;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Tyrus/Grizzly can catch InterruptedException internally and re-throw it
                // as DeploymentException — detect this so we exit cleanly on shutdown.
                if (hasCause(e, InterruptedException.class)) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (shutdown.get()) {
                    // App is shutting down — connection failure is expected, not an error.
                    log.debug("[Worker {}] Connection closed during shutdown", workerId);
                    break;
                }
                String failMsg = "connect failed: " + e.getMessage();
                health.recordFailure(FailureType.from(failMsg), failMsg);
                log.error("[Worker {}] Connection attempt {} failed: {}",
                        workerId, attempts + 1, e.getMessage(), e);
                attempts++;
            } finally {
                cancelPing();
                currentSession = null;
                closeQuietly(session);
            }
        }

        log.info("[Worker {}] Connection loop exited", workerId);
    }

    // -------------------------------------------------------------------------
    // Subscribe / unsubscribe
    // -------------------------------------------------------------------------

    private void subscribeToChannels(Session session) throws Exception {
        String timestamp = epochSeconds();
        for (String channel : CHANNELS) {
            sendSubscriptionFrame("subscribe", channel, timestamp, session);
            log.info("[Worker {}] Subscribed to channel '{}'", workerId, channel);
        }
    }

    private void unsubscribeFromChannels(Session session) {
        if (session == null || !session.isOpen()) return;
        String timestamp = epochSeconds();
        for (String channel : CHANNELS) {
            try {
                sendSubscriptionFrame("unsubscribe", channel, timestamp, session);
                log.info("[Worker {}] Unsubscribed from channel '{}'", workerId, channel);
            } catch (Exception e) {
                log.warn("[Worker {}] Failed to unsubscribe from '{}': {}",
                        workerId, channel, e.getMessage());
            }
        }
    }

    private void sendSubscriptionFrame(String type, String channel, String timestamp,
                                       Session session) throws Exception {
        String signature = signatureService.sign(
                channel,
                props.getAccessKey(),
                props.getApiKeyId(),
                timestamp,
                props.getPortfolioId(),
                productIds,
                props.getSecretKey());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type",         type);
        payload.put("channel",      channel);
        payload.put("access_key",   props.getAccessKey());
        payload.put("api_key_id",   props.getApiKeyId());
        payload.put("timestamp",    timestamp);
        payload.put("passphrase",   props.getPassphrase());
        payload.put("signature",    signature);
        payload.put("portfolio_id", props.getPortfolioId());
        payload.put("product_ids",  productIds);

        session.getBasicRemote().sendText(objectMapper.writeValueAsString(payload));
    }

    // -------------------------------------------------------------------------
    // Ping
    // -------------------------------------------------------------------------

    private void schedulePing(Session session, PrimeWebSocketEndpoint endpoint) {
        cancelPing();
        pingTask = pingScheduler.scheduleAtFixedRate(
                () -> endpoint.sendPing(session),
                PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelPing() {
        ScheduledFuture<?> task = pingTask;
        if (task != null) {
            task.cancel(false);
            pingTask = null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String epochSeconds() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private static void closeQuietly(Session session) {
        if (session != null && session.isOpen()) {
            try { session.close(); } catch (Exception ignored) {}
        }
    }

    /** Returns true if any exception in the cause chain is an instance of {@code type}. */
    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        Throwable cause = t;
        while (cause != null) {
            if (type.isInstance(cause)) return true;
            cause = cause.getCause();
        }
        return false;
    }
}

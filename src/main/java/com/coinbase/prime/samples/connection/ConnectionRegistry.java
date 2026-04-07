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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Singleton registry that tracks the health of every {@link PrimeConnectionWorker}.
 *
 * <p>Workers register themselves on creation via {@link #register} and update their
 * {@link ConnectionHealth} records directly. This service logs a summary of all
 * connections — current status and cumulative failure counts — once per minute.
 *
 * <p>A <em>failure</em> is any of:
 * <ul>
 *   <li>WebSocket transport error ({@code @OnError})</li>
 *   <li>Unexpected session close ({@code @OnClose})</li>
 *   <li>Sequence gap detected by {@link com.coinbase.prime.samples.service.PrimeMessageProcessor}</li>
 *   <li>Server-sent {@code {"type":"error"}} frame</li>
 *   <li>Failed connection attempt ({@code connectToServer} throws)</li>
 * </ul>
 */
@Service
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);
    private static final long REPORT_INTERVAL_SECONDS = 60;

    private final ConcurrentHashMap<Integer, ConnectionHealth> registry = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "connection-health-reporter");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void startReporting() {
        scheduler.scheduleAtFixedRate(
                this::logReport,
                REPORT_INTERVAL_SECONDS,
                REPORT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * Registers a new worker and returns its {@link ConnectionHealth} record.
     * The worker updates this object directly throughout its lifecycle.
     */
    ConnectionHealth register(int workerId, List<String> productIds) {
        ConnectionHealth health = new ConnectionHealth(workerId, productIds);
        registry.put(workerId, health);
        return health;
    }

    /** Logs a one-line summary per connection, sorted by worker ID. */
    void logReport() {
        if (registry.isEmpty()) return;

        StringBuilder sb = new StringBuilder(
                "Connection health report (" + registry.size() + " connection(s)):\n");

        registry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    ConnectionHealth h = e.getValue();
                    sb.append(String.format("  Worker %-2d [%s]  status=%-10s  failures=%d",
                            h.workerId,
                            String.join(",", h.productIds),
                            h.status,
                            h.failures.get()));

                    String breakdown = h.countsByType.entrySet().stream()
                            .filter(te -> te.getValue().get() > 0)
                            .map(te -> te.getKey().name() + "=" + te.getValue().get())
                            .collect(Collectors.joining(", "));
                    if (!breakdown.isEmpty()) {
                        sb.append("  [").append(breakdown).append(']');
                    }

                    if (h.lastFailureAt != null) {
                        sb.append(String.format("  lastFailure=%s  reason='%s'",
                                h.lastFailureAt, h.lastFailureReason));
                    }
                    sb.append('\n');
                });

        log.info(sb.toString().stripTrailing());
    }
}

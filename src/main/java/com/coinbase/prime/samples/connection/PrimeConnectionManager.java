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
import com.coinbase.prime.samples.service.SignatureService;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Orchestrates multiple {@link PrimeConnectionWorker} instances, one per batch
 * of up to {@value #MAX_PRODUCTS_PER_CONNECTION} product IDs.
 *
 * <p>Products configured in {@code coinbase.prime.product-ids} are partitioned
 * into groups of at most 10 before the application starts. A dedicated WebSocket
 * connection — with its own message processor, connection thread, and ping
 * scheduler — is created for each partition. All connections share the same
 * {@link OrderQueueService}.
 *
 * <p>Implements {@link ApplicationRunner} so workers start after the Spring
 * context is fully initialised, and {@link DisposableBean} so all connections
 * are cleanly closed on Ctrl-C / JVM shutdown.
 */
@Service
public class PrimeConnectionManager implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PrimeConnectionManager.class);

    static final int MAX_PRODUCTS_PER_CONNECTION = 10;

    private final CoinbasePrimeProperties props;
    private final ClientManager clientManager;
    private final SignatureService signatureService;
    private final ObjectMapper objectMapper;
    private final OrderQueueService orderQueueService;
    private final ConnectionRegistry connectionRegistry;

    private List<PrimeConnectionWorker> workers;

    /** Released by {@link #destroy()} to unblock the main thread and allow Spring to shut down. */
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public PrimeConnectionManager(CoinbasePrimeProperties props,
                                  ClientManager clientManager,
                                  SignatureService signatureService,
                                  ObjectMapper objectMapper,
                                  OrderQueueService orderQueueService,
                                  ConnectionRegistry connectionRegistry) {
        this.props               = props;
        this.clientManager       = clientManager;
        this.signatureService    = signatureService;
        this.objectMapper        = objectMapper;
        this.orderQueueService   = orderQueueService;
        this.connectionRegistry  = connectionRegistry;
    }

    // -------------------------------------------------------------------------
    // ApplicationRunner — starts workers after context is ready
    // -------------------------------------------------------------------------

    @Override
    public void run(ApplicationArguments args) {
        List<List<String>> partitions = partition(props.getProductIds(), MAX_PRODUCTS_PER_CONNECTION);
        log.info("Starting {} WebSocket connection(s) for {} product(s)",
                partitions.size(), props.getProductIds().size());

        workers = new ArrayList<>(partitions.size());
        for (int i = 0; i < partitions.size(); i++) {
            PrimeConnectionWorker worker = new PrimeConnectionWorker(
                    i,
                    partitions.get(i),
                    props,
                    clientManager,
                    signatureService,
                    objectMapper,
                    orderQueueService,
                    connectionRegistry);
            workers.add(worker);
            worker.start();
        }

        // Block the main thread so the process stays alive.
        // Spring Boot with web-application-type=none exits as soon as run() returns;
        // virtual threads are daemon threads and do not prevent JVM exit on their own.
        // destroy() releases this latch when Ctrl-C / shutdown is received.
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // DisposableBean — clean shutdown triggered by Ctrl-C or JVM shutdown hook
    // -------------------------------------------------------------------------

    @Override
    public void destroy() throws Exception {
        shutdownLatch.countDown(); // unblock the main thread
        if (workers == null) return;
        for (PrimeConnectionWorker worker : workers) {
            worker.shutdown();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Partitions {@code items} into sub-lists of at most {@code size} elements.
     * Returns a list containing one empty list when {@code items} is empty,
     * so that at least one connection is always started.
     */
    static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            result.add(items.subList(i, Math.min(i + size, items.size())));
        }
        if (result.isEmpty()) {
            result.add(List.of());
        }
        return result;
    }
}

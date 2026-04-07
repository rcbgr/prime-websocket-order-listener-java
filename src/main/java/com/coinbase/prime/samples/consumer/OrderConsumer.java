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

package com.coinbase.prime.samples.consumer;

import com.coinbase.prime.samples.model.Order;
import com.coinbase.prime.samples.service.OrderQueueService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drains the {@link OrderQueueService} on a dedicated virtual thread and logs
 * details for each order.
 *
 * <p>The consumer starts as soon as the Spring context finishes initialising
 * (constructor → virtual thread launch) and stops cleanly on {@link PreDestroy}.
 */
@Service
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private static final java.util.Set<String> TERMINAL_STATUSES =
            java.util.Set.of("FILLED", "CANCELLED", "EXPIRED", "REJECTED", "FAILED");

    private final OrderQueueService orderQueueService;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread consumerThread;

    /** Tracks the last known status per order ID to detect status changes. */
    private final ConcurrentHashMap<String, String> lastStatusByOrderId = new ConcurrentHashMap<>();

    public OrderConsumer(OrderQueueService orderQueueService) {
        this.orderQueueService = orderQueueService;
        this.consumerThread    = Thread.ofVirtual()
                .name("order-consumer")
                .start(this::consumeOrders);
    }

    // -------------------------------------------------------------------------
    // Consumer loop
    // -------------------------------------------------------------------------

    private void consumeOrders() {
        log.info("Order consumer started");
        while (running.get()) {
            try {
                Order order = orderQueueService.poll(1, TimeUnit.SECONDS);
                if (order != null) {
                    logOrder(order);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Order consumer stopped");
    }

    private void logOrder(Order order) {
        String label;
        if (isFill(order.getCumQty())) {
            label = "FILL";
        } else {
            String previous = lastStatusByOrderId.put(order.getOrderId(), order.getStatus());
            label = (previous != null && !previous.equals(order.getStatus()))
                    ? "ORDER status changed" : "ORDER";
        }
        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            lastStatusByOrderId.remove(order.getOrderId());
        }
        log.info("{}: orderId={} clientOrderId={} productId={} side={} orderType={} "
                        + "status={} cumQty={} leavesQty={} avgPx={} netAvgPx={} fees={}",
                label,
                order.getOrderId(),
                order.getClientOrderId(),
                order.getProductId(),
                order.getSide(),
                order.getOrderType(),
                order.getStatus(),
                order.getCumQty(),
                order.getLeavesQty(),
                order.getAvgPx(),
                order.getNetAvgPx(),
                order.getFees());
    }

    private static boolean isFill(String cumQty) {
        if (cumQty == null || cumQty.isEmpty()) return false;
        try {
            return new BigDecimal(cumQty).compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    @PreDestroy
    public void shutdown() {
        log.info("Order consumer shutting down");
        running.set(false);
        consumerThread.interrupt();
    }
}

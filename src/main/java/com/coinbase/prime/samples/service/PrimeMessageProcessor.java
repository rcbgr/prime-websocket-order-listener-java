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

package com.coinbase.prime.samples.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coinbase.prime.samples.model.Order;
import com.coinbase.prime.samples.model.PrimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Core message processing logic for Coinbase Prime WebSocket messages.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Deserialise raw JSON frames into {@link PrimeMessage}</li>
 *   <li>Track per-channel sequence numbers and trigger reconnect on gaps</li>
 *   <li>Route order events to {@link OrderQueueService}</li>
 *   <li>Handle server-sent error frames</li>
 * </ul>
 *
 * <p>Not a Spring bean — one instance is created per {@link com.coinbase.prime.samples.connection.PrimeConnectionWorker}
 * so that each connection tracks its own sequence numbers independently.
 *
 * <p>This class is intentionally free of any WebSocket lifecycle code so that
 * it can be unit-tested by passing raw JSON strings without a live connection.
 */
public class PrimeMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(PrimeMessageProcessor.class);

    static final String CHANNEL_HEARTBEATS    = "heartbeats";
    static final String CHANNEL_ORDERS        = "orders";
    static final String CHANNEL_SUBSCRIPTIONS = "subscriptions";
    static final String TYPE_ERROR            = "error";

    /** Sentinel indicating no message has yet been seen for a channel. */
    private static final long UNINITIALIZED = -1L;

    private final ObjectMapper objectMapper;
    private final OrderQueueService orderQueueService;

    /**
     * Called when a reconnect is required. Receives a human-readable failure reason
     * (e.g. "sequence gap") so callers can record it in {@link com.coinbase.prime.samples.connection.ConnectionHealth}.
     * Set by {@link com.coinbase.prime.samples.connection.PrimeConnectionWorker}.
     */
    private volatile Consumer<String> reconnectCallback;

    /**
     * Global sequence counter for this connection. The Coinbase Prime WebSocket uses a
     * single monotonically increasing counter across all channels — heartbeats, orders,
     * and subscriptions all share the same sequence space. Reset on each reconnection.
     */
    private final AtomicLong lastSequence = new AtomicLong(UNINITIALIZED);

    public PrimeMessageProcessor(ObjectMapper objectMapper, OrderQueueService orderQueueService) {
        this.objectMapper      = objectMapper;
        this.orderQueueService = orderQueueService;
    }

    public void setReconnectCallback(Consumer<String> callback) {
        this.reconnectCallback = callback;
    }

    /** Called by the connection manager after each successful reconnect. */
    public void resetSequenceNumbers() {
        lastSequence.set(UNINITIALIZED);
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Process a single raw WebSocket text frame.
     *
     * @return {@code true} if processing was normal; {@code false} if a reconnect
     *         was triggered (sequence gap or server error)
     */
    public boolean processMessage(String json) {
        PrimeMessage message;
        try {
            message = objectMapper.readValue(json, PrimeMessage.class);
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message: {}", json, e);
            return true; // Malformed frame — do not reconnect, keep connection alive
        }

        // Server-sent error frame
        if (TYPE_ERROR.equals(message.getType())) {
            log.error("Received error from Coinbase Prime: {}", message.getMessage());
            triggerReconnect("server error: " + message.getMessage());
            return false;
        }

        String channel = message.getChannel();
        if (channel == null) {
            log.warn("Message missing 'channel' field — ignoring: {}", json);
            return true;
        }

        if (!checkSequence(message.getSequenceNum())) {
            return false; // Reconnect already triggered inside checkSequence
        }

        switch (channel) {
            case CHANNEL_HEARTBEATS    -> handleHeartbeat(message);
            case CHANNEL_ORDERS        -> handleOrders(message);
            case CHANNEL_SUBSCRIPTIONS -> log.info("Subscription confirmed: {}", json);
            default                    -> log.debug("Ignoring unknown channel: {}", channel);
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Sequence number validation
    // -------------------------------------------------------------------------

    /**
     * Validates that {@code sequenceNum} is exactly one greater than the last received
     * value. The Coinbase Prime WebSocket uses a single monotonically increasing counter
     * shared across all channels on a connection.
     *
     * <ul>
     *   <li>First message on the connection: accepted as the baseline.</li>
     *   <li>Gap (received &gt; expected): error logged, reconnect triggered.</li>
     *   <li>Out-of-order (received &lt; expected): warning logged, message kept.</li>
     * </ul>
     *
     * @return {@code false} if a reconnect was triggered, {@code true} otherwise
     */
    boolean checkSequence(long sequenceNum) {
        long previous = lastSequence.get();

        if (previous == UNINITIALIZED) {
            lastSequence.set(sequenceNum);
            log.debug("Baseline sequence set to {}", sequenceNum);
            return true;
        }

        long expected = previous + 1L;

        if (sequenceNum == expected) {
            lastSequence.set(sequenceNum);
            return true;
        }

        if (Long.compareUnsigned(sequenceNum, expected) < 0) {
            log.warn("Out-of-order message: expected={}, received={}", expected, sequenceNum);
            return true;
        }

        // Gap detected: one or more messages were dropped
        long dropped = sequenceNum - expected;
        log.error("Sequence gap: expected={}, received={}, dropped={}",
                expected, sequenceNum, Long.toUnsignedString(dropped));
        triggerReconnect("sequence gap (dropped=" + Long.toUnsignedString(dropped) + ")");
        return false;
    }

    // -------------------------------------------------------------------------
    // Channel handlers
    // -------------------------------------------------------------------------

    private void handleHeartbeat(PrimeMessage message) {
        if (message.getEvents() == null || message.getEvents().isEmpty()) return;
        JsonNode event = message.getEvents().get(0);
        log.debug("Heartbeat: currentTime={} counter={}",
                event.path("current_time").asText(),
                event.path("heartbeat_counter").asLong());
    }

    private void handleOrders(PrimeMessage message) {
        if (message.getEvents() == null) return;

        for (JsonNode event : message.getEvents()) {
            JsonNode ordersNode = event.get("orders");
            if (ordersNode == null || !ordersNode.isArray()) continue;

            for (JsonNode orderNode : ordersNode) {
                try {
                    Order order = objectMapper.treeToValue(orderNode, Order.class);
                    orderQueueService.enqueue(order);
                    log.debug("Enqueued order: orderId={} productId={} status={}",
                            order.getOrderId(), order.getProductId(), order.getStatus());
                } catch (Exception e) {
                    log.error("Failed to deserialise order node", e);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reconnect
    // -------------------------------------------------------------------------

    private void triggerReconnect(String reason) {
        Consumer<String> cb = reconnectCallback;
        if (cb != null) {
            cb.accept(reason);
        } else {
            log.warn("Reconnect triggered but no callback is registered");
        }
    }
}

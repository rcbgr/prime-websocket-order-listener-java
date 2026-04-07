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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coinbase.prime.samples.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests {@link PrimeMessageProcessor} without any live WebSocket connection.
 * Raw JSON strings are fed directly into {@link PrimeMessageProcessor#processMessage(String)}.
 */
@ExtendWith(MockitoExtension.class)
class PrimeMessageProcessorTest {

    @Mock
    private OrderQueueService orderQueueService;

    private PrimeMessageProcessor processor;
    private AtomicBoolean reconnectCalled;

    @BeforeEach
    void setUp() {
        processor = new PrimeMessageProcessor(new ObjectMapper(), orderQueueService);
        reconnectCalled = new AtomicBoolean(false);
        processor.setReconnectCallback(reason -> reconnectCalled.set(true));
    }

    // -------------------------------------------------------------------------
    // Heartbeat
    // -------------------------------------------------------------------------

    @Test
    void processMessage_heartbeat_returnsTrueAndDoesNotQueue() {
        boolean result = processor.processMessage(heartbeatJson(1));

        assertThat(result).isTrue();
        verifyNoInteractions(orderQueueService);
        assertThat(reconnectCalled).isFalse();
    }

    @Test
    void processMessage_heartbeat_tracksSequence() {
        processor.processMessage(heartbeatJson(1));
        boolean result = processor.processMessage(heartbeatJson(2));

        assertThat(result).isTrue();
        assertThat(reconnectCalled).isFalse();
    }

    // -------------------------------------------------------------------------
    // Orders
    // -------------------------------------------------------------------------

    @Test
    void processMessage_orders_enqueueSingleOrder() {
        boolean result = processor.processMessage(ordersSnapshotJson(0, "ord-1", "BTC-USD", "buy"));

        assertThat(result).isTrue();
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderQueueService).enqueue(captor.capture());
        Order order = captor.getValue();
        assertThat(order.getOrderId()).isEqualTo("ord-1");
        assertThat(order.getProductId()).isEqualTo("BTC-USD");
        assertThat(order.getSide()).isEqualTo("buy");
    }

    @Test
    void processMessage_orders_enqueueMultipleOrdersInSingleEvent() {
        String json = """
                {
                  "channel": "orders",
                  "sequence_num": 0,
                  "events": [
                    {
                      "type": "snapshot",
                      "orders": [
                        {"order_id": "id-1", "product_id": "BTC-USD", "side": "buy",
                         "status": "OPEN", "cum_qty": "0", "leaves_qty": "1",
                         "avg_px": "0", "net_avg_px": "0", "fees": "0"},
                        {"order_id": "id-2", "product_id": "ETH-USD", "side": "sell",
                         "status": "FILLED", "cum_qty": "5", "leaves_qty": "0",
                         "avg_px": "3000", "net_avg_px": "2990", "fees": "10"}
                      ]
                    }
                  ]
                }
                """;
        processor.processMessage(json);

        verify(orderQueueService, times(2)).enqueue(any(Order.class));
    }

    // -------------------------------------------------------------------------
    // Subscriptions channel
    // -------------------------------------------------------------------------

    @Test
    void processMessage_subscriptions_duplicateSeqIsOutOfOrderNotGap() {
        processor.processMessage(subscriptionConfirmationJson(0));
        // Sending the same seq again is out-of-order — warn but do not reconnect.
        processor.processMessage(subscriptionConfirmationJson(0));

        assertThat(reconnectCalled).isFalse();
    }

    // -------------------------------------------------------------------------
    // Error message
    // -------------------------------------------------------------------------

    @Test
    void processMessage_error_triggersReconnect() {
        String errorJson = """
                {"type": "error", "message": "ErrSlowConsume"}
                """;
        boolean result = processor.processMessage(errorJson);

        assertThat(result).isFalse();
        assertThat(reconnectCalled).isTrue();
    }

    // -------------------------------------------------------------------------
    // Sequence number validation
    // -------------------------------------------------------------------------

    @Test
    void checkSequence_acceptsFirstMessageAsBaseline() {
        boolean ok = processor.checkSequence(42L);
        assertThat(ok).isTrue();
        assertThat(reconnectCalled).isFalse();
    }

    @Test
    void checkSequence_acceptsSequentialMessage() {
        processor.checkSequence(1L);
        boolean ok = processor.checkSequence(2L);
        assertThat(ok).isTrue();
        assertThat(reconnectCalled).isFalse();
    }

    @Test
    void checkSequence_triggersReconnectOnGap() {
        processor.checkSequence(0L);
        boolean ok = processor.checkSequence(5L);

        assertThat(ok).isFalse();
        assertThat(reconnectCalled).isTrue();
    }

    @Test
    void checkSequence_warnsOnOutOfOrderButDoesNotReconnect() {
        processor.checkSequence(5L);
        boolean ok = processor.checkSequence(3L);

        assertThat(ok).isTrue();
        assertThat(reconnectCalled).isFalse();
    }

    @Test
    void checkSequence_isGlobalAcrossAllChannelMessages() {
        // Heartbeat at seq=10 sets baseline; next message (any channel) must be seq=11.
        processor.checkSequence(10L);
        boolean ok = processor.checkSequence(11L);

        assertThat(ok).isTrue();
        assertThat(reconnectCalled).isFalse();
    }

    @Test
    void resetSequenceNumbers_clearsGlobalState() {
        processor.checkSequence(10L);
        processor.resetSequenceNumbers();

        // After reset, any sequence number is accepted as a new baseline.
        boolean ok = processor.checkSequence(999L);
        assertThat(ok).isTrue();
        assertThat(reconnectCalled).isFalse();
    }

    // -------------------------------------------------------------------------
    // Malformed input
    // -------------------------------------------------------------------------

    @Test
    void processMessage_invalidJson_returnsTrueWithoutCrash() {
        boolean result = processor.processMessage("this is not json {{{}}}");
        assertThat(result).isTrue();
        assertThat(reconnectCalled).isFalse();
    }

    @Test
    void processMessage_emptyMessage_doesNotThrow() {
        boolean result = processor.processMessage("{}");
        assertThat(result).isTrue();
    }

    // -------------------------------------------------------------------------
    // JSON builders
    // -------------------------------------------------------------------------

    private static String heartbeatJson(long seq) {
        return """
                {
                  "channel": "heartbeats",
                  "timestamp": "2026-01-25T20:53:01Z",
                  "sequence_num": %d,
                  "events": [
                    {"current_time": "2026-01-25 20:53:01 +0000 UTC", "heartbeat_counter": 1}
                  ]
                }
                """.formatted(seq);
    }

    private static String ordersSnapshotJson(long seq, String orderId,
                                             String productId, String side) {
        return """
                {
                  "channel": "orders",
                  "timestamp": "2026-01-25T21:16:07Z",
                  "sequence_num": %d,
                  "events": [
                    {
                      "type": "snapshot",
                      "orders": [
                        {
                          "order_id": "%s",
                          "client_order_id": "client-1",
                          "product_id": "%s",
                          "side": "%s",
                          "status": "OPEN",
                          "order_type": "market",
                          "cum_qty": "0",
                          "leaves_qty": "1",
                          "avg_px": "0",
                          "net_avg_px": "0",
                          "fees": "0"
                        }
                      ]
                    }
                  ]
                }
                """.formatted(seq, orderId, productId, side);
    }

    private static String subscriptionConfirmationJson(long seq) {
        return """
                {
                  "channel": "subscriptions",
                  "timestamp": "2026-01-25T21:16:07Z",
                  "sequence_num": %d,
                  "events": [
                    {"subscriptions": {"orders": ["port-id"]}}
                  ]
                }
                """.formatted(seq);
    }
}

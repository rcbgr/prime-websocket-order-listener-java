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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Tests {@link OrderConsumer} against a mock {@link OrderQueueService}.
 * No WebSocket connection is required.
 */
@ExtendWith(MockitoExtension.class)
class OrderConsumerTest {

    @Mock
    private OrderQueueService orderQueueService;

    private OrderConsumer consumer;

    @BeforeEach
    void setUp() throws InterruptedException {
        when(orderQueueService.poll(anyLong(), any(TimeUnit.class))).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) consumer.shutdown();
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void consumer_startsAndDrainsQueue() throws InterruptedException {
        Order order = buildOrder("ord-42", "BTC-USD", "buy", "OPEN");

        when(orderQueueService.poll(anyLong(), any(TimeUnit.class)))
                .thenReturn(order)
                .thenReturn(null);

        consumer = new OrderConsumer(orderQueueService);

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        verify(orderQueueService, atLeastOnce()).poll(anyLong(), any(TimeUnit.class)));
    }

    @Test
    void consumer_handlesNullPollGracefully() throws InterruptedException {
        consumer = new OrderConsumer(orderQueueService);
        Thread.sleep(150);

        assertThat(consumer).isNotNull();
        verify(orderQueueService, atLeastOnce()).poll(anyLong(), any(TimeUnit.class));
    }

    @Test
    void shutdown_stopsConsumerThread() throws InterruptedException {
        consumer = new OrderConsumer(orderQueueService);
        Thread.sleep(50);
        consumer.shutdown();

        int callsBefore = mockingDetails(orderQueueService).getInvocations().size();
        Thread.sleep(200);
        int callsAfter = mockingDetails(orderQueueService).getInvocations().size();
        assertThat(callsAfter).isEqualTo(callsBefore);
    }

    private static Order buildOrder(String id, String productId, String side, String status) {
        Order o = new Order();
        o.setOrderId(id);
        o.setClientOrderId("client-" + id);
        o.setProductId(productId);
        o.setSide(side);
        o.setStatus(status);
        o.setOrderType("market");
        o.setCumQty("0");
        o.setLeavesQty("1");
        o.setAvgPx("0");
        o.setNetAvgPx("0");
        o.setFees("0");
        return o;
    }
}

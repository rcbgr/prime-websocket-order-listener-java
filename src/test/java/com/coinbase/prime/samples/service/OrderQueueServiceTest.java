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

import com.coinbase.prime.samples.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrderQueueServiceTest {

    private OrderQueueService queue;

    @BeforeEach
    void setUp() {
        queue = new OrderQueueService();
    }

    @Test
    void enqueue_andPoll_roundTrip() throws InterruptedException {
        Order order = orderWithId("ord-1");
        queue.enqueue(order);

        Order polled = queue.poll(100, TimeUnit.MILLISECONDS);
        assertThat(polled).isNotNull();
        assertThat(polled.getOrderId()).isEqualTo("ord-1");
    }

    @Test
    void poll_returnsNull_whenQueueIsEmpty() throws InterruptedException {
        Order polled = queue.poll(50, TimeUnit.MILLISECONDS);
        assertThat(polled).isNull();
    }

    @Test
    void enqueue_dropsOrdersWhenFull() {
        for (int i = 0; i < OrderQueueService.QUEUE_CAPACITY; i++) {
            queue.enqueue(orderWithId("ord-" + i));
        }
        assertThat(queue.remainingCapacity()).isZero();

        queue.enqueue(orderWithId("dropped"));
        assertThat(queue.size()).isEqualTo(OrderQueueService.QUEUE_CAPACITY);
    }

    @Test
    void size_reflects_currentCount() {
        assertThat(queue.size()).isZero();
        queue.enqueue(orderWithId("a"));
        queue.enqueue(orderWithId("b"));
        assertThat(queue.size()).isEqualTo(2);
    }

    @Test
    void remainingCapacity_decreases_afterEnqueue() {
        int before = queue.remainingCapacity();
        queue.enqueue(orderWithId("x"));
        assertThat(queue.remainingCapacity()).isEqualTo(before - 1);
    }

    private static Order orderWithId(String id) {
        Order o = new Order();
        o.setOrderId(id);
        return o;
    }
}

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
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe bounded queue that buffers {@link Order} objects between the
 * WebSocket message handler (producer) and the
 * {@link com.coinbase.prime.samples.consumer.OrderConsumer} (consumer).
 *
 * <p>Capacity is fixed at 5,000 entries. When full, {@link #enqueue} returns
 * {@code false} without blocking so the caller can apply back-pressure
 * (e.g. disconnect the WebSocket and reconnect after the consumer drains).
 */
@Service
public class OrderQueueService {

    static final int QUEUE_CAPACITY = 5_000;

    private final LinkedBlockingQueue<Order> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    /**
     * Enqueue an order without blocking.
     *
     * @return {@code true} if the order was accepted; {@code false} if the queue is full
     */
    public boolean enqueue(Order order) {
        return queue.offer(order);
    }

    /**
     * Retrieve and remove the head of the queue, waiting up to {@code timeout}
     * if necessary for an element to become available.
     *
     * @return the next order, or {@code null} if the timeout elapsed
     */
    public Order poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}

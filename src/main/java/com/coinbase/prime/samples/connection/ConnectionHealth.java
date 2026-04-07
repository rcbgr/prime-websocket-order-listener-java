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

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable health record for a single {@link PrimeConnectionWorker}.
 * Updated directly by the worker; read periodically by {@link ConnectionRegistry}.
 */
class ConnectionHealth {

    final int workerId;
    final List<String> productIds;

    volatile ConnectionStatus status = ConnectionStatus.CONNECTING;
    final AtomicLong failures = new AtomicLong(0);
    final EnumMap<FailureType, AtomicLong> countsByType = new EnumMap<>(FailureType.class);
    volatile Instant lastFailureAt;
    volatile String lastFailureReason;

    ConnectionHealth(int workerId, List<String> productIds) {
        this.workerId   = workerId;
        this.productIds = List.copyOf(productIds);
        for (FailureType type : FailureType.values()) {
            countsByType.put(type, new AtomicLong(0));
        }
    }

    void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    /** Records a failure, increments the total and per-type counts, and transitions to CLOSED. */
    void recordFailure(FailureType type, String reason) {
        failures.incrementAndGet();
        countsByType.get(type).incrementAndGet();
        lastFailureAt     = Instant.now();
        lastFailureReason = reason;
        status            = ConnectionStatus.CLOSED;
    }
}

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

/**
 * Categorises the failure events recorded in {@link ConnectionHealth}.
 *
 * <p>Use {@link #from(String)} to classify a free-form reason string returned
 * by {@link com.coinbase.prime.samples.service.PrimeMessageProcessor} or produced
 * by the connection worker itself.
 */
enum FailureType {

    /** Server sent {@code {"type":"error","message":"ErrSlowConsume"}}. */
    SERVER_ERROR_SLOW_CONSUME,

    /** Server sent {@code {"type":"error","message":"ErrSlowRead"}}. */
    SERVER_ERROR_SLOW_READ,

    /** A sequence number gap was detected, indicating dropped messages. */
    SEQUENCE_GAP,

    /** TCP/IP connection was reset by the peer or an intermediate network device. */
    CONNECTION_RESET,

    /** TLS/SSL handshake or certificate failure. */
    TLS_ERROR,

    /** Server or client issued a normal WebSocket close (1000/1001). */
    DISCONNECT_REQUESTED,

    /** Incoming WebSocket frame exceeded the configured maximum message size. */
    MESSAGE_TOO_BIG,

    /** Order queue was at capacity; the connection was closed to apply back-pressure. */
    QUEUE_FULL,

    /** Any failure that does not match a more specific category. */
    UNKNOWN;


    /**
     * Classifies a reason string into the most specific {@link FailureType}.
     * Matching is case-insensitive and based on well-known substrings from
     * Coinbase Prime server errors, Tyrus transport exceptions, and worker labels.
     */
    static FailureType from(String reason) {
        if (reason == null) return UNKNOWN;
        String lower = reason.toLowerCase();

        if (lower.contains("queue full"))                              return QUEUE_FULL;
        if (lower.contains("sequence gap"))                           return SEQUENCE_GAP;
        if (lower.contains("errslowconsume") ||
                lower.contains("slow consume"))                       return SERVER_ERROR_SLOW_CONSUME;
        if (lower.contains("errslowread") ||
                lower.contains("slow read"))                          return SERVER_ERROR_SLOW_READ;
        if (lower.contains("connection reset") ||
                lower.contains("connection closed") ||
                lower.contains("connect failed"))                     return CONNECTION_RESET;
        if (lower.contains("tls") || lower.contains("ssl"))          return TLS_ERROR;
        if (lower.contains("disconnect") ||
                lower.contains("normal closure") ||
                lower.contains("going away"))                         return DISCONNECT_REQUESTED;
        if (lower.contains("message too big") ||
                lower.contains("too large") ||
                lower.contains("frame size"))                         return MESSAGE_TOO_BIG;

        return UNKNOWN;
    }
}

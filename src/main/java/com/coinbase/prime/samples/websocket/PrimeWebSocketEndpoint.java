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

package com.coinbase.prime.samples.websocket;

import com.coinbase.prime.samples.service.PrimeMessageProcessor;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * Jakarta WebSocket {@code @ClientEndpoint} that handles the low-level lifecycle
 * of a single connection to Coinbase Prime.
 *
 * <p><b>Not a Spring bean.</b> A fresh instance is created by
 * {@link com.coinbase.prime.samples.connection.PrimeConnectionWorker} for each connection
 * attempt, which avoids CGLIB-proxy interference with Jakarta's annotation scanning
 * and simplifies per-connection state (e.g. the close future).
 *
 * <p>All business logic is delegated to the worker-owned
 * {@link PrimeMessageProcessor}; this class only bridges the Jakarta callbacks.
 */
@ClientEndpoint
public class PrimeWebSocketEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PrimeWebSocketEndpoint.class);

    private final PrimeMessageProcessor processor;

    /**
     * Completes (with {@code null}) when the session closes — whether due to a
     * clean shutdown, a network error, or an application-initiated close for
     * reconnect purposes.
     *
     * {@link com.coinbase.prime.samples.connection.PrimeConnectionManager} blocks on
     * this future to know when to reconnect.
     */
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    /** Set in {@link #onError} so the worker can distinguish transport errors from clean closes. */
    private volatile Throwable transportError;

    public PrimeWebSocketEndpoint(PrimeMessageProcessor processor) {
        this.processor = processor;
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        log.info("WebSocket session opened: id={}", session.getId());
    }

    @OnMessage
    public void onMessage(String text) {
        processor.processMessage(text);
    }

    /**
     * Sends a WebSocket ping frame.  Called periodically by the connection
     * manager to keep the connection alive and detect silent failures.
     */
    public void sendPing(Session session) {
        if (session != null && session.isOpen()) {
            try {
                session.getAsyncRemote().sendPing(ByteBuffer.allocate(0));
                log.debug("Ping sent: sessionId={}", session.getId());
            } catch (IOException e) {
                log.warn("Failed to send ping: {}", e.getMessage());
            }
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        log.info("WebSocket session closed: code={} reason='{}'",
                reason.getCloseCode(), reason.getReasonPhrase());
        closeFuture.complete(null);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: {}", error.getMessage(), error);
        transportError = error;
        // Ensure the close future is always completed so the connection manager
        // can reconnect even when @OnClose is not called after certain transport errors.
        closeFuture.complete(null);
    }

    public CompletableFuture<Void> getCloseFuture() {
        return closeFuture;
    }

    /** Returns the transport-level error if {@link #onError} was called, otherwise {@code null}. */
    public Throwable getTransportError() {
        return transportError;
    }
}

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
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests {@link PrimeWebSocketEndpoint} by invoking the Jakarta lifecycle callbacks
 * directly — no live WebSocket connection is required.
 */
@ExtendWith(MockitoExtension.class)
class PrimeWebSocketEndpointTest {

    @Mock
    private PrimeMessageProcessor processor;

    @Mock
    private Session session;

    private PrimeWebSocketEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new PrimeWebSocketEndpoint(processor);
    }

    @Test
    void onMessage_delegatesToProcessor() {
        String payload = """
                {"channel":"heartbeats","sequence_num":1,"events":[]}
                """;
        endpoint.onMessage(payload);
        verify(processor).processMessage(payload);
    }

    @Test
    void onClose_completesCloseFuture() throws Exception {
        CompletableFuture<Void> future = endpoint.getCloseFuture();
        assertThat(future).isNotDone();

        CloseReason reason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");
        endpoint.onClose(session, reason);

        assertThat(future.get(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void onError_completesCloseFuture() throws Exception {
        CompletableFuture<Void> future = endpoint.getCloseFuture();
        endpoint.onError(session, new RuntimeException("transport failure"));

        assertThat(future.get(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void onError_thenOnClose_doesNotThrow() throws Exception {
        endpoint.onError(session, new RuntimeException("error"));
        CloseReason reason = new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "gone");
        endpoint.onClose(session, reason);

        assertThat(endpoint.getCloseFuture()).isDone();
    }

    @Test
    void getCloseFuture_returnsSameInstance() {
        assertThat(endpoint.getCloseFuture()).isSameAs(endpoint.getCloseFuture());
    }

    @Test
    void onOpen_doesNotThrow() {
        endpoint.onOpen(session, null);
    }

    @Test
    void sendPing_doesNotThrowWhenSessionIsNull() {
        endpoint.sendPing(null);
    }

    @Test
    void sendPing_doesNotThrowWhenSessionIsClosed() {
        when(session.isOpen()).thenReturn(false);
        endpoint.sendPing(session);
    }
}

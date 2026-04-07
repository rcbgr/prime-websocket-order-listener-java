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

package com.coinbase.prime.samples.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Tyrus Jakarta WebSocket client container.
 *
 * <p>Settings applied:
 * <ul>
 *   <li>Max text message buffer:   1 MB</li>
 *   <li>Max binary message buffer: 1 MB</li>
 *   <li>Session idle timeout:      5 minutes — server-side idle timeout; also acts as
 *       effective read timeout (analogous to SO_TIMEOUT at the WebSocket layer)</li>
 *   <li>Connect (handshake) timeout: 10 seconds</li>
 *   <li>Keep-alive: WebSocket-level ping frames sent every 30 s by PrimeConnectionManager</li>
 *   <li>Container idle timeout: disabled (container stays alive across reconnections)</li>
 * </ul>
 *
 * <p>Note on SO_TIMEOUT: TCP-level SO_TIMEOUT is not directly exposed by the Jakarta
 * WebSocket API or Tyrus's public client properties. The combination of session idle
 * timeout (5 min) and application-level ping/pong provides equivalent liveness guarantees.
 */
@Configuration
public class WebSocketClientConfig {

    private static final int ONE_MB                   = 1024 * 1024;
    private static final long SESSION_IDLE_TIMEOUT_MS = 5L * 60 * 1000;  // 5 minutes
    private static final int CONNECT_TIMEOUT_MS        = 10_000;          // 10 seconds

    /**
     * Explicit ObjectMapper bean — required because spring-boot-starter without
     * spring-web does not trigger JacksonAutoConfiguration (which depends on
     * Jackson2ObjectMapperBuilder from spring-web).
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public ClientManager webSocketClientManager() {
        ClientManager clientManager = ClientManager.createClient();

        // Buffer limits
        clientManager.setDefaultMaxTextMessageBufferSize(ONE_MB);
        clientManager.setDefaultMaxBinaryMessageBufferSize(ONE_MB);

        // Session idle timeout (5 minutes)
        clientManager.setDefaultMaxSessionIdleTimeout(SESSION_IDLE_TIMEOUT_MS);

        // WebSocket handshake / connect timeout (Tyrus-specific property, value in ms)
        clientManager.getProperties().put(ClientProperties.HANDSHAKE_TIMEOUT, CONNECT_TIMEOUT_MS);

        // Keep the shared container alive indefinitely between reconnections
        // (0 = container does not time out when there are no active sessions)
        clientManager.getProperties().put(ClientProperties.SHARED_CONTAINER_IDLE_TIMEOUT, 0);

        return clientManager;
    }
}

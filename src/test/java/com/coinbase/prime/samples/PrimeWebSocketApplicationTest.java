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

package com.coinbase.prime.samples;

import com.coinbase.prime.samples.connection.PrimeConnectionManager;
import com.coinbase.prime.samples.consumer.OrderConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies that the Spring application context loads successfully.
 *
 * <p>{@link PrimeConnectionManager} and {@link OrderConsumer} are replaced with
 * mocks so the test does not attempt a live WebSocket connection to Coinbase Prime.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "coinbase.prime.access-key=test-access-key",
        "coinbase.prime.api-key-id=test-api-key-id",
        "coinbase.prime.passphrase=test-passphrase",
        "coinbase.prime.secret-key=test-secret-key",
        "coinbase.prime.portfolio-id=test-portfolio-id",
        "coinbase.prime.product-ids=BTC-USD"
})
class PrimeWebSocketApplicationTest {

    /**
     * Mock out the connection manager so no real WebSocket connection is attempted.
     * The {@link org.springframework.boot.ApplicationRunner#run} method will be a no-op.
     */
    @MockBean
    private PrimeConnectionManager connectionManager;

    /**
     * Mock out the order consumer so its background thread is never started.
     */
    @MockBean
    private OrderConsumer orderConsumer;

    @Test
    void contextLoads() {
        // The Spring context should start without errors when credentials are present
        // and the connection manager / consumer are mocked out.
    }
}

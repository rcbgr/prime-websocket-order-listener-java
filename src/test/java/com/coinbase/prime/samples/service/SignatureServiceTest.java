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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureServiceTest {

    private SignatureService signatureService;

    @BeforeEach
    void setUp() {
        signatureService = new SignatureService();
    }

    @Test
    void sign_returnsBase64String() {
        String sig = signatureService.sign(
                "heartbeats", "accessKey", "svcAcctId",
                "1700000000", "portfolioId", List.of("BTC-USD"), "secretKey");

        assertThat(sig).isNotBlank();
        byte[] decoded = Base64.getDecoder().decode(sig);
        // SHA-256 HMAC output is always 32 bytes
        assertThat(decoded).hasSize(32);
    }

    @Test
    void sign_isDeterministic_forSameInput() {
        String sig1 = signatureService.sign(
                "orders", "key", "acct", "1700000001", "port", List.of("ETH-USD"), "secret");
        String sig2 = signatureService.sign(
                "orders", "key", "acct", "1700000001", "port", List.of("ETH-USD"), "secret");

        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void sign_changesWhenChannelChanges() {
        String sigHeartbeats = signatureService.sign(
                "heartbeats", "k", "a", "ts", "p", List.of(), "secret");
        String sigOrders = signatureService.sign(
                "orders",     "k", "a", "ts", "p", List.of(), "secret");

        assertThat(sigHeartbeats).isNotEqualTo(sigOrders);
    }

    @Test
    void sign_changesWhenTimestampChanges() {
        String sig1 = signatureService.sign("c", "k", "a", "1000", "p", List.of(), "secret");
        String sig2 = signatureService.sign("c", "k", "a", "2000", "p", List.of(), "secret");

        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    void sign_concatenatesMultipleProductsWithoutSeparator() {
        String sigTwo = signatureService.sign("c", "k", "a", "ts", "p",
                List.of("BTC-USD", "ETH-USD"), "s");
        String sigOne = signatureService.sign("c", "k", "a", "ts", "p",
                List.of("BTC-USDETH-USD"), "s");

        // Both produce the same prehash because products are joined without separator
        assertThat(sigTwo).isEqualTo(sigOne);
    }

    @Test
    void sign_handlesEmptyProductList() {
        String sig = signatureService.sign("heartbeats", "k", "a", "ts", "", List.of(), "secret");
        assertThat(sig).isNotBlank();
    }

    @Test
    void sign_handlesNullProductList() {
        String sig = signatureService.sign("heartbeats", "k", "a", "ts", "", null, "secret");
        assertThat(sig).isNotBlank();
    }

    @Test
    void sign_changesWhenSecretChanges() {
        String sig1 = signatureService.sign("c", "k", "a", "ts", "p", List.of(), "secret1");
        String sig2 = signatureService.sign("c", "k", "a", "ts", "p", List.of(), "secret2");

        assertThat(sig1).isNotEqualTo(sig2);
    }
}

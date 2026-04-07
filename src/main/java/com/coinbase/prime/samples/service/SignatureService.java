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

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Generates the HMAC-SHA256 signature required by Coinbase Prime WebSocket
 * subscribe and unsubscribe messages.
 *
 * <p>Prehash formula (all values concatenated without separators):
 * <pre>
 *   channelName + accessKey + svcAccountId + timestamp + portfolioId + products
 * </pre>
 * where {@code products} is each product ID concatenated without separators
 * (e.g. "BTC-USDETH-USD" for two products).
 */
@Service
public class SignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * @param channel      WebSocket channel name (e.g. "heartbeats", "orders")
     * @param accessKey    Coinbase Prime API access key
     * @param svcAccountId Service/API account ID
     * @param timestamp    Epoch seconds as a string (e.g. "1645300122")
     * @param portfolioId  Portfolio ID (empty string for channels that don't require it)
     * @param productIds   Product IDs to subscribe to (may be empty)
     * @param secretKey    Coinbase Prime API secret (used as the HMAC key)
     * @return Base64-encoded HMAC-SHA256 signature
     */
    public String sign(String channel,
                       String accessKey,
                       String svcAccountId,
                       String timestamp,
                       String portfolioId,
                       List<String> productIds,
                       String secretKey) {

        String products = (productIds == null || productIds.isEmpty())
                ? ""
                : String.join("", productIds);

        String prehash = channel + accessKey + svcAccountId + timestamp + portfolioId + products;

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(prehash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}

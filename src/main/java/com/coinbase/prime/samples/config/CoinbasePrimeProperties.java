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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "coinbase.prime")
public class CoinbasePrimeProperties {

    private String websocketUri = "wss://ws-feed.prime.coinbase.com";
    private String accessKey;
    private String apiKeyId;
    private String passphrase;
    private String secretKey;
    private String portfolioId;
    private List<String> productIds = List.of("BTC-USD");
    private long reconnectInitialDelayMs = 1000;
    private long reconnectMaxDelayMs = 60000;
    /** -1 means unlimited reconnect attempts. */
    private int maxReconnectAttempts = -1;

    public String getWebsocketUri() { return websocketUri; }
    public void setWebsocketUri(String websocketUri) { this.websocketUri = websocketUri; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(String apiKeyId) { this.apiKeyId = apiKeyId; }

    public String getPassphrase() { return passphrase; }
    public void setPassphrase(String passphrase) { this.passphrase = passphrase; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getPortfolioId() { return portfolioId; }
    public void setPortfolioId(String portfolioId) { this.portfolioId = portfolioId; }

    public List<String> getProductIds() { return productIds; }
    public void setProductIds(List<String> productIds) { this.productIds = productIds; }

    public long getReconnectInitialDelayMs() { return reconnectInitialDelayMs; }
    public void setReconnectInitialDelayMs(long reconnectInitialDelayMs) { this.reconnectInitialDelayMs = reconnectInitialDelayMs; }

    public long getReconnectMaxDelayMs() { return reconnectMaxDelayMs; }
    public void setReconnectMaxDelayMs(long reconnectMaxDelayMs) { this.reconnectMaxDelayMs = reconnectMaxDelayMs; }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }
}

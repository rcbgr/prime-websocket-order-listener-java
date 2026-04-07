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

package com.coinbase.prime.samples.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeeDetails {

    @JsonProperty("client_fee")
    private String clientFee;

    @JsonProperty("financing_fee")
    private String financingFee;

    @JsonProperty("trading_desk_fee")
    private String tradingDeskFee;

    @JsonProperty("venue_fee")
    private String venueFee;

    @JsonProperty("nfa_fee")
    private String nfaFee;

    @JsonProperty("clearing_fee")
    private String clearingFee;

    public String getClientFee() { return clientFee; }
    public void setClientFee(String clientFee) { this.clientFee = clientFee; }

    public String getFinancingFee() { return financingFee; }
    public void setFinancingFee(String financingFee) { this.financingFee = financingFee; }

    public String getTradingDeskFee() { return tradingDeskFee; }
    public void setTradingDeskFee(String tradingDeskFee) { this.tradingDeskFee = tradingDeskFee; }

    public String getVenueFee() { return venueFee; }
    public void setVenueFee(String venueFee) { this.venueFee = venueFee; }

    public String getNfaFee() { return nfaFee; }
    public void setNfaFee(String nfaFee) { this.nfaFee = nfaFee; }

    public String getClearingFee() { return clearingFee; }
    public void setClearingFee(String clearingFee) { this.clearingFee = clearingFee; }

    @Override
    public String toString() {
        return "FeeDetails{clientFee=" + clientFee + ", venueFee=" + venueFee
                + ", clearingFee=" + clearingFee + "}";
    }
}

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

/**
 * Represents a single order received from the Coinbase Prime 'orders' WebSocket channel.
 * All numeric fields are kept as strings to preserve precision and match the wire format.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    /** Cumulative filled quantity. */
    @JsonProperty("cum_qty")
    private String cumQty;

    /** Remaining open quantity. */
    @JsonProperty("leaves_qty")
    private String leavesQty;

    /** Average fill price. */
    @JsonProperty("avg_px")
    private String avgPx;

    /** Net average price (after fees). */
    @JsonProperty("net_avg_px")
    private String netAvgPx;

    private String status;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("user_context")
    private String userContext;

    @JsonProperty("limit_px")
    private String limitPx;

    @JsonProperty("order_type")
    private String orderType;

    /** "buy" or "sell". */
    private String side;

    private String fees;

    @JsonProperty("fee_details")
    private FeeDetails feeDetails;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getClientOrderId() { return clientOrderId; }
    public void setClientOrderId(String clientOrderId) { this.clientOrderId = clientOrderId; }

    public String getCumQty() { return cumQty; }
    public void setCumQty(String cumQty) { this.cumQty = cumQty; }

    public String getLeavesQty() { return leavesQty; }
    public void setLeavesQty(String leavesQty) { this.leavesQty = leavesQty; }

    public String getAvgPx() { return avgPx; }
    public void setAvgPx(String avgPx) { this.avgPx = avgPx; }

    public String getNetAvgPx() { return netAvgPx; }
    public void setNetAvgPx(String netAvgPx) { this.netAvgPx = netAvgPx; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getUserContext() { return userContext; }
    public void setUserContext(String userContext) { this.userContext = userContext; }

    public String getLimitPx() { return limitPx; }
    public void setLimitPx(String limitPx) { this.limitPx = limitPx; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public String getFees() { return fees; }
    public void setFees(String fees) { this.fees = fees; }

    public FeeDetails getFeeDetails() { return feeDetails; }
    public void setFeeDetails(FeeDetails feeDetails) { this.feeDetails = feeDetails; }

    @Override
    public String toString() {
        return "Order{orderId=" + orderId + ", productId=" + productId
                + ", side=" + side + ", status=" + status + "}";
    }
}

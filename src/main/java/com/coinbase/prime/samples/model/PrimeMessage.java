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
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Top-level envelope for all inbound Coinbase Prime WebSocket messages.
 *
 * <p>Normal channel messages carry {@code channel}, {@code timestamp},
 * {@code sequence_num}, and {@code events}.  Error messages from the server
 * carry {@code type = "error"} and {@code message}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrimeMessage {

    /** Present on error frames: value is {@code "error"}. */
    private String type;

    /** Channel name: "heartbeats", "orders", "subscriptions". */
    private String channel;

    private String timestamp;

    /**
     * Monotonically increasing per-channel sequence counter.
     * Modelled as {@code long} (Java equivalent of Go's {@code uint64}).
     * A gap larger than 1 indicates dropped messages; a value smaller than
     * the previous indicates an out-of-order delivery.
     */
    @JsonProperty("sequence_num")
    private long sequenceNum;

    /**
     * Channel-specific event payloads.  Kept as {@link JsonNode} to handle
     * the polymorphic event shapes across channels without a large hierarchy.
     */
    private List<JsonNode> events;

    /** Present on error frames: human-readable error description. */
    private String message;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public long getSequenceNum() { return sequenceNum; }
    public void setSequenceNum(long sequenceNum) { this.sequenceNum = sequenceNum; }

    public List<JsonNode> getEvents() { return events; }
    public void setEvents(List<JsonNode> events) { this.events = events; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

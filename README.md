# Prime WebSocket Order Listener Java

Prime WebSocket Order Listener Java is a real-time order monitoring application that connects to the [Coinbase Prime WebSocket feed](https://docs.cdp.coinbase.com/prime/websocket-feed/overview) and streams live order updates to a thread-safe buffer for downstream processing.

This is a sample application; test thoroughly and verify it meets your requirements before using.

## Overview

This application establishes authenticated WebSocket connections to Coinbase Prime, subscribes to the `heartbeats` and `orders` channels, and routes received orders to an in-memory queue consumed by a background logging thread.

Products are partitioned into groups of up to 10, with a dedicated WebSocket connection per group. All connections write to a single shared order queue.

**Core Features:**
- Authenticated WebSocket connections with HMAC-SHA256 signed subscribe messages
- Automatic product partitioning — up to 10 products per connection, connections scaled to cover the full product list
- Global sequence number validation per connection with automatic reconnect on gaps
- Exponential back-off reconnection with configurable limits
- Thread-safe bounded order queue (5,000 capacity) decoupling ingestion from processing
- Periodic WebSocket ping frames to detect silent connection failures
- Connection health monitoring — per-worker status, cumulative failure counts, and per-category breakdowns logged every 60 seconds
- Clean shutdown on Ctrl-C — sends unsubscribe messages before closing

## Setup

### Environment Variables

Export the following before running the application:

```bash
export ACCESS_KEY='your-access-key'
export SIGNING_KEY='your-signing-key'
export PASSPHRASE='your-passphrase'
export PORTFOLIO_ID='your-portfolio-id'
```

| Variable | Description |
|---|---|
| `ACCESS_KEY` | Coinbase Prime API access key |
| `SIGNING_KEY` | Coinbase Prime API signing key (used for HMAC-SHA256 signatures) |
| `PASSPHRASE` | Coinbase Prime API passphrase |
| `PORTFOLIO_ID` | The Prime portfolio to subscribe to for order updates |

### Product Configuration

The list of product IDs to subscribe to is configured in `src/main/resources/application.yml`:

```yaml
coinbase:
  prime:
    product-ids:
      - BTC-USD
      - ETH-USD
```

Add or remove product IDs as needed. Products are automatically split into groups of at most 10, with one WebSocket connection established per group.

### Optional Configuration

```yaml
coinbase:
  prime:
    websocket-uri: wss://ws-feed.prime.coinbase.com
    reconnect-initial-delay-ms: 1000   # Initial back-off delay
    reconnect-max-delay-ms: 60000      # Maximum back-off delay (doubles each attempt)
    max-reconnect-attempts: -1         # -1 = unlimited
```

## Building and Running

**Requirements:** Java 21, Maven 3.8+

```bash
# Build
mvn package -DskipTests

# Run
mvn spring-boot:run

# Run with explicit env vars
ACCESS_KEY=... SIGNING_KEY=... PASSPHRASE=... PORTFOLIO_ID=... mvn spring-boot:run
```

## Running Tests

All tests run without a live WebSocket connection.

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=PrimeMessageProcessorTest

# Run a specific test method
mvn test -Dtest=PrimeMessageProcessorTest#checkSequence_triggersReconnectOnGap
```

## How It Works

### Connection Lifecycle

1. `PrimeConnectionManager` partitions the configured product list into batches of at most 10 and starts one `PrimeConnectionWorker` per batch.
2. Each worker runs its own connection loop on a dedicated virtual thread.
3. A new `PrimeWebSocketEndpoint` instance is created for each connection attempt and registered with the Tyrus WebSocket container.
4. Once the handshake completes, signed `subscribe` frames are sent for `heartbeats` then `orders`.
5. A ping is sent every 30 seconds to keep the connection alive.
6. When a session closes — due to a network error, server disconnect, or sequence gap — the worker reconnects with exponential back-off.
7. On Ctrl-C, Spring's shutdown hook unsubscribes and closes all sessions cleanly.

### Message Processing

All incoming frames are handled by `PrimeMessageProcessor` (one instance per connection):

- **`heartbeats`** — debug-logged; global sequence number tracked.
- **`orders`** — each order in the event is deserialised and handed to `OrderQueueService`.
- **`subscriptions`** — subscription confirmation is info-logged; global sequence tracking continues.
- **`error` frames** — logged and triggers an immediate reconnect.

### Sequence Number Validation

Coinbase Prime uses a **single monotonically increasing counter per connection** shared across all channels. Each connection tracks this with one counter that resets on reconnect.

| Condition | Action |
|---|---|
| First message on connection | Accept as baseline |
| `received == previous + 1` | Normal — advance counter |
| `received < previous + 1` | Out-of-order — warn, keep connection |
| `received > previous + 1` | Gap — log error, reconnect |

### Order Queue and Consumer

`OrderQueueService` wraps a `LinkedBlockingQueue<Order>` with a fixed capacity of 5,000. Orders are enqueued non-blocking; if the queue is full the order is dropped with a warning.

`OrderConsumer` polls the queue on a dedicated virtual thread and classifies each order:

| Label | Condition |
|---|---|
| `FILL` | `cumQty > 0` — order has been partially or fully executed |
| `ORDER status changed` | `cumQty == 0` and status differs from the last recorded value |
| `ORDER` | First time this order ID is seen |

```
FILL: orderId=... clientOrderId=... productId=BTC-USD side=BUY orderType=market
      status=FILLED cumQty=0.00002948 leavesQty=0 avgPx=68118.25 netAvgPx=67842.60 fees=0
```

Replace the logging call in `OrderConsumer.logOrder()` with your downstream processing logic.

### Connection Health Monitoring

`ConnectionRegistry` tracks the status and failure history of every worker. Every 60 seconds it logs a report:

```
Connection health report (5 connection(s)):
  Worker 0  [BTC-USD,ETH-USD,...]   status=CONNECTED    failures=1  [SEQUENCE_GAP=1]  lastFailure=...  reason='sequence gap (dropped=2)'
  Worker 1  [LINK-USD,UNI-USD,...]  status=CONNECTED    failures=0
  Worker 2  [APT-USD,ARB-USD,...]   status=CLOSED       failures=2  [SERVER_ERROR_SLOW_CONSUME=1, CONNECTION_RESET=1]  lastFailure=...
```

A failure is recorded whenever a connection is closed, a sequence gap is detected, or a WebSocket error occurs. Failures are classified into: `SERVER_ERROR_SLOW_CONSUME`, `SERVER_ERROR_SLOW_READ`, `SEQUENCE_GAP`, `CONNECTION_RESET`, `TLS_ERROR`, `DISCONNECT_REQUESTED`, `MESSAGE_TOO_BIG`, `UNKNOWN`.

### Signature Generation

Subscribe and unsubscribe messages are signed using HMAC-SHA256. The prehash string is:

```
channel + accessKey + apiKeyId + timestamp + portfolioId + products
```

where `products` is each product ID concatenated directly with no separator between them (e.g. `BTC-USD` + `ETH-USD` → `BTC-USDETH-USD`), and `timestamp` is epoch seconds as a string.

## WebSocket Configuration

| Setting | Value |
|---|---|
| Max text message buffer | 1 MB |
| Max binary message buffer | 1 MB |
| Session idle timeout | 5 minutes |
| Connect (handshake) timeout | 10 seconds |
| Keep-alive ping interval | 30 seconds |
| Implementation | [Tyrus](https://projects.eclipse.org/projects/ee4j.tyrus) (Jakarta WebSocket 2.2) |

## License

Copyright 2026-present Coinbase Global, Inc.

Licensed under the [Apache License, Version 2.0](LICENSE).

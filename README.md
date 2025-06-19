# Distributed-Cache 📔️

A high-performance, **generic**, and **asynchronous** in-process cache library in Java supporting:

- **TTL expiration** (time-to-live for entries)
- **Eviction policies**: LRU or LFU
- **Fetch strategies**: Write-through & Write-back
- **Eager loading** of keys
- **Parallelism with per-key thread‑based concurrency**
- **Async fetch and persist** via `CompletionStage`
- Customizable via `CacheBuilder`

---

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
    - [Quick Example](#quick-example)
- [Usage & Configuration](#usage--configuration)
    - [Eviction & Expiry](#eviction--expiry)
    - [Fetch Strategies](#fetch-strategies)
    - [Concurrency / Pool Size](#concurrency--pool-size)
- [Internals Overview](#internals-overview)
- [Upcoming Improvements](#upcoming-improvements)
- [Contributing](#contributing)
- [License](#license)

---

## Features

- **Generic**: Supports any key/value types
- **Async**: Uses `CompletionStage<V>` for non-blocking I/O
- **Eviction**: Choose between LRU (Least Recently Used) or LFU (Least Frequently Used)
- **Fetch Strategy**: `WRITE_THROUGH` (sync write) or `WRITE_BACK` (async)
- **Expiry**: TTL expiration of entries using a `Timer` abstraction
- **Eager Loading**: Warm up cache at startup
- **Concurrency**: Thread-safe with per-key thread mapping
- **Event Logging**: Records cache operations in an audit queue

---

## Getting Started

### Quick Example

```java
Cache<String, String> cache = new CacheBuilder<String, String>()
    .maxSize(500)
    .expiryTime(Duration.ofMinutes(30))
    .evictionAlgorithm(EvictionAlgorithm.LRU)
    .fetchAlgorithm(FetchAlgorithm.WRITE_THROUGH)
    .dataSource(myDataSource)  // implements DataSource<KEY, VALUE>
    .onStartLoad(Set.of("key1", "key2"))
    .poolSize(Runtime.getRuntime().availableProcessors())
    .build();

// Fetching
cache.get("foo")
     .thenAccept(value -> System.out.println("Value: " + value));

// Updating
cache.set("foo", "newValue");
```

---

## Usage & Configuration

### Eviction & Expiry

- Set `maxSize(int n)` to cap cache entries.
- Expiry is applied to each entry with `expiryTime(Duration)`; after expiration, it’s evicted on access or insertion.
- Eviction policy (`LRU` or `LFU`) is configured using `evictionAlgorithm(...)`.

### Fetch Strategies

- **WRITE_THROUGH**: Writes are immediately persisted synchronously.
- **WRITE_BACK**: Writes can be deferred, performed asynchronously in the background.

### Concurrency & Pool Size

- Configurable via `poolSize(int threads)`.
- Default is `Runtime.getRuntime().availableProcessors()`.
- Each key is consistently assigned to a single-thread executor based on `hashCode()`, ensuring per-key sequential integrity.
- ⚠️ Highly active keys may accumulate queued operations—consider scaling the pool or coalescing operations.

---

## Internals Overview

1. **Data Structures**:
    - `ConcurrentHashMap` for entries.
    - `ConcurrentSkipListMap` for eviction (priority queue) and expiry tracking.
2. **Per-Key Threading**:
    - `getThreadFor(key, task)` schedules tasks in a dedicated executor to avoid locks.
3. **Eager Loading**:
    - Specified keys are pre-loaded on startup using async fetch and inserted into the cache.
4. **Event Queue**:
    - Audit `List` capturing cache operations (load, update, eviction, etc.) for logging or metrics.

---

## Upcoming Improvements


- **Distributed coordination** (e.g., Redis or gRPC for multi-instance coherency)

- **Cache backplane** for cross-node cache consistency

- **Background refresh** or soft TTL extensions

- **Usage metrics / observability hooks**

- **More fetch policies** (refresh ahead, cache stampede prevention)

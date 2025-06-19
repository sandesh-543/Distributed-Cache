import enums.EvictionAlgorithm;
import enums.FetchAlgorithm;
import enums.EvictionType;
import events.*;
import models.AccessDetails;
import models.Record;
import models.Timer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;

public class Cache<KEY, VALUE> {
    // Using Builder pattern to create class
    private final int maxSize;
    private final FetchAlgorithm fetchAlgorithm;
    private final Duration expiryTime;
    private final Map<KEY, CompletionStage<Record<KEY, VALUE>>> cache;
    private final ConcurrentSkipListMap<AccessDetails, List<KEY>> priorityQueue;
    private final ConcurrentSkipListMap<Long, List<KEY>> expiryQueue;
    private final DataSource<KEY, VALUE> dataSource;
    private final List<Event<KEY, VALUE>> eventQueue;
    private final ExecutorService[] executorPool;
    private final Timer timer;

    protected Cache(final int maxSize,
                    final Duration expiryTime,
                    final FetchAlgorithm fetchAlgorithm,
                    final EvictionAlgorithm evictionAlgorithm,
                    final DataSource<KEY, VALUE> dataSource,
                    final Set<KEY> keysToEagerlyLoad,
                    final Timer timer,
                    final int poolsize) {
        this.maxSize = maxSize;
        this.expiryTime = expiryTime;
        this.fetchAlgorithm = fetchAlgorithm;
        this.dataSource = dataSource;
        this.timer = timer;
        this.cache = new ConcurrentHashMap<>();
        this.eventQueue = new CopyOnWriteArrayList<>();
        this.executorPool = new ExecutorService[poolsize];

        for (int i = 0; i < poolsize; i++) {
            executorPool[i] = Executors.newSingleThreadExecutor();
        }

        // Comparison based on the eviction policy chosen
        priorityQueue = new ConcurrentSkipListMap<>((first, second) -> {
            final var accessTimeDiff = (int) (first.getLastAccessTime() - second.getLastAccessTime());
            if (evictionAlgorithm.equals(EvictionAlgorithm.LRU)) {
                return accessTimeDiff;
            }
            final var accessCountDiff = first.getAccessCount() - second.getAccessCount();
            return accessCountDiff != 0 ? accessCountDiff : accessTimeDiff;
        });

        expiryQueue = new ConcurrentSkipListMap<>();
        final var eagerLoading = keysToEagerlyLoad.stream()
                .map(key -> getThreadFor(key, addToCache(key, loadFromDB(dataSource, key))))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(eagerLoading).join();
    }

    private <U> CompletionStage<U> getThreadFor(KEY key, CompletionStage<U> task){
        return CompletableFuture.supplyAsync(() -> task, executorPool[Math.abs(key.hashCode() % executorPool.length)]).thenCompose(Function.identity());
    }

    public CompletionStage<VALUE> get(KEY key) {
        return getThreadFor(key, getFromCache(key));
    }

    public CompletionStage<Void> set(KEY key, VALUE value) {
        return getThreadFor(key, setInCache(key, value));
    }

    private CompletionStage<VALUE> getFromCache(KEY key) {
        final CompletionStage<Record<KEY, VALUE>> result;
        if (!cache.containsKey(key)) {
            result = addToCache(key, loadFromDB(dataSource, key));
        }
        else {
            result = cache.get(key).thenCompose(record -> {
                if (hasExpired(record)) {
                    priorityQueue.get(record.getAccessDetails()).remove(key);
                    expiryQueue.get(record.getInsertTime()).remove(key);
                    eventQueue.add(new Eviction<>(record, EvictionType.EXPIRY, timer.getCurrentTime()));
                    return addToCache(key, loadFromDB(dataSource, key));
                }
                else {
                    return CompletableFuture.completedFuture(record);
                }
            });
        }
        return result.thenApply(record -> {
            priorityQueue.get(record.getAccessDetails()).remove(key);
            final AccessDetails updatedAccessDetails = record.getAccessDetails().update(timer.getCurrentTime());
            priorityQueue.putIfAbsent(updatedAccessDetails, new CopyOnWriteArrayList<>());
            priorityQueue.get(updatedAccessDetails).add(key);
            record.setAccessDetails(updatedAccessDetails);
            return record.getValue();
        });
    }

    public CompletionStage<Void> setInCache(KEY key, VALUE value) {
        CompletionStage<Void> result = CompletableFuture.completedFuture(null);
        if (cache.containsKey(key)) {
            result = cache.remove(key)
                    .thenAccept(oldRecord -> {
                        priorityQueue.get(oldRecord.getAccessDetails()).remove(key);
                        expiryQueue.get(oldRecord.getInsertTime()).remove(key);
                        if (hasExpired(oldRecord)) {
                            eventQueue.add(new Eviction<>(oldRecord, EvictionType.EXPIRY, timer.getCurrentTime()));
                        }
                        else {
                            eventQueue.add(new Update<>(new Record<>(key, value, timer.getCurrentTime()), oldRecord, timer.getCurrentTime()));
                        }
                    });
        }
        return result.thenCompose(_ -> addToCache(key, CompletableFuture.completedFuture(value))).thenCompose(record -> {
            final CompletionStage<Void> writeOperation = persistRecord(record);
            return fetchAlgorithm == FetchAlgorithm.WRITE_THROUGH ? writeOperation : CompletableFuture.completedFuture(null);
        });
    }

    @org.jetbrains.annotations.NotNull
    private CompletionStage<Record<KEY, VALUE>> addToCache(final KEY key, final CompletionStage<VALUE> valueFuture) {
        manageEntries();
        final var recordFuture = valueFuture.thenApply(value -> {
            final Record<KEY, VALUE> record = new Record<>(key, value, timer.getCurrentTime());
            expiryQueue.putIfAbsent(record.getInsertTime(), new CopyOnWriteArrayList<>());
            expiryQueue.get(record.getInsertTime()).add(key);
            priorityQueue.putIfAbsent(record.getAccessDetails(), new CopyOnWriteArrayList<>());
            priorityQueue.get(record.getAccessDetails()).add(key);
            return record;
        });
        cache.put(key, recordFuture);
        return recordFuture;
    }

    private synchronized void manageEntries() {
        // Eviction based on expiry of records
        if (cache.size() >= maxSize) {
            while(!expiryQueue.isEmpty() && hasExpired(expiryQueue.firstKey())) {
                final List<KEY> keys = expiryQueue.pollFirstEntry().getValue();
                for (final KEY key : keys) {
                    final Record<KEY, VALUE> lowestPriorityRecord = cache.remove(key).toCompletableFuture().join();
                    expiryQueue.get(lowestPriorityRecord.getInsertTime()).remove(lowestPriorityRecord.getKey());
                }
            }
        }
        // Eviction based on replacement policy
        if (cache.size() >= maxSize) {
            List<KEY> keys = priorityQueue.pollFirstEntry().getValue();
            while (keys.isEmpty()) {
                keys = priorityQueue.pollFirstEntry().getValue();
            }
            for (final KEY key : keys) {
                final Record<KEY, VALUE> lowestPriorityRecord = cache.remove(key).toCompletableFuture().join();
                expiryQueue.get(lowestPriorityRecord.getInsertTime()).remove(lowestPriorityRecord.getKey());
                eventQueue.add(new Eviction<>(lowestPriorityRecord, EvictionType.REPLACEMENT, timer.getCurrentTime()));
            }
        }
    }

    private CompletionStage<Void> persistRecord(final Record<KEY, VALUE> record) {
        return dataSource.persist(record.getKey(), record.getValue(), record.getInsertTime())
                .thenAccept(_ -> eventQueue.add(new Write<>(record, timer.getCurrentTime())));
    }

    private boolean hasExpired(final Record<KEY, VALUE> record) {
        return hasExpired(record.getInsertTime());
    }

    private boolean hasExpired(final long time) {
        return Duration.ofNanos(timer.getCurrentTime() - time).compareTo(expiryTime) > 0;
    }

    public List<Event<KEY, VALUE>> getEventQueue() {
        return eventQueue;
    }

    private CompletionStage<VALUE> loadFromDB(final DataSource<KEY, VALUE> dataSource, KEY key) {
        return dataSource.load(key).whenComplete((value, throwable) -> {
            if (throwable == null) {
                eventQueue.add((Event<KEY, VALUE>) new Load<>(new Record<>(key, value, timer.getCurrentTime()), timer.getCurrentTime()));
            }
        });
    }
}
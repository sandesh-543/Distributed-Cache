import enums.EvictionAlgorithm;
import enums.FetchAlgorithm;
import models.Timer;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public class CacheBuilder<KEY, VALUE> {
    private int maxSize;
    private Duration expiryTime;
    private final Set<KEY> onStartLoad;
    private EvictionAlgorithm evictionAlgorithm;
    private FetchAlgorithm fetchAlgorithm;
    private DataSource<KEY, VALUE> dataSource;
    private Timer timer;
    private int poolSize;

    public CacheBuilder(int maxSize, Duration duration, Set<KEY> onStartLoad) {
        this.maxSize = maxSize;
        this.expiryTime = duration;
        this.onStartLoad = onStartLoad;
    }

    public CacheBuilder(){
        maxSize = 1000;
        expiryTime =Duration.ofDays(365);
        fetchAlgorithm = FetchAlgorithm.WRITE_THROUGH;
        evictionAlgorithm = EvictionAlgorithm.LRU;
        onStartLoad = new HashSet<>();
        poolSize = Runtime.getRuntime().availableProcessors();
        // Note: above statement is like saying:
        // "Let me create as many parallel queues as I have logical CPU cores,
        // so I can maximize throughput without overwhelming the system."
        timer = new Timer();
    }

    public CacheBuilder<KEY, VALUE> evictionAlgorithm(final EvictionAlgorithm evictionAlgorithm) {
        this.evictionAlgorithm = evictionAlgorithm;
        return this;
    }

    public CacheBuilder<KEY, VALUE> fetchAlgorithm(final FetchAlgorithm fetchAlgorithm) {
        this.fetchAlgorithm = fetchAlgorithm;
        return this;
    }

    public CacheBuilder<KEY, VALUE> dataSource(final DataSource<KEY, VALUE> dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    public CacheBuilder<KEY, VALUE> maxSize(final int maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    public CacheBuilder<KEY, VALUE> expiryTime(final Duration expiryTime) {
        this.expiryTime = expiryTime;
        return this;
    }

    public CacheBuilder<KEY, VALUE> poolSize(final int poolSize) {
        this.poolSize = poolSize;
        return this;
    }

    public CacheBuilder<KEY, VALUE> onStartLoad(final Set<KEY> key) {
        this.onStartLoad.addAll(key);
        return this;
    }

    public CacheBuilder<KEY, VALUE> timer(final Timer timer) {
        this.timer = timer;
        return this;
    }

    public Cache<KEY, VALUE> build() {
        if (dataSource == null) {
            throw new IllegalArgumentException("No data source specified");
        }
        return new Cache<>(maxSize, expiryTime, fetchAlgorithm, evictionAlgorithm, dataSource, onStartLoad, timer, poolSize);;
    }
}
package models;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

public class AccessDetails {
    private final LongAdder accessCount;
    // When cache entries are accessed from multiple threads,
    // you don’t want to slow down the system with lock contention just to increment a counter.
    //LongAdder ensures this increment remains efficient and thread-safe.
    private long lastAccessTime;

    public AccessDetails(long lastAccessTime) {
        accessCount = new LongAdder();
        this.lastAccessTime = lastAccessTime;
    }

    public int getAccessCount() {
        return (int) accessCount.longValue();
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public AccessDetails update(long lastAccessTime) {
        final AccessDetails accessDetails = new AccessDetails(lastAccessTime);
        accessDetails.accessCount.add(this.accessCount.longValue() + 1);
        return accessDetails;
    }

    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessDetails that = (AccessDetails) o;
        return this.getAccessCount() == that.getAccessCount();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAccessCount(), lastAccessTime);
    }

    @Override
    public String toString() {
        return "AccessDetails{" +
                "acessCount = " + accessCount +
                ", lastAccessTime = " + lastAccessTime +
                "}";
    }
}

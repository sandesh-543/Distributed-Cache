package models;

// Generic class to store any key value pair
public class Record<KEY, VALUE> {
    private final KEY key;
    private final VALUE value;
    private final long insertionTime;
    private AccessDetails accessDetails;

    //key and value store the actual key-value pair for cache entry.
    //insertionTime records when this entry was created (usually timestamp).
    //accessDetails tracks access count & last access time (like how many times and when it was last accessed).

    public Record(KEY key, VALUE value, long insertTime) {
        this.key = key;
        this.value = value;
        this.insertionTime = insertTime;
        accessDetails = new AccessDetails(insertTime);
    }

    public KEY getKey() {
        return key;
    }

    public VALUE getValue() {
        return value;
    }

    public long getInsertTime() {
        return insertionTime;
    }

    public AccessDetails getAccessDetails() {
        return accessDetails;
    }

    @Override
    public String toString() {
        return "Record{" +
                "key=" + key +
                ", value=" + value +
                ", insertionTime=" + insertionTime +
                ", accessDetails=" + accessDetails +
                '}';
    }

    public void setAccessDetails(AccessDetails updatedAccessDetails) {
        this.accessDetails = updatedAccessDetails;
    }
}
package events;

import models.Record;

import enums.EvictionType;

public class Eviction<K, V> extends Event<K, V> {
    private final EvictionType evictionType;

    public Eviction(Record<K, V> element, EvictionType evictionType, long timeStamp) {
        super(element, timeStamp);
        this.evictionType = evictionType;
    }

    public EvictionType getType() {
        return evictionType;
    }

    @Override
    public String toString() {
        return "Eviction{" +
                "type=" + evictionType +
                ", "+super.toString() +
                "}\n";
    }
}
package events;

import models.Record;

import Enums.Type;

public class Eviction<K, V> extends Event<K, V> {
    private final Type type;

    public Eviction(Record<K, V> element, Type type, long timeStamp) {
        super(element, timeStamp);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Eviction{" +
                "type=" + type +
                ", "+super.toString() +
                "}\n";
    }
}
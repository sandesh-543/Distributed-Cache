package events;

import models.Record;

import java.util.UUID;

public abstract class Event<KEY, VALUE> {
    private final String id;
    private final Record<KEY, VALUE> element;
    private final long timeStamp;

    public Event(Record<KEY, VALUE> element, long timeStamp) {
        this.id = UUID.randomUUID().toString(); // unique id in space and time
        this.element = element;
        this.timeStamp = timeStamp;
    }

    public String getId() {
        return id;
    }

    public Record<KEY, VALUE> getElement() {
        return element;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "{" +
                "element=" + element +
                ", timestamp=" + timeStamp +
                "}\n";
    }
}
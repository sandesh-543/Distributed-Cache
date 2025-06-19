package events;

import models.Record;

public class Load<KEY, VALUE> extends Event<KEY, VALUE> {

    public Load(Record<KEY, VALUE> element, long timestamp) {
        super(element, timestamp);
    }
}
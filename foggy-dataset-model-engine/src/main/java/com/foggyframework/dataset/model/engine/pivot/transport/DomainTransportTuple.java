package com.foggyframework.dataset.model.engine.pivot.transport;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class DomainTransportTuple {
    private final List<Object> values;
}

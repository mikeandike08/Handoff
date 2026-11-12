package com.mikeandike.handoff.model;
import java.util.List;

public record PageResult<T>(List<T> data) {
    public PageResult {
        data = List.copyOf(data);
    }
}

package com.mikeandike.handoff.dto;

import java.util.List;

public record ListResponse<T>(List<T> data) {
    public ListResponse {
        data = List.copyOf(data);
    }
}

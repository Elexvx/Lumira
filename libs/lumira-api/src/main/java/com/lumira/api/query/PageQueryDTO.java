package com.lumira.api.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQueryDTO(
        @Min(1) int page,
        @Min(1) @Max(200) int size
) {
}

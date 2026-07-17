package com.nbamanager.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record SyncJobPageResponse(
        List<SyncJobResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static SyncJobPageResponse from(Page<SyncJobResponse> page) {
        return new SyncJobPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

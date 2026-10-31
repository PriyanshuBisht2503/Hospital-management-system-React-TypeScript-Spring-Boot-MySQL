package com.hms.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    @DisplayName("sanitizePageSize enforces strict cap of 100 for requested sizes > 100")
    void sanitizePageSizeCapsAt100() {
        assertThat(PageResponse.sanitizePageSize(101)).isEqualTo(100);
        assertThat(PageResponse.sanitizePageSize(500)).isEqualTo(100);
        assertThat(PageResponse.sanitizePageSize(1000)).isEqualTo(100);
        assertThat(PageResponse.sanitizePageSize(100)).isEqualTo(100);
    }

    @Test
    @DisplayName("sanitizePageSize defaults to 10 for invalid zero or negative page sizes")
    void sanitizePageSizeDefaultsForZeroOrNegative() {
        assertThat(PageResponse.sanitizePageSize(0)).isEqualTo(PageResponse.DEFAULT_PAGE_SIZE);
        assertThat(PageResponse.sanitizePageSize(-5)).isEqualTo(PageResponse.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("sanitizePageSize preserves valid requested sizes under 100")
    void sanitizePageSizePreservesValidSizes() {
        assertThat(PageResponse.sanitizePageSize(25)).isEqualTo(25);
        assertThat(PageResponse.sanitizePageSize(50)).isEqualTo(50);
    }

    @Test
    @DisplayName("PageResponse.from maps Spring Page metadata and content accurately")
    void fromMapsPageAccurately() {
        List<String> items = List.of("item1", "item2", "item3");
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageImpl<String> springPage = new PageImpl<>(items, pageRequest, 25);

        PageResponse<String> response = PageResponse.from(springPage);

        assertThat(response.content()).containsExactly("item1", "item2", "item3");
        assertThat(response.pageNumber()).isEqualTo(0);
        assertThat(response.pageSize()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
    }
}

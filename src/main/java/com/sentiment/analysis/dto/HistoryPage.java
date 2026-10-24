package com.sentiment.analysis.dto;

import java.util.List;

public record HistoryPage(List<HistoryRow> items, int page, int size, int totalPages, long totalItems) {
}

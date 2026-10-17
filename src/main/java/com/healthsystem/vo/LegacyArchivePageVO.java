package com.healthsystem.vo;

import java.util.List;

public record LegacyArchivePageVO(List<LegacyManagedArchiveVO> items, long total, int page, int pageSize) {}

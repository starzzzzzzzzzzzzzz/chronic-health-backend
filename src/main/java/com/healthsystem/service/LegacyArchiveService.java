package com.healthsystem.service;

import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.vo.LegacyArchivePageVO;

public interface LegacyArchiveService {
    LegacyArchivePageVO list(BusinessPrincipal principal, int page, int pageSize, String keyword);
}

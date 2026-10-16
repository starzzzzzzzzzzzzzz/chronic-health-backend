package com.healthsystem.common.security;

import java.security.Principal;

public record BusinessPrincipal(String orgId, String subjectId, String dataMode) implements Principal {
    @Override public String getName() { return subjectId; }
}

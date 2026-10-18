package com.healthsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.dto.*;
import com.healthsystem.entity.LegacyRecommendationRequest;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.mapper.LegacyRecommendationRequestMapper;
import com.healthsystem.service.CarePlanService;
import com.healthsystem.service.PatientService;
import com.healthsystem.vo.PlanGenerationJobVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

/** Commits the draft, request key and queued job together; never waits for a model in a transaction. */
@Service
public class LegacyRecommendationStore {
    private final LegacyArchiveResolver archives;
    private final LegacyRecommendationRequestMapper requests;
    private final CarePlanService drafts;
    private final PlanGenerationService generations;
    private final Clock clock;
    private final PatientService patients;
    private final ManagementFocusPolicy management;
    public LegacyRecommendationStore(LegacyArchiveResolver archives, LegacyRecommendationRequestMapper requests,
            CarePlanService drafts, PlanGenerationService generations, Clock clock, PatientService patients, ManagementFocusPolicy management) {
        this.archives = archives; this.requests = requests; this.drafts = drafts; this.generations = generations; this.clock = clock;this.patients=patients;this.management=management;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PlanGenerationJobVO submit(BusinessPrincipal principal, String requestKey, LegacyGenerateRecommendationsRequest request) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9._:-]{1,64}"))
            throw BusinessException.badRequest("Invalid X-Request-Id");
        var patient = archives.require(principal, request.archiveId(), "PLAN_EDIT", true);
        archives.require(principal, request.archiveId(), "PATIENT_READ", false);
        if (request.shopId() != null && !request.shopId().equals(principal.orgId())
                || request.teamId() != null && !request.teamId().equals(patient.getTeamId()))
            throw new BusinessException(403, "Requested shop/team does not match the authorized local scope");
        String hash = CanonicalJson.hash(JsonValues.tree(request));
        var previous = requests.selectOne(new QueryWrapper<LegacyRecommendationRequest>()
                .eq("org_id", principal.orgId()).eq("created_by", principal.subjectId()).eq("request_key", requestKey));
        if (previous != null) {
            if (!hash.equals(previous.getRequestHash())) throw BusinessException.conflict("X-Request-Id has another request");
            return generations.get(principal, previous.getGenerationJobId());
        }
        management.bind(principal,JsonValues.tree(patients.healthSummary(principal,patient.getId())),request.confirmedManagement());
        LocalDate start = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        var draft = drafts.create(principal, new CreateCarePlanRequest(patient.getId(), String.valueOf(patient.getProfileRevision()),
                "旧版前端 · 方案候选", start, start.plusDays(6), "Asia/Shanghai", null));
        // These are user preferences, never new confirmed diagnoses or patient facts.
        Map<String, Object> preferences = new LinkedHashMap<>();
        if (request.disease() != null) preferences.put("requestedDisease", request.disease());
        if (request.focuses() != null) preferences.put("requestedFocuses", request.focuses());
        if (request.prompt() != null) preferences.put("prompt", request.prompt());
        if (request.supplementalRequirements() != null) preferences.put("supplementalRequirements", request.supplementalRequirements());
        if (!preferences.isEmpty()) {
            String requirements = "以下仅为前端提交的生成偏好，不能作为已确认病种或患者事实：\n" + JsonValues.write(preferences);
            if (requirements.length() > 20000) throw BusinessException.badRequest("Combined generation requirements are too long");
            draft = drafts.update(principal, draft.versionId(), new UpdateCarePlanRequest(JsonValues.tree(
                    Map.of("expectedRevision", draft.rowVersion(), "supplementalRequirements", requirements))));
        }
        var job = generations.submit(principal, draft.versionId(), "legacy:" + UUID.randomUUID(),
                new GeneratePlanRequest(draft.rowVersion(), String.valueOf(patient.getProfileRevision()), null, request.confirmedManagement())).job();
        var row = new LegacyRecommendationRequest();
        row.setId(UUID.randomUUID().toString()); row.setOrgId(principal.orgId()); row.setCreatedBy(principal.subjectId());
        row.setCreatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        row.setRequestKey(requestKey); row.setRequestHash(hash); row.setGenerationJobId(job.id()); requests.insert(row);
        return job;
    }
}

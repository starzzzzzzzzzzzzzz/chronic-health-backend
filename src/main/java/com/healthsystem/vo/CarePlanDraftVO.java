package com.healthsystem.vo;
import com.healthsystem.dto.PlanComponents.*;
import com.healthsystem.dto.PlanComponents.Module;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.List;
public record CarePlanDraftVO(String planId,String versionId,int versionNo,String patientId,String status,String rowVersion,String contentRevision,String title,LocalDate startDate,LocalDate endDate,String zoneId,String primaryGoal,JsonNode goalBasis,String supplementalRequirements,String changeReason,String inputSnapshotId,JsonNode inputSnapshot,String contentHash,List<Module> modules,List<Focus> managementFocuses,List<Dimension> dimensions,List<Item> items,OffsetDateTime updatedAt) {}

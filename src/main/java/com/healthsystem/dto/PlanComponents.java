package com.healthsystem.dto;
import tools.jackson.databind.JsonNode;
import java.util.*;
/** Shared request/view shapes; never persistence entities. */
public final class PlanComponents {
 private PlanComponents() {}
 public record Module(String id,String moduleCode,String selectionReason,List<String> factIds) {}
 public record Dimension(String id,String dimensionCode,String decision,String decisionReason,Integer sortOrder) {}
 public record Focus(String id,String diagnosisFactId,String primarySubtypeCode,List<String> secondarySubtypeCodes,String priorityCode,Map<String,Integer> dimensionWeights,Integer contributionBps,String adjustmentReason) {}
 public record Basis(String moduleCode,String factId,String note) {}
 public record Item(String id,String dimensionCode,String title,String content,Boolean executionGoal,String actorType,JsonNode contraindications,JsonNode professionalParameters,JsonNode references,Boolean isSelected,Integer sortOrder,List<Basis> basis) {}
}

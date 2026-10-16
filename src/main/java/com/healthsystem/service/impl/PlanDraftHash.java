package com.healthsystem.service.impl;
import com.healthsystem.common.json.*;
import com.healthsystem.entity.*;
import java.util.*;
/** Body hash deliberately excludes lifecycle status, change reason and row revision. */
public final class PlanDraftHash {
 private PlanDraftHash() {}
 public static String hash(CarePlanVersion v,PlanDraftStore.Content c,HealthInputSnapshot s){Map<String,Object> payload=new TreeMap<>();payload.put("title",v.getTitle());payload.put("startDate",v.getStartDate());payload.put("endDate",v.getEndDate());payload.put("zoneId",v.getZoneId());payload.put("primaryGoal",v.getPrimaryGoal());payload.put("goalBasis",JsonValues.read(v.getGoalBasisJson()));payload.put("supplementalRequirements",v.getSupplementalRequirements());payload.put("sourceSnapshotHash",s.getPayloadHash());payload.put("components",c);return CanonicalJson.hash(JsonValues.tree(payload));}
}

package com.healthsystem.service.impl;
import com.healthsystem.common.json.*;
import com.healthsystem.entity.TaskBundle;
import com.healthsystem.vo.PlanReviewContextVO.Issue;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.healthsystem.service.impl.TaskBundleStore.issue;
import static com.healthsystem.dto.WorkflowInput.*;
import static com.healthsystem.service.impl.PlanDraftValidation.check;

/** Pure deterministic calendar expansion. Does not interpret clinical free text. */
@Component
public class TaskSchedulePolicy {
    public record Expansion(List<JsonNode> occurrences,List<Issue> issues) {}
    public Expansion expand(TaskBundle bundle,List<JsonNode> groups) {
        var issues=new ArrayList<Issue>();var result=new ArrayList<JsonNode>();
        long days=ChronoUnit.DAYS.between(bundle.getStartDate(),bundle.getEndDate())+1;
        if(days<1 || days>90)return new Expansion(List.of(),List.of(issue("CYCLE_LIMIT","startDate","Cycle must contain 1..90 business days")));
        ZoneId zone=ZoneId.of(bundle.getZoneId());
        for(var g:groups) {
            String field="groups."+g.path("id").asText();var rule=g.path("schedule");
            if(!rule.isObject())continue;
            fields(rule,"isoWeekdays","slots","rotationPolicy","variantKeys","minRestDays","activeInputRequired");
            var weekdays=rule.path("isoWeekdays");var slots=rule.path("slots");
            if(!weekdays.isArray() || weekdays.isEmpty() || !slots.isArray() || slots.isEmpty() || slots.size()>24){issues.add(issue("INVALID_SCHEDULE",field,"Weekdays and 1..24 slots required"));continue;}
            var daySet=new HashSet<Integer>();boolean valid=true;
            for(var day:weekdays){if(!day.isIntegralNumber() || day.intValue()<1 || day.intValue()>7 || !daySet.add(day.intValue()))valid=false;}
            if(!valid){issues.add(issue("INVALID_WEEKDAY",field,"Unique ISO weekdays required"));continue;}
            String rotation=rule.path("rotationPolicy").asText("FIXED");
            var variants=new ArrayList<JsonNode>();for(var v:g.path("variants"))variants.add(v);
            if(rule.has("variantKeys")) {
                var keys=rule.path("variantKeys");check(keys.isArray()&&!keys.isEmpty(),"Invalid variant selection");
                var selected=new ArrayList<JsonNode>();var seen=new HashSet<String>();
                for(var k:keys){check(k.isString()&&seen.add(k.asText()),"Invalid duplicate variant");var v=variants.stream().filter(x->x.path("variantKey").asText().equals(k.asText())).findFirst().orElse(null);check(v!=null,"Unknown task variant");selected.add(v);}variants=selected;
            }
            if(!Set.of("FIXED","ROTATE").contains(rotation) || variants.isEmpty()) {issues.add(issue("INVALID_ROTATION",field,"Fixed or deterministic rotation required"));continue;}
            if("ROTATE".equals(rotation) && (!Set.of("diet","exercise").contains(g.path("dimensionCode").asText()) || variants.size()<2 || variants.size()>8)) {
                issues.add(issue("ROTATION_NOT_ALLOWED",field,"Only confirmed diet/exercise candidate pools may rotate"));continue;
            }
            if("FIXED".equals(rotation) && variants.size()>1)variants=new ArrayList<>(List.of(variants.get(0)));
            if("medication".equals(g.path("dimensionCode").asText()) && !CanonicalJson.text(rule).equals(CanonicalJson.text(g.path("professionalBinding").path("schedule")))) {
                issues.add(issue("MEDICATION_SCHEDULE_CHANGED",field,"Execution schedule must match the confirmed source order"));continue;
            }
            int rest=rule.path("minRestDays").asInt(0);check(rest>=0&&rest<=30,"Invalid rest interval");
            var keys=new HashSet<String>();var validSlots=new ArrayList<JsonNode>();
            for(var slot:slots) {
                fields(slot,"slotKey","mode","localTime","opensOffsetMinutes","closesOffsetMinutes","normalCutoffOffsetMinutes");
                String key=string(slot,"slotKey",true,64);
                if(!key.matches("[A-Za-z0-9._-]{1,64}") || !keys.add(key)){issues.add(issue("DUPLICATE_SLOT_KEY",field,"Unique safe slot keys required"));continue;}
                if(!"FIXED".equals(slot.path("mode").asText())){issues.add(issue("ANCHOR_NOT_SUPPORTED",field,"Actual event integration is not available"));continue;}
                if(!slot.path("localTime").isString() || !slot.path("localTime").asText().matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")){issues.add(issue("INVALID_LOCAL_TIME",field,"Use HH:mm"));continue;}
                for(String name:List.of("opensOffsetMinutes","closesOffsetMinutes"))check(slot.path(name).isIntegralNumber(),"Integer window offsets required");
                int open=slot.path("opensOffsetMinutes").intValue(),close=slot.path("closesOffsetMinutes").intValue();
                if(open < -1440 || close>2880 || close<=open){issues.add(issue("INVALID_WINDOW",field,"Execution window must be ordered and bounded"));continue;}
                validSlots.add(slot);
            }
            LocalDate previous=null;int dayIndex=0;
            for(LocalDate date=bundle.getStartDate();!date.isAfter(bundle.getEndDate());date=date.plusDays(1)) {
                if(!daySet.contains(date.getDayOfWeek().getValue()))continue;
                if(previous!=null && ChronoUnit.DAYS.between(previous,date)<=rest){issues.add(issue("REST_INTERVAL",field,"Selected execution days violate the confirmed rest interval"));continue;}
                var variant=variants.get("ROTATE".equals(rotation)?dayIndex%variants.size():0);dayIndex++;previous=date;
                var dayRows=new ArrayList<JsonNode>();
                for(var slot:validSlots) {
                    var local=date.atTime(LocalTime.parse(slot.path("localTime").asText()));
                    if(zone.getRules().getValidOffsets(local).size()!=1){issues.add(issue("AMBIGUOUS_LOCAL_TIME",field,"Ambiguous or missing local time requires human scheduling"));continue;}
                    Instant planned=local.atZone(zone).toInstant(),opens=planned.plusSeconds(slot.path("opensOffsetMinutes").longValue()*60),closes=planned.plusSeconds(slot.path("closesOffsetMinutes").longValue()*60);
                    Instant normal=closes;
                    if("routine".equals(g.path("dimensionCode").asText()))normal=date.plusDays(1).atTime(12,0).atZone(zone).toInstant();
                    if(slot.has("normalCutoffOffsetMinutes")){check(slot.path("normalCutoffOffsetMinutes").isIntegralNumber(),"Invalid submission cutoff");int offset=slot.path("normalCutoffOffsetMinutes").intValue();check(offset>=slot.path("closesOffsetMinutes").intValue()&&offset<=2880,"Invalid submission cutoff");Instant configured=planned.plusSeconds(offset*60L);
                        if("routine".equals(g.path("dimensionCode").asText()) && !configured.equals(normal)){issues.add(issue("FIXED_NORMAL_CUTOFF",field,"Routine submission cutoff must remain next business day 12:00"));continue;}normal=configured;}
                    if(normal.isBefore(closes)){issues.add(issue("INVALID_SUBMISSION_CUTOFF",field,"Submission cutoff is earlier than execution window end"));continue;}
                    Instant makeup=makeupDeadline(g.path("dimensionCode").asText(),date,normal,closes,zone);
                    if(makeup.isBefore(normal)){issues.add(issue("INVALID_DEADLINE_ORDER",field,"Submission and makeup rules cannot contain this execution window"));continue;}
                    var n=new LinkedHashMap<String,Object>();
                    n.put("occurrenceKey",g.path("id").asText()+":"+date+":"+slot.path("slotKey").asText());n.put("groupId",g.path("id").asText());n.put("variantId",variant.path("id").asText());
                    n.put("dimensionCode",g.path("dimensionCode").asText());n.put("executionDate",date.toString());n.put("zoneId",zone.getId());n.put("slotKey",slot.path("slotKey").asText());
                    n.put("plannedAt",planned.toString());n.put("opensAt",opens.toString());n.put("closesAt",closes.toString());n.put("normalCutoffAt",normal.toString());n.put("makeupCutoffAt",makeup.toString());
                    n.put("contentSnapshot",Map.of("ruleVersion",TaskTemplateCatalog.RULE_VERSION,"sourcePlanVersionId",bundle.getPlanVersionId(),"sourceReviewSubmissionId",bundle.getSourceReviewSubmissionId(),"sourceReviewDecisionId",bundle.getSourceReviewDecisionId(),"sourceContentHash",bundle.getSourcePlanHash(),"group",g,"variant",variant));
                    n.put("taskClass",g.path("taskClass").asText());n.put("priorityCode",g.path("priorityCode").asText());n.put("safetyFocus",g.path("safetyFocus").asBoolean());n.put("activeInputRequired",rule.path("activeInputRequired").asBoolean(false));
                    dayRows.add(JsonValues.tree(n));
                }
                dayRows.sort(Comparator.comparing(x->x.path("opensAt").asText()));
                for(int i=1;i<dayRows.size();i++)if(Instant.parse(dayRows.get(i).path("opensAt").asText()).isBefore(Instant.parse(dayRows.get(i-1).path("closesAt").asText())))issues.add(issue("SLOT_WINDOW_CONFLICT",field,"Execution windows of this group overlap"));
                result.addAll(dayRows);
                if(result.size()>5000)return new Expansion(List.of(),List.of(issue("INSTANCE_LIMIT","groups","More than 5000 occurrences; nothing was truncated or published")));
            }
        }
        result.sort(Comparator.comparing((JsonNode n)->n.path("plannedAt").asText()).thenComparing(n->n.path("priorityCode").asText()).thenComparing(n->n.path("occurrenceKey").asText()));
        var perDay=new TreeMap<String,List<JsonNode>>();for(var n:result)perDay.computeIfAbsent(n.path("executionDate").asText(),k->new ArrayList<>()).add(n);
        for(var day:perDay.entrySet()) {
            long active=day.getValue().stream().filter(n->n.path("activeInputRequired").asBoolean()).count();
            if(day.getValue().size()>displayLimit(bundle) || active>6)issues.add(issue("DAILY_LOAD_EXCEEDED",day.getKey(),"Daily task or active-input limit exceeded; safety items retained"));
        }
        // Match the confirmed medicine across groups, never just an incidental group UUID.
        var medication=result.stream().filter(n->"medication".equals(n.path("dimensionCode").asText())).sorted(Comparator.comparing(n->n.path("opensAt").asText())).toList();
        for(int i=0;i<medication.size();i++)for(int j=i+1;j<medication.size();j++) {
            var a=medication.get(i);var b=medication.get(j);String medicine=TaskMedicationIdentity.of(a.path("contentSnapshot").path("group"));
            if(medicine!=null && medicine.equals(TaskMedicationIdentity.of(b.path("contentSnapshot").path("group")))) {
                Instant next=Instant.parse(b.path("opensAt").asText()),normal=Instant.parse(a.path("normalCutoffAt").asText()),deadline=Instant.parse(a.path("makeupCutoffAt").asText());
                if(next.isBefore(normal))issues.add(issue("MEDICATION_WINDOW_CONFLICT",a.path("groupId").asText(),"Confirmed same-drug execution/submission windows overlap"));
                if(next.isBefore(deadline))((ObjectNode)a).put("makeupCutoffAt",next.toString());break;
            }
        }
        return new Expansion(List.copyOf(result),List.copyOf(issues));
    }
    private static int displayLimit(TaskBundle bundle) {
        if(bundle.getSourceSnapshotJson()==null)return 10;
        var values=new HashSet<String>();
        for(var f:JsonValues.read(bundle.getSourceSnapshotJson()).path("plan").path("inputSnapshot").path("facts"))
            if("functional_classification".equals(f.path("factCode").asText()) && "CONFIRMED_PROFESSIONAL".equals(f.path("confirmationStatus").asText()))values.add(f.path("value").path("value").asText());
        return values.equals(Set.of("CARE_DEPENDENT"))?12:10;
    }
    static Instant makeupDeadline(String dimension,LocalDate date,Instant normal,Instant closes,ZoneId zone) {
        Instant dayEnd=date.plusDays(1).atStartOfDay(zone).toInstant();
        return switch(dimension) {
            case "routine" -> date.plusDays(1).atTime(18,0).atZone(zone).toInstant();
            case "monitoring" -> earlier(closes.plusSeconds(12*3600),dayEnd);
            case "tcm" -> earlier(closes.plusSeconds(6*3600),dayEnd);
            case "medication" -> normal.plusSeconds(12*3600);
            default -> date.plusDays(2).atStartOfDay(zone).toInstant();
        };
    }
    private static Instant earlier(Instant a,Instant b){return a.isBefore(b)?a:b;}
}

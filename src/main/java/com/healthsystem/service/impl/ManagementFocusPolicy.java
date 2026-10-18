package com.healthsystem.service.impl;

import com.healthsystem.common.json.*;
import com.healthsystem.common.security.BusinessPrincipal;
import com.healthsystem.exception.BusinessException;
import com.healthsystem.vo.ManagementBasisVO;
import com.healthsystem.vo.ManagementBasisVO.*;
import com.healthsystem.vo.LegacyRecommendationsVO.DecisionExplanation;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.healthsystem.service.impl.PlanDraftValidation.check;

/** Source binding and integer arithmetic; never infers a diagnosis or activates medical thresholds. */
@Component
public class ManagementFocusPolicy {
    public static final String VERSION="management-doc-v1";
    public static final List<String> DIMENSIONS=List.of("medication","diet","exercise","routine","monitoring","tcm","other");
    private static final List<String> LABELS=List.of("用药","饮食","运动","起居","监测","中医","其他");
    private static final Map<String,Integer> SCORES=Map.of("重点管理",3,"加强管理",2,"常规管理",1);
    private static final Set<String> FIELDS=Set.of("evaluation","sourceHash","primarySubtype","secondarySubtypes","adjustmentReason","priority","weights");
    private final JsonNode catalog;
    private final String catalogHash;
    public ManagementFocusPolicy() {
        try { catalog=JsonValues.read(new ClassPathResource("ai/management-catalog-v1.json").getContentAsString(StandardCharsets.UTF_8)); }
        catch(Exception e){throw new IllegalStateException("Management catalog unavailable",e);}
        catalogHash=CanonicalJson.hash(catalog);
    }
    private record Diagnosis(String name,List<JsonNode> facts) {}
    private List<Diagnosis> diagnoses(JsonNode snapshot) {
        Map<String,List<JsonNode>> grouped=new TreeMap<>();
        for(var f:snapshot.path("facts")) {
            if(!f.path("factCode").asText().startsWith("diagnosis")||!"CONFIRMED_PROFESSIONAL".equals(f.path("confirmationStatus").asText()))continue;
            String name="";var value=f.path("value");
            if(value.isString())name=value.asText().trim();
            else for(String key:List.of("name","diagnosis","disease","value"))if(value.path(key).isString()){name=value.path(key).asText().trim();break;}
            if(name.isBlank())name=switch(f.path("factCode").asText()) {
                case "diagnosis.diabetes" -> "糖尿病";case "diagnosis.hypertension" -> "高血压";case "diagnosis.disability" -> "失能照护";default -> "未命名诊断："+f.path("id").asText();
            };
            name=switch(name){case "2型糖尿病","1型糖尿病" -> "糖尿病";case "失能" -> "失能照护";default -> name;};
            grouped.computeIfAbsent(name,k->new ArrayList<>()).add(f);
        }
        List<Diagnosis> result=new ArrayList<>();
        grouped.forEach((name,facts)->{facts.sort(Comparator.comparing(f->f.path("id").asText()));result.add(new Diagnosis(name,List.copyOf(facts)));});
        return result;
    }
    public String sourceHash(String org,JsonNode snapshot) {
        List<JsonNode> facts=new ArrayList<>();snapshot.path("facts").forEach(facts::add);
        facts.sort(Comparator.comparing(f->f.path("id").asText()));
        return CanonicalJson.hash(JsonValues.tree(Map.of("orgId",org,"patientId",snapshot.path("patient").path("id"),
                "profileRevision",snapshot.path("profileRevision"),"facts",facts,"conflicts",snapshot.path("conflicts"),"catalogHash",catalogHash)));
    }
    public ManagementBasisVO evaluate(String org,String archiveId,JsonNode snapshot) {
        List<Disease> diseases=new ArrayList<>();
        for(var d:diagnoses(snapshot)) {
            List<Option> options=new ArrayList<>();List<Match> matches=new ArrayList<>();
            for(var row:catalog.path("subtypes"))if(d.name().equals(row.path("disease").asText())) {
                String name=row.path("name").asText();
                options.add(new Option(name,"文档管理侧重点："+row.path("focus").asText()+"；须人工核对适用依据",percentLabels(row.path("weights"))));
                matches.add(new Match(name,"PENDING","本批未启用医学亚型自动判定规则","需核对亚型适用依据、个体目标及管理优先级",List.of()));
            }
            String basis=String.join("；",d.facts().stream().map(f->"已确认诊断事实 "+f.path("id").asText()+"，来源 "+f.path("sourceType").asText()).toList());
            diseases.add(new Disease(d.name(),"待评估","待确认","待确认",basis,Map.of(),options,matches,List.of(),VERSION));
        }
        return new ManagementBasisVO(archiveId,sourceHash(org,snapshot),diseases,
                "本次侧重评估不解释医嘱或调整药物；用药来源请查看患者资料。",
                new DecisionExplanation("依据授权患者当前已确认的诊断事实；选项来自补充文档4.3。",
                        "亚型与优先级待人工确认；不因疾病名称自动判级。","按来源核对并填写确认原因；无匹配规则的病种保持待确认。"));
    }
    private Map<String,Integer> percentLabels(JsonNode values) {
        Map<String,Integer> out=new LinkedHashMap<>();for(int i=0;i<7;i++)out.put(LABELS.get(i),values.path(DIMENSIONS.get(i)).intValue());return out;
    }
    public ObjectNode bind(BusinessPrincipal principal,JsonNode snapshot,JsonNode submitted) {
        check("DEMO".equals(principal.dataMode()),"Management confirmation currently supports DEMO only");
        var baseline=evaluate(principal.orgId(),"",snapshot);
        if(submitted==null||submitted.isNull())submitted=JsonValues.tree(List.of());
        check(submitted.isArray()&&submitted.size()<=20,"Invalid confirmedManagement collection");
        var context=(ObjectNode)JsonValues.tree(Map.of("ruleVersion",VERSION,"catalogHash",catalogHash,"sourceHash",baseline.sourceHash(),
                "profileRevision",snapshot.path("profileRevision"),"confirmationMode",submitted.isEmpty()?"UNCONFIRMED":"DEMO_OPERATOR_CONFIRMED",
                "confirmationInputs",submitted));
        if(!submitted.isEmpty())context.put("confirmedBy",principal.subjectId()).put("confirmedAt",OffsetDateTime.now(ZoneOffset.UTC).toString());
        var focuses=context.putArray("focuses");var names=new HashSet<String>();
        var diagnosisList=diagnoses(snapshot);
        Map<String,Disease> evaluations=new HashMap<>();baseline.diseases().forEach(d->evaluations.put(d.name(),d));
        var details=context.putArray("diseases");
        for(var input:submitted) {
            check(input.isObject()&&FIELDS.containsAll(input.propertyNames()),"Invalid confirmedManagement fields");
            check(input.path("sourceHash").isString()&&!input.path("sourceHash").asText().isBlank(),"sourceHash required");
            if(!baseline.sourceHash().equals(input.path("sourceHash").asText()))throw BusinessException.conflict("Management source changed or belongs to another patient; evaluate again");
            String name=input.path("evaluation").path("name").asText();
            check(names.add(name)&&evaluations.containsKey(name),"Invalid or duplicate disease evaluation");
            check(CanonicalJson.text(input.path("evaluation")).equals(CanonicalJson.text(JsonValues.tree(evaluations.get(name)))),"Disease evaluation was modified; use the original evaluated value");
            var diagnosis=diagnosisList.stream().filter(d->name.equals(d.name())).findFirst().orElseThrow();
            var ids=diagnosis.facts().stream().map(f->f.path("id").asText()).toList();
            for(var conflict:snapshot.path("conflicts"))for(var id:conflict.path("factIds"))
                check(!ids.contains(id.asText()),"Diagnosis sources conflict; resolve before confirmation");
            String subtype=requiredText(input,"primarySubtype",64),priority=requiredText(input,"priority",32);
            check(SCORES.containsKey(priority),"Priority must be 重点管理/加强管理/常规管理");
            JsonNode selected=null;Set<String> available=new HashSet<>();
            for(var row:catalog.path("subtypes"))if(name.equals(row.path("disease").asText())) {
                available.add(row.path("name").asText());if(subtype.equals(row.path("name").asText()))selected=row;
            }
            check(selected!=null,"Unknown management subtype for this disease");
            List<String> secondary=new ArrayList<>();var secondaryInput=input.path("secondarySubtypes");
            check(secondaryInput.isMissingNode()||secondaryInput.isNull()||secondaryInput.isArray()&&secondaryInput.size()<=10,"Invalid secondarySubtypes");
            for(var s:secondaryInput){check(s.isString()&&available.contains(s.asText())&&!subtype.equals(s.asText())&&!secondary.contains(s.asText()),"Invalid secondary subtype");secondary.add(s.asText());}
            String reason=requiredText(input,"adjustmentReason",2000);
            var weights=input.hasNonNull("weights")?weights(input.path("weights")):weights(selected.path("weights"));
            focuses.add(JsonValues.tree(Map.of("diagnosisFactId",ids.get(0),"primarySubtypeCode",subtype,"secondarySubtypeCodes",secondary,
                    "priorityCode",priority,"dimensionWeights",weights,"adjustmentReason",reason)));
            details.add(JsonValues.tree(Map.of("name",name,"diagnosisFactId",ids.get(0),"sourceFactIds",ids,"focus",selected.path("focus").asText(),"score",SCORES.get(priority))));
        }
        // Order is stable regardless of frontend selection ordering.
        List<JsonNode> ordered=new ArrayList<>();focuses.forEach(ordered::add);ordered.sort(Comparator.comparing(f->f.path("diagnosisFactId").asText()));focuses.removeAll();ordered.forEach(focuses::add);
        List<String> unconfirmed=diagnosisList.stream().map(Diagnosis::name).filter(n->!names.contains(n)).toList();
        var fusion=context.putObject("fusion");fusion.put("ruleVersion",VERSION);fusion.set("unconfirmedDiseases",JsonValues.tree(unconfirmed));
        fusion.put("status",focuses.isEmpty()||!unconfirmed.isEmpty()?"PENDING":"CONFIRMED_DEMO");
        fusion.put("note","3/2/1 为文档内部候选分值，仅用于 DEMO；权重不授权用药、中医执行，不替代审核。共享行为只生成一条建议，冲突优先安全与照护可行性。");
        fusion.set("dimensionWeights",JsonValues.tree(Map.of()));
        if(!focuses.isEmpty()&&unconfirmed.isEmpty()) {
            int total=0;long[] numerators=new long[7];List<Integer> scores=new ArrayList<>();
            for(var f:focuses){int score=SCORES.get(f.path("priorityCode").asText());scores.add(score);total+=score;
                for(int i=0;i<7;i++)numerators[i]+=(long)f.path("dimensionWeights").path(DIMENSIONS.get(i)).intValue()*score;}
            var fused=roundToBps(numerators,total);Map<String,Integer> values=new LinkedHashMap<>();for(int i=0;i<7;i++)values.put(DIMENSIONS.get(i),fused[i]);
            fusion.set("dimensionWeights",JsonValues.tree(values));
            long[] shares=scores.stream().mapToLong(s->s*10000L).toArray();var rounded=roundToBps(shares,total);
            for(int i=0;i<focuses.size();i++)((ObjectNode)focuses.get(i)).put("contributionBps",rounded[i]);
        }
        return context;
    }
    private static String requiredText(JsonNode n,String key,int limit) {
        check(n.path(key).isString()&&!n.path(key).asText().isBlank()&&n.path(key).asText().length()<=limit,"Missing or invalid "+key);return n.path(key).asText();
    }
    private static Map<String,Integer> weights(JsonNode n) {
        check(n.isObject()&&n.size()==7,"Weights must cover seven dimensions in integer percent");
        Map<String,Integer> out=new LinkedHashMap<>();int total=0;
        for(var e:n.properties()) {
            String key=e.getKey();int label=LABELS.indexOf(key);if(label>=0)key=DIMENSIONS.get(label);
            if("作息".equals(key))key="routine";if("中医康复".equals(key))key="tcm";
            check(DIMENSIONS.contains(key)&&!out.containsKey(key)&&e.getValue().isIntegralNumber()&&e.getValue().canConvertToInt(),"Invalid dimension or weight");
            int value=e.getValue().intValue();check(value>=0&&value<=100,"Weight outside 0..100 percent");total+=value;out.put(key,value*100);
        }
        check(total==100,"Weights must total 100 percent");return out;
    }
    /** Round once to 0.1%. Apply residual to the largest unrounded weight; ties use stable dimension order. */
    static int[] roundToBps(long[] numerators,int denominator) {
        int[] result=new int[numerators.length];int sum=0,best=0;
        for(int i=0;i<numerators.length;i++) {
            result[i]=(int)((numerators[i]*2+10L*denominator)/(20L*denominator))*10;sum+=result[i];
            if(numerators[i]>numerators[best])best=i;
        }
        result[best]+=10000-sum;return result;
    }
    /** Projection sent to the provider deliberately excludes operator identity and original evaluation text. */
    public static JsonNode modelContext(JsonNode context) {
        return JsonValues.tree(Map.of("ruleVersion",context.path("ruleVersion"),"focuses",context.path("focuses"),
                "diseases",context.path("diseases"),"fusion",context.path("fusion")));
    }
    static void requireFrozenChoices(List<com.healthsystem.dto.PlanComponents.Focus> focuses,JsonNode context) {
        check(focuses.size()==context.path("focuses").size(),"Management changes require a new source-bound confirmation");
        Map<String,JsonNode> expected=new HashMap<>();for(var f:context.path("focuses"))expected.put(f.path("diagnosisFactId").asText(),f);
        for(var focus:focuses) {
            var actual=JsonValues.tree(focus);var original=expected.get(focus.diagnosisFactId());
            check(original!=null,"Management diagnosis differs from confirmed source");
            for(String field:List.of("primarySubtypeCode","secondarySubtypeCodes","priorityCode","dimensionWeights","contributionBps","adjustmentReason"))
                check(CanonicalJson.text(actual.path(field)).equals(CanonicalJson.text(original.path(field))),"Management changes require a new source-bound confirmation");
        }
    }
}

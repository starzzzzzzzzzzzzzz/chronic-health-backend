package com.healthsystem.service.impl;

import com.healthsystem.common.json.JsonValues;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class AiPlanPolicyTest {
    static final String SNAPSHOT="""
      {"patient":{"name":"不应发送的姓名"},"facts":[{"id":"fact-1","axisCode":"B","factCode":"routine","value":{"value":"晚睡"},"confirmationStatus":"REPORTED","sourceType":"DEMO"}]}
      """;
    public static String candidate() {
        return """
        {"schemaVersion":"ai-plan-v1","primaryGoal":"先改善晚睡习惯","goalFactIds":["fact-1"],
         "modules":[{"moduleCode":"L","selectionReason":"本人自述晚睡","factIds":["fact-1"]}],
         "managementFocuses":[],"dimensions":[
          {"dimensionCode":"routine","decision":"ENABLED","decisionReason":"有晚睡问题"},
          {"dimensionCode":"medication","decision":"CLOSED","decisionReason":"没有用药依据"},
          {"dimensionCode":"diet","decision":"CLOSED","decisionReason":"缺少饮食资料"},
          {"dimensionCode":"exercise","decision":"CLOSED","decisionReason":"缺少活动资料"},
          {"dimensionCode":"monitoring","decision":"CLOSED","decisionReason":"无监测依据"},
          {"dimensionCode":"tcm","decision":"CLOSED","decisionReason":"无已审模板"},
          {"dimensionCode":"other","decision":"CLOSED","decisionReason":"暂无协同需要"}],
         "items":[{"dimensionCode":"routine","title":"记录睡前安排","content":"睡前记录当天上床时间，次日回看晚睡原因。","executionGoal":true,"actorType":"PATIENT","isSelected":true,"basis":[{"moduleCode":"L","factId":"fact-1"}]}],
         "missingInformation":["平时上床和起床时间是什么？"],"warnings":[]}
        """;
    }
    @Test void producesEditablePatchWithTraceableFactsAndNoFabricatedSources() {
        var patch=new AiPlanPolicy().validate(candidate(),JsonValues.read(SNAPSHOT));
        assertThat(patch.path("goalBasis").path("factIds").get(0).asText()).isEqualTo("fact-1");
        assertThat(patch.path("dimensions").size()).isEqualTo(7);
        assertThat(patch.path("items").get(0).hasNonNull("id")).isFalse();
    }
    @Test void rejectsForeignFactsAndInventedDiagnoses() {
        var policy=new AiPlanPolicy();
        assertThatThrownBy(()->policy.validate(candidate().replace("fact-1","foreign"),JsonValues.read(SNAPSHOT))).hasMessageContaining("fact");
        assertThatThrownBy(()->policy.validate(candidate().replace("\"managementFocuses\":[]","\"managementFocuses\":[{\"diagnosisFactId\":\"fact-1\"}]"),JsonValues.read(SNAPSHOT))).hasMessageContaining("diagnosis");
    }
    @Test void rejectsUnsupportedProfessionalContentAndUnexpectedRootFields() {
        assertThatThrownBy(()->new AiPlanPolicy().validate(candidate().replace("\"dimensionCode\":\"routine\"","\"dimensionCode\":\"medication\""),JsonValues.read(SNAPSHOT))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->new AiPlanPolicy().validate(candidate().replace("\"warnings\":[]","\"warnings\":[],\"approved\":true"),JsonValues.read(SNAPSHOT))).hasMessageContaining("field");
    }
    @Test void cannotTreatSelfReportedFactsAsConfirmedDiseaseModule() {
        assertThatThrownBy(()->new AiPlanPolicy().validate(candidate().replace("\"moduleCode\":\"L\"","\"moduleCode\":\"C\""),JsonValues.read(SNAPSHOT))).hasMessageContaining("confirmed diagnosis");
    }
    @Test void excludesIdentityFromModelInputAndPreservesUnknownFacts() {
        String input=new AiPlanPolicy().input(JsonValues.read(SNAPSHOT),"补充说明").toString();
        assertThat(input).doesNotContain("不应发送的姓名").contains("REPORTED","fact-1","补充说明");
    }
    @Test void aggregatesOnlyExplicitExistingBasisIntoModuleIndexAndRecordsTheChange() {
        var snapshot=(tools.jackson.databind.node.ObjectNode)JsonValues.read(SNAPSHOT);
        ((tools.jackson.databind.node.ArrayNode)snapshot.path("facts")).add(JsonValues.tree(java.util.Map.of("id","fact-2","axisCode","F","factCode","function","confirmationStatus","REPORTED")));
        String raw=candidate().replace("\"factId\":\"fact-1\"","\"factId\":\"fact-2\"");
        var policy=new AiPlanPolicy();var patch=policy.validate(raw,snapshot);
        assertThat(patch.path("modules").get(0).path("factIds").toString()).isEqualTo("[\"fact-1\",\"fact-2\"]");
        assertThat(policy.normalizations(JsonValues.read(raw),patch)).containsExactly(java.util.Map.of("code","MODULE_FACT_INDEX_COMPLETED","moduleCode","L","factId","fact-2"));
        assertThat(JsonValues.read(raw).path("modules").get(0).path("factIds").size()).isEqualTo(1);
    }
    @Test void aggregationCannotInventFactsOrModulesOrHideDuplicateBasis() {
        var policy=new AiPlanPolicy();var snapshot=JsonValues.read(SNAPSHOT);
        assertThatThrownBy(()->policy.validate(candidate().replace("\"factId\":\"fact-1\"","\"factId\":\"foreign\""),snapshot)).hasMessageContaining("basis");
        assertThatThrownBy(()->policy.validate(candidate().replace("\"basis\":[{\"moduleCode\":\"L\"","\"basis\":[{\"moduleCode\":\"A\""),snapshot)).hasMessageContaining("basis");
        assertThatThrownBy(()->policy.validate(candidate().replace("\"basis\":[{\"moduleCode\":\"L\",\"factId\":\"fact-1\"}]","\"basis\":[{\"moduleCode\":\"L\",\"factId\":\"fact-1\"},{\"moduleCode\":\"L\",\"factId\":\"fact-1\"}]"),snapshot)).hasMessageContaining("Duplicate basis");
    }
    @Test void enabledDimensionMustContainSelectedAdvice() {
        assertThatThrownBy(()->new AiPlanPolicy().validate(candidate().replace("\"isSelected\":true","\"isSelected\":false"),JsonValues.read(SNAPSHOT)))
            .hasMessageContaining("Enabled dimension requires");
    }
}

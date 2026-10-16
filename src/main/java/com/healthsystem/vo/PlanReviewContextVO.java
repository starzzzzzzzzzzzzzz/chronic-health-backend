package com.healthsystem.vo;
import java.util.List;
public record PlanReviewContextVO(CarePlanDraftVO plan,ConditionConfirmationVO confirmations,List<Issue> issues,List<ReviewSubmissionVO> submissions) {
    public record Issue(String code,String severity,String fieldPath,String message) {}
}

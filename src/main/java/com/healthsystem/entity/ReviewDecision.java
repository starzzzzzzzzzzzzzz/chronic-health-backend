package com.healthsystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("review_decision")
public class ReviewDecision extends ScopedEntity {
    @TableField(value="submission_id", updateStrategy=FieldStrategy.ALWAYS)
    private String submissionId;
    public String getSubmissionId() {return submissionId;}
    public void setSubmissionId(String value) {submissionId=value;}
    @TableField(value="decision", updateStrategy=FieldStrategy.ALWAYS)
    private String decision;
    public String getDecision() {return decision;}
    public void setDecision(String value) {decision=value;}
    @TableField(value="comment", updateStrategy=FieldStrategy.ALWAYS)
    private String comment;
    public String getComment() {return comment;}
    public void setComment(String value) {comment=value;}
    @TableField(value="reviewer_id", updateStrategy=FieldStrategy.ALWAYS)
    private String reviewerId;
    public String getReviewerId() {return reviewerId;}
    public void setReviewerId(String value) {reviewerId=value;}
    @TableField(value="reviewed_at", updateStrategy=FieldStrategy.ALWAYS)
    private LocalDateTime reviewedAt;
    public LocalDateTime getReviewedAt() {return reviewedAt;}
    public void setReviewedAt(LocalDateTime value) {reviewedAt=value;}
}

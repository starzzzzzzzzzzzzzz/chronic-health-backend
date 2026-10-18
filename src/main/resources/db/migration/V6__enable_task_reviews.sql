-- Extend the existing closed PLAN-only target constraint after TASK tables exist.
ALTER TABLE review_submission DROP CHECK review_submission_chk_1;
ALTER TABLE review_submission
 ADD CONSTRAINT ck_review_stage_target CHECK(
   (stage='PLAN' AND plan_version_id IS NOT NULL AND task_bundle_id IS NULL AND schedule_preview_id IS NULL)
   OR (stage='TASK' AND plan_version_id IS NULL AND task_bundle_id IS NOT NULL AND schedule_preview_id IS NOT NULL)),
 ADD CONSTRAINT fk_review_task_bundle FOREIGN KEY(org_id,task_bundle_id) REFERENCES task_bundle(org_id,id),
 ADD CONSTRAINT fk_review_task_preview FOREIGN KEY(org_id,schedule_preview_id) REFERENCES schedule_preview(org_id,id),
 ADD UNIQUE KEY uk_pending_task(org_id,task_bundle_id,pending_slot);

export const dimensions = [
  { code: "medication", label: "用药", icon: "pill" },
  { code: "diet", label: "饮食", icon: "leaf" },
  { code: "exercise", label: "运动", icon: "activity" },
  { code: "routine", label: "起居", icon: "moon" },
  { code: "monitoring", label: "监测", icon: "heart" },
  { code: "tcm", label: "中医", icon: "flower" },
  { code: "other", label: "其他", icon: "grid" },
];
export const axes = [
  { code: "H", name: "健康阶段", icon: "heart" },
  { code: "F", name: "功能状态", icon: "activity" },
  { code: "B", name: "生活方式", icon: "moon" },
  { code: "C", name: "照护支持", icon: "people" },
  { code: "E", name: "执行条件", icon: "check" },
  { code: "G", name: "当前问题", icon: "target" },
];
export const decisions = {
  ENABLED: "已纳入",
  CLOSED: "本期不纳入",
  PENDING: "待确认",
};
export const actors = {
  PATIENT: "患者本人",
  CAREGIVER: "照护者",
  PROFESSIONAL: "专业人员",
  SHARED: "共同执行",
};
export const jobLabels = {
  PENDING: "正在排队",
  RUNNING: "正在生成",
  SUCCEEDED: "候选已就绪",
  REJECTED: "候选未通过校验",
  FAILED: "生成失败",
  APPLIED: "已采纳为草稿",
};
export const moduleNames = {
  B0: "基础生活",
  L: "习惯改善",
  R: "风险复核",
  C: "慢病管理",
  F: "功能照护",
  A: "支持协同",
};
export const confirmationLabels = {
  REPORTED: "自述",
  UNKNOWN: "待核实",
  CONFIRMED_PROFESSIONAL: "专业确认",
  CONFIRMED_PATIENT: "本人确认",
  CONFIRMED_CAREGIVER: "照护者确认",
};
export function factText(value) {
  if (value == null) return "暂无记录";
  if (typeof value !== "object") return String(value);
  if ("value" in value) return factText(value.value);
  return Object.entries(value)
    .map(([k, v]) => `${k}：${factText(v)}`)
    .join("；");
}
export function day(offset = 0) {
  const now = new Date();
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const get = (type) => parts.find((p) => p.type === type).value;
  const date = new Date(
    `${get("year")}-${get("month")}-${get("day")}T12:00:00+08:00`,
  );
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}
export function clone(value) {
  return JSON.parse(JSON.stringify(value));
}
export function editableDraft(draft) {
  return clone({
    primaryGoal: draft.primaryGoal || "",
    dimensions: draft.dimensions,
    items: draft.items,
  });
}
export function draftPatch(edit, revision) {
  // Explicit allow-list: no status, snapshot, AI metadata or arbitrary server fields.
  return {
    expectedRevision: revision,
    primaryGoal: edit.primaryGoal,
    dimensions: clone(edit.dimensions),
    items: clone(edit.items),
    changeReason: "在本地方案工作台人工调整建议",
  };
}
export function validationMessage(edit) {
  if (!edit.primaryGoal.trim()) return "请先填写本周期主目标。";
  for (const d of edit.dimensions) {
    if (d.decision !== "ENABLED" && !d.decisionReason?.trim())
      return "未纳入或待确认的维度需要填写原因。";
    if (
      d.decision !== "ENABLED" &&
      edit.items.some(
        (i) => i.dimensionCode === d.dimensionCode && i.isSelected,
      )
    )
      return "请先取消该维度中的建议，再调整维度状态。";
  }
  for (const i of edit.items)
    if (!i.title?.trim() || !i.content?.trim())
      return "每条建议的标题和内容不能为空。";
  return "";
}

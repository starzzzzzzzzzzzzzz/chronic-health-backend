<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from "vue";
import Icon from "./Icon.vue";
import { api } from "./api.js";
import {
  dimensions,
  axes,
  decisions,
  actors,
  jobLabels,
  moduleNames,
  confirmationLabels,
  factText,
  day,
  editableDraft,
  draftPatch,
  validationMessage,
} from "./domain.js";
const apiDocsUrl = __LOCAL_API_DOCS_URL__;

const patients = ref([]),
  total = ref(0),
  page = ref(1),
  query = ref(""),
  listLoading = ref(false),
  connection = ref("connecting");
const patient = ref(null),
  summary = ref(null),
  draft = ref(null),
  job = ref(null),
  history = ref([]),
  edit = ref(null);
const tab = ref("profile"),
  dimension = ref("all"),
  busy = ref(""),
  loadingProfile = ref(false),
  polling = ref(false);
const notice = ref(null),
  basis = ref(null),
  openVersion = ref(""),
  showOpen = ref(false),
  historyOpen = ref(false),
  historyPreview = ref(false);
const intent = ref(null);
const form = reactive({
  title: "",
  startDate: day(),
  endDate: day(6),
  requirements: "",
});
let pollTimer,
  searchTimer,
  selection = 0,
  listRequest = 0;
const storageKey = "chronic-demo-workbench-v1";
let saved = {};
try {
  saved = JSON.parse(localStorage.getItem(storageKey) || "{}");
} catch {
  /* Fresh local workspace. */
}
function persist() {
  if (!patient.value) return;
  saved.selected = patient.value.id;
  saved[patient.value.id] = {
    versionId: draft.value?.versionId || null,
    intent: intent.value,
  };
  try {
    localStorage.setItem(storageKey, JSON.stringify(saved));
  } catch {
    /* UI works without persistence. */
  }
}
const activeJob = computed(() =>
  ["PENDING", "RUNNING"].includes(job.value?.status),
);
const locked = computed(
  () =>
    !!busy.value || loadingProfile.value || activeJob.value || !!intent.value,
);
const editing = computed(
  () =>
    !historyPreview.value &&
    !!draft.value?.items.length &&
    !["PENDING", "RUNNING", "SUCCEEDED"].includes(job.value?.status) &&
    draft.value.status === "DRAFT",
);
const dirty = computed(
  () =>
    !!edit.value &&
    JSON.stringify(edit.value) !== JSON.stringify(editableDraft(draft.value)),
);
const candidate = computed(() =>
  job.value?.status === "SUCCEEDED" || historyPreview.value
    ? job.value?.candidate
    : null,
);
const content = computed(
  () =>
    candidate.value ||
    (historyPreview.value ? null : editing.value ? edit.value : draft.value),
);
const hasPlan = computed(() => !!content.value?.dimensions?.length);
const selectedCount = computed(
  () => content.value?.items?.filter((i) => i.isSelected).length || 0,
);
const enabledCount = computed(
  () =>
    content.value?.dimensions?.filter((d) => d.decision === "ENABLED").length ||
    0,
);
const pendingCount = computed(
  () =>
    content.value?.dimensions?.filter((d) => d.decision === "PENDING").length ||
    0,
);
const visibleDimensions = computed(() =>
  dimensions.filter(
    (d) => dimension.value === "all" || d.code === dimension.value,
  ),
);
const canAdopt = computed(
  () =>
    !historyPreview.value &&
    candidate.value &&
    draft.value?.status === "DRAFT" &&
    draft.value.rowVersion === job.value.baseRevision,
);
const shortName = (p) => p?.name.replace(/^AI虚构-/, "") || "";
const formatDate = (t) =>
  t
    ? new Date(t).toLocaleString("zh-CN", {
        hour12: false,
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
const facts = computed(() =>
  draft.value && job.value?.status === "APPLIED"
    ? draft.value.inputSnapshot.facts
    : summary.value?.facts || [],
);
const errorLabels = {
  AI_TIMEOUT: "模型响应超时，可重新生成。",
  AI_RATE_LIMITED: "模型服务限流，稍后重试。",
  AI_AUTH_FAILED: "模型凭证不可用，请检查后端 AI 配置。",
  AI_INSUFFICIENT_BALANCE: "模型账户余额不足。",
  AI_OUTPUT_REJECTED: "输出未通过结构或来源校验，已保留失败记录。",
  AI_RULE_VERSION_CHANGED: "生成规则已更新，请重新生成。",
  AI_OUTPUT_INCOMPLETE: "输出不完整，请重新生成。",
  AI_ACCESS_REVOKED: "当前患者授权已失效。",
};
function notify(text, kind = "success", requestId = "") {
  notice.value = { text, kind, requestId };
}
function handleError(error) {
  const text =
    error.status === 409
      ? "资料或草稿已发生变化。你的修改仍保留，请重新载入后核对，不能直接覆盖。"
      : error.status === 403
        ? "当前身份没有此操作权限，请核对患者授权。"
        : error.status === 401
          ? "本地 DEMO 身份未启用，请使用项目 run-demo.sh 启动后端。"
          : error.message;
  notify(text, "error", error.requestId);
}
function setDraft(data) {
  draft.value = data;
  edit.value = editableDraft(data);
  Object.assign(form, {
    title: data.title,
    startDate: data.startDate,
    endDate: data.endDate,
    requirements: data.supplementalRequirements || "",
  });
  persist();
}
async function loadPatients() {
  const call = ++listRequest;
  listLoading.value = true;
  try {
    const data = await api(
      `/patients?page=${page.value}&size=20&q=${encodeURIComponent(query.value)}`,
    );
    if (call !== listRequest) return;
    patients.value = data.items;
    total.value = data.total;
    connection.value = "connected";
  } catch (e) {
    if (call === listRequest) {
      handleError(e);
      connection.value = "error";
    }
  } finally {
    if (call === listRequest) listLoading.value = false;
  }
}
function search() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    page.value = 1;
    loadPatients();
  }, 300);
}
function allowDiscard() {
  return (
    !dirty.value || window.confirm("有尚未保存的方案修改，确定放弃这些修改吗？")
  );
}
async function choosePatient(p) {
  if (
    locked.value ||
    (patient.value?.id === p.id && summary.value) ||
    !allowDiscard()
  )
    return;
  const epoch = ++selection;
  clearTimeout(pollTimer);
  patient.value = p;
  summary.value = null;
  draft.value = null;
  edit.value = null;
  job.value = null;
  history.value = [];
  intent.value = null;
  historyPreview.value = false;
  notice.value = null;
  tab.value = "profile";
  dimension.value = "all";
  loadingProfile.value = true;
  Object.assign(form, {
    title: `${shortName(p)} · 健康管理方案`,
    startDate: day(),
    endDate: day(6),
    requirements: "",
  });
  try {
    const data = await api(`/patients/${p.id}/health-summary`);
    if (epoch !== selection) return;
    summary.value = data;
    const previous = saved[p.id];
    if (previous?.versionId) {
      try {
        const d = await api(`/plan-versions/${previous.versionId}`);
        if (d.patientId !== p.id) throw new Error("保存的方案不属于当前患者。");
        intent.value = previous.intent || null;
        setDraft(d);
        await refreshHistory();
      } catch (e) {
        handleError(e);
      }
    }
    persist();
  } catch (e) {
    handleError(e);
  } finally {
    if (epoch === selection) loadingProfile.value = false;
  }
}
async function refreshHistory() {
  if (!draft.value) return;
  history.value = await api(
    `/plan-versions/${draft.value.versionId}/generation-jobs`,
  );
  historyPreview.value = false;
  job.value = history.value[0] || null;
  if (activeJob.value) {
    tab.value = "plan";
    poll();
  }
}
async function poll() {
  clearTimeout(pollTimer);
  if (!job.value?.id) return;
  const id = job.value.id;
  polling.value = true;
  try {
    const data = await api(`/plan-generation-jobs/${id}`);
    if (job.value?.id !== id) return;
    job.value = data;
    history.value = [data, ...history.value.filter((h) => h.id !== id)];
    if (["PENDING", "RUNNING"].includes(data.status))
      pollTimer = setTimeout(poll, 2000);
    else if (data.status === "SUCCEEDED") {
      tab.value = "plan";
      notify("候选已生成。请先核对目标、建议和待确认信息，再采纳。");
    } else if (["FAILED", "REJECTED"].includes(data.status))
      notify(
        errorLabels[data.errorCode] || "生成未完成，请查看记录后重试。",
        "error",
      );
  } catch (e) {
    handleError(
      e,
    ); /* Stop on network error. Resume uses the same job, never a new billable call. */
  } finally {
    polling.value = false;
  }
}
async function generate() {
  if (busy.value || activeJob.value || historyPreview.value || !summary.value)
    return;
  if (dirty.value)
    return notify("请先保存或放弃当前修改，再生成新的候选。", "error");
  if (intent.value) return recoverSubmission();
  if (
    !form.title.trim() ||
    !form.startDate ||
    !form.endDate ||
    form.endDate < form.startDate
  )
    return notify("请填写方案名称，并选择有效的起止日期。", "error");
  busy.value = "generate";
  notice.value = null;
  try {
    if (!draft.value) {
      const data = await api("/care-plans", {
        method: "POST",
        body: {
          patientId: patient.value.id,
          expectedProfileRevision: summary.value.profileRevision,
          title: form.title,
          startDate: form.startDate,
          endDate: form.endDate,
          zoneId: "Asia/Shanghai",
        },
      });
      const requirements = form.requirements;
      setDraft(data);
      form.requirements = requirements;
    }
    if (draft.value.status !== "DRAFT")
      throw new Error("当前方案已送审或批准。请新建本地演示方案后再生成。");
    summary.value = await api(`/patients/${patient.value.id}/health-summary`);
    if ((draft.value.supplementalRequirements || "") !== form.requirements)
      setDraft(
        await api(`/plan-versions/${draft.value.versionId}`, {
          method: "PATCH",
          body: {
            expectedRevision: draft.value.rowVersion,
            supplementalRequirements: form.requirements,
          },
        }),
      );
    const body = {
      expectedRevision: draft.value.rowVersion,
      expectedProfileRevision: summary.value.profileRevision,
    };
    if (["FAILED", "REJECTED"].includes(job.value?.status))
      body.retryOfJobId = job.value.id;
    intent.value = { key: crypto.randomUUID(), body };
    persist();
    await submitIntent();
  } catch (e) {
    handleError(e);
  } finally {
    busy.value = "";
  }
}
async function submitIntent() {
  try {
    job.value = await api(
      `/plan-versions/${draft.value.versionId}/generation-jobs`,
      { method: "POST", body: intent.value.body, key: intent.value.key },
    );
    intent.value = null;
    persist();
    tab.value = "plan";
    poll();
  } catch (e) {
    if (e.status >= 400 && e.status < 500) {
      intent.value = null;
      persist();
    }
    throw e;
  }
}
async function recoverSubmission() {
  if (!intent.value || busy.value) return;
  busy.value = "recover";
  try {
    await submitIntent();
  } catch (e) {
    handleError(e);
  } finally {
    busy.value = "";
  }
}
async function adopt() {
  if (!canAdopt.value || busy.value) return;
  busy.value = "adopt";
  try {
    setDraft(
      await api(`/plan-generation-jobs/${job.value.id}/adoptions`, {
        method: "POST",
        body: { expectedRevision: draft.value.rowVersion },
      }),
    );
    job.value = await api(`/plan-generation-jobs/${job.value.id}`);
    tab.value = "plan";
    notify("已采纳为草稿。现在可以调整建议，再保存修改。");
  } catch (e) {
    handleError(e);
  } finally {
    busy.value = "";
  }
}
async function save() {
  if (!dirty.value || busy.value || !editing.value) return;
  const issue = validationMessage(edit.value);
  if (issue) return notify(issue, "error");
  busy.value = "save";
  try {
    setDraft(
      await api(`/plan-versions/${draft.value.versionId}`, {
        method: "PATCH",
        body: draftPatch(edit.value, draft.value.rowVersion),
      }),
    );
    notify("修改已保存到后端，方案仍为待审核草稿。");
  } catch (e) {
    handleError(e);
  } finally {
    busy.value = "";
  }
}
async function reloadDraft() {
  if (!draft.value || busy.value || !allowDiscard()) return;
  busy.value = "reload";
  try {
    setDraft(await api(`/plan-versions/${draft.value.versionId}`));
    await refreshHistory();
    notify("已重新载入后端草稿。");
  } catch (e) {
    handleError(e);
  } finally {
    busy.value = "";
  }
}
async function openExisting() {
  if (!openVersion.value.trim() || locked.value || !allowDiscard()) return;
  busy.value = "open";
  try {
    const d = await api(
      `/plan-versions/${encodeURIComponent(openVersion.value.trim())}`,
    );
    if (d.patientId !== patient.value.id)
      throw new Error("这个方案不属于当前患者，请先选择对应患者。");
    intent.value = null;
    setDraft(d);
    await refreshHistory();
    tab.value = d.items.length ? "plan" : "profile";
    showOpen.value = false;
    notify("已打开方案。");
  } catch (e) {
    handleError(e);
  } finally {
    busy.value = "";
  }
}
function newDraft() {
  if (locked.value || !allowDiscard()) return;
  draft.value = null;
  edit.value = null;
  job.value = null;
  history.value = [];
  intent.value = null;
  historyPreview.value = false;
  tab.value = "profile";
  notice.value = null;
  Object.assign(form, {
    title: `${shortName(patient.value)} · 健康管理方案`,
    startDate: day(),
    endDate: day(6),
    requirements: "",
  });
  persist();
}
function viewHistory(h) {
  if (!allowDiscard()) return;
  edit.value = draft.value ? editableDraft(draft.value) : null;
  job.value = h;
  historyPreview.value = true;
  tab.value = "plan";
  historyOpen.value = false;
}
function returnToDraft() {
  historyPreview.value = false;
  job.value = history.value[0] || null;
}
function dimState(code) {
  return content.value?.dimensions?.find((d) => d.dimensionCode === code);
}
function dimItems(code) {
  return content.value?.items?.filter((i) => i.dimensionCode === code) || [];
}
function basisFact(id) {
  return historyPreview.value ? null : facts.value.find((f) => f.id === id);
}
function onLeave(e) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
function onKey(e) {
  if (e.key === "Escape") {
    basis.value = null;
    showOpen.value = false;
    historyOpen.value = false;
  }
}
onMounted(async () => {
  window.addEventListener("beforeunload", onLeave);
  window.addEventListener("keydown", onKey);
  await loadPatients();
  const p =
    patients.value.find((p) => p.id === saved.selected) ||
    patients.value.find((p) => p.name.includes("晚睡生活习惯")) ||
    patients.value[0];
  if (p) await choosePatient(p);
});
onUnmounted(() => {
  clearTimeout(pollTimer);
  clearTimeout(searchTimer);
  window.removeEventListener("beforeunload", onLeave);
  window.removeEventListener("keydown", onKey);
});
</script>

<template>
  <div class="app-shell">
    <aside class="rail">
      <a class="brand" href="/" aria-label="慢病健康管理首页"
        ><span class="brand-mark"><Icon name="plus" :size="25" /></span
        ><span>慢病健康管理<small>HEALTH WORKSPACE</small></span></a
      >
      <div class="rail-caption">医生工作台</div>
      <div class="rail-nav active">
        <Icon name="grid" /><span>方案工作台</span>
      </div>
      <a class="rail-nav" :href="apiDocsUrl" target="_blank" rel="noopener"
        ><Icon name="file" /><span>接口文档</span><Icon name="arrow" :size="15"
      /></a>
      <div class="rail-note">
        <Icon name="info" :size="17" />
        <p>本地演示环境<br /><span>使用虚构患者资料</span></p>
      </div>
      <div class="operator">
        <span class="avatar small">D</span>
        <div>DEMO 编辑者<small>本地联调</small></div>
        <span class="online-dot"></span>
      </div>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <div class="breadcrumb">
          医生工作台 <Icon name="chevron" :size="13" /><strong>健康方案</strong>
        </div>
        <div class="connection" :class="connection">
          <span class="online-dot"></span
          >{{
            connection === "connected"
              ? "已连接本地后端"
              : connection === "error"
                ? "连接异常"
                : "正在连接"
          }}<span class="environment-label">DEMO</span>
        </div>
      </header>
      <div class="page-heading">
        <div>
          <div class="eyebrow">CARE PLAN STUDIO</div>
          <h1>健康方案工作台<span class="version-tag">演示版</span></h1>
          <p>从患者资料出发，生成、核对并完善本周期方案。</p>
        </div>
        <button
          class="button secondary"
          :disabled="!patient || locked"
          @click="newDraft"
        >
          <Icon name="plus" :size="17" />新建方案
        </button>
      </div>
      <div class="stepper" aria-label="方案流程">
        <div
          class="step"
          :class="{
            current: !activeJob && !hasPlan,
            complete: hasPlan || activeJob,
          }"
        >
          <span>01</span>
          <div>选择患者<small>核对六方面资料</small></div>
        </div>
        <Icon name="chevron" :size="16" />
        <div
          class="step"
          :class="{ current: activeJob || !!candidate, complete: editing }"
        >
          <span>02</span>
          <div>生成候选<small>七维按需纳入</small></div>
        </div>
        <Icon name="chevron" :size="16" />
        <div class="step" :class="{ current: editing }">
          <span>03</span>
          <div>完善草稿<small>采纳后编辑保存</small></div>
        </div>
        <span class="stepper-note"
          ><Icon name="check" :size="15" />保留依据与生成记录</span
        >
      </div>

      <div v-if="notice" class="notice" :class="notice.kind" role="status">
        <Icon :name="notice.kind === 'error' ? 'info' : 'check'" :size="19" />
        <div>
          {{ notice.text
          }}<small v-if="notice.requestId"
            >请求编号 {{ notice.requestId }}</small
          >
        </div>
        <button
          class="icon-button"
          aria-label="关闭提示"
          @click="notice = null"
        >
          <Icon name="close" :size="16" />
        </button>
      </div>

      <div class="workbench">
        <aside class="patient-pane">
          <div class="section-heading">
            <h2>患者列表</h2>
            <span class="count">{{ total }}</span
            ><button
              class="icon-button"
              :disabled="listLoading"
              aria-label="刷新患者列表"
              @click="loadPatients"
            >
              <Icon name="refresh" :size="16" />
            </button>
          </div>
          <label class="search-box"
            ><Icon name="search" :size="17" /><input
              v-model="query"
              maxlength="100"
              placeholder="搜索患者姓名"
              aria-label="搜索患者姓名"
              @input="search"
          /></label>
          <div class="list-caption">已授权的虚构资料</div>
          <div v-if="listLoading" class="list-placeholder">正在读取患者…</div>
          <div v-else-if="!patients.length" class="list-placeholder">
            <Icon name="people" :size="26" />
            <p>
              {{
                connection === "error" ? "暂时无法读取患者" : "没有匹配的患者"
              }}
            </p>
            <button class="text-button" @click="loadPatients">重新读取</button>
          </div>
          <div v-else class="patient-list">
            <button
              v-for="p in patients"
              :key="p.id"
              class="patient-row"
              :class="{ selected: patient?.id === p.id }"
              :disabled="locked"
              @click="choosePatient(p)"
            >
              <span class="avatar">{{ shortName(p).slice(0, 1) }}</span
              ><span class="patient-text"
                ><strong>{{ shortName(p) }}</strong
                ><small
                  >模拟病例 ·
                  {{
                    p.sex === "UNKNOWN"
                      ? "性别未记录"
                      : p.sex === "MALE"
                        ? "男"
                        : "女"
                  }}</small
                ></span
              ><Icon v-if="patient?.id === p.id" name="chevron" :size="15" />
            </button>
          </div>
          <div v-if="total > 20" class="pagination">
            <button
              class="text-button"
              :disabled="page === 1 || listLoading"
              @click="
                page--;
                loadPatients();
              "
            >
              上一页</button
            ><span>{{ page }} / {{ Math.ceil(total / 20) }}</span
            ><button
              class="text-button"
              :disabled="page * 20 >= total || listLoading"
              @click="
                page++;
                loadPatients();
              "
            >
              下一页
            </button>
          </div>
          <div class="patient-footer">
            <Icon name="link" :size="14" />资料来自本机后端
          </div>
        </aside>

        <main class="main-pane">
          <div v-if="loadingProfile" class="loading-surface">
            <div class="spinner"></div>
            <h3>正在读取患者资料</h3>
            <p>正在核对当前资料和本地方案记录</p>
          </div>
          <div v-else-if="!summary" class="empty-surface">
            <Icon name="people" :size="42" />
            <h3>先选择一位患者</h3>
            <p>六方面资料、生成的建议与依据会显示在这里。</p>
            <button
              v-if="patient"
              class="button secondary"
              @click="choosePatient(patient)"
            >
              重新读取资料
            </button>
          </div>
          <template v-else>
            <div class="patient-header">
              <span class="avatar large">{{
                shortName(patient).slice(0, 1)
              }}</span>
              <div>
                <h2>
                  {{ shortName(patient) }}<span class="soft-tag">虚构资料</span>
                </h2>
                <p>
                  {{ summary.facts.length }} 条事实记录 <span>·</span> 资料版本
                  {{ summary.profileRevision }} <span>·</span>
                  {{ 6 - summary.missingFields.length }}/6 方面有记录
                </p>
              </div>
              <button
                class="icon-button"
                title="打开已有方案"
                aria-label="打开已有方案"
                :disabled="locked"
                @click="showOpen = true"
              >
                <Icon name="file" />
              </button>
            </div>
            <div class="workspace-tabs">
              <button
                :class="{ active: tab === 'profile' }"
                @click="tab = 'profile'"
              >
                患者资料<span>{{ summary.facts.length }}</span></button
              ><button
                :class="{ active: tab === 'plan' }"
                @click="tab = 'plan'"
              >
                方案建议<span v-if="hasPlan">{{ selectedCount }}</span>
              </button>
              <div v-if="draft" class="draft-marker">
                <span :class="['dot', dirty ? 'amber' : '']"></span
                >{{
                  dirty
                    ? "有未保存修改"
                    : draft.status === "DRAFT"
                      ? "草稿"
                      : "已进入审核流程"
                }}
              </div>
            </div>

            <section v-if="tab === 'profile'" class="profile-content fade-in">
              <div class="section-intro">
                <h3>六方面健康资料</h3>
                <p>生成将读取最新资料；缺失信息会保留为未知。</p>
              </div>
              <div v-if="summary.conflicts.length" class="inline-warning">
                有 {{ summary.conflicts.length }} 组资料冲突，生成后请重点核对。
              </div>
              <div class="axis-list">
                <article v-for="axis in axes" :key="axis.code" class="axis-row">
                  <div class="axis-symbol">
                    <Icon :name="axis.icon" :size="19" />
                  </div>
                  <div class="axis-content">
                    <div class="axis-title">
                      <h4>{{ axis.name }}</h4>
                      <span class="axis-code">{{ axis.code }}</span
                      ><span
                        v-if="summary.axes[axis.code]?.status === 'UNKNOWN'"
                        class="muted-label"
                        >资料待补充</span
                      >
                    </div>
                    <div
                      v-if="
                        !summary.facts.some((f) => f.axisCode === axis.code)
                      "
                      class="unknown-line"
                    >
                      暂无相关记录，生成时不会自动补充为事实。
                    </div>
                    <div
                      v-for="fact in summary.facts.filter(
                        (f) => f.axisCode === axis.code,
                      )"
                      :key="fact.id"
                      class="fact"
                    >
                      <p>
                        {{ factText(fact.value)
                        }}<span v-if="fact.unit"> {{ fact.unit }}</span>
                      </p>
                      <span
                        class="fact-source"
                        :class="{
                          confirmed:
                            fact.confirmationStatus ===
                            'CONFIRMED_PROFESSIONAL',
                        }"
                        >{{
                          confirmationLabels[fact.confirmationStatus] ||
                          fact.confirmationStatus
                        }}</span
                      ><span v-if="fact.observedAt" class="fact-time">{{
                        formatDate(fact.observedAt)
                      }}</span>
                    </div>
                  </div>
                </article>
              </div>
            </section>

            <section v-else class="plan-content fade-in">
              <div v-if="historyPreview" class="history-preview-bar">
                <span>历史生成结果 · 只读</span
                ><button class="text-button" @click="returnToDraft">
                  返回当前草稿
                </button>
              </div>
              <div v-if="activeJob" class="generating-surface">
                <div class="generation-orbit">
                  <Icon name="spark" :size="32" />
                </div>
                <span class="soft-tag">{{ jobLabels[job.status] }}</span>
                <h3>正在整理本周期方案</h3>
                <p>
                  结合患者资料，选择管理模块与七维建议。<br />完成后将在这里展示，请稍候。
                </p>
                <div class="generation-stages">
                  <span><Icon name="check" :size="15" />患者输入已冻结</span
                  ><span
                    ><span class="spinner small-spinner"></span
                    >{{
                      job.status === "PENDING"
                        ? "等待生成队列"
                        : "AI 正在生成候选"
                    }}</span
                  ><span class="muted">结构与来源校验</span>
                </div>
                <button class="text-button" :disabled="polling" @click="poll">
                  重新查询进度
                </button>
              </div>
              <template v-else-if="hasPlan">
                <div class="plan-title-row">
                  <div>
                    <span class="eyebrow">{{
                      candidate ? "AI CANDIDATE" : "EDITABLE DRAFT"
                    }}</span>
                    <h3>
                      {{ candidate ? "本周期方案候选" : "本周期方案草稿" }}
                    </h3>
                  </div>
                  <span class="soft-tag" :class="{ amber: pendingCount }">{{
                    pendingCount ? pendingCount + " 维待确认" : "待人工审核"
                  }}</span>
                </div>
                <div class="goal-block">
                  <span class="goal-icon"
                    ><Icon name="target" :size="22"
                  /></span>
                  <div>
                    <label for="primary-goal">本周期主目标</label
                    ><textarea
                      v-if="editing"
                      id="primary-goal"
                      v-model="edit.primaryGoal"
                      class="goal-input"
                      rows="2"
                      maxlength="1000"
                      :disabled="!!busy"
                    ></textarea>
                    <p v-else>{{ content.primaryGoal }}</p>
                  </div>
                </div>
                <div class="module-line">
                  <span>管理模块</span
                  ><span
                    v-for="m in content.modules || draft?.modules || []"
                    :key="m.moduleCode"
                    class="module-label"
                    :title="m.selectionReason"
                    >{{ moduleNames[m.moduleCode] || m.moduleCode }}</span
                  >
                </div>
                <div class="dimension-filter" aria-label="七维筛选">
                  <button
                    :class="{ active: dimension === 'all' }"
                    @click="dimension = 'all'"
                  >
                    全部 <span>{{ selectedCount }}</span></button
                  ><button
                    v-for="d in dimensions"
                    :key="d.code"
                    :class="{ active: dimension === d.code }"
                    @click="dimension = d.code"
                  >
                    <Icon :name="d.icon" :size="14" />{{ d.label
                    }}<span
                      v-if="dimItems(d.code).filter((i) => i.isSelected).length"
                      >{{
                        dimItems(d.code).filter((i) => i.isSelected).length
                      }}</span
                    >
                  </button>
                </div>
                <div class="recommendations">
                  <article
                    v-for="d in visibleDimensions"
                    :key="d.code"
                    class="dimension-section"
                    :class="{
                      inactive: dimState(d.code)?.decision !== 'ENABLED',
                    }"
                  >
                    <header>
                      <span class="dimension-icon"
                        ><Icon :name="d.icon" :size="19"
                      /></span>
                      <h4>{{ d.label }}</h4>
                      <span
                        class="decision-label"
                        :class="dimState(d.code)?.decision"
                        >{{
                          decisions[dimState(d.code)?.decision] || "未设置"
                        }}</span
                      ><select
                        v-if="editing && dimState(d.code)"
                        v-model="dimState(d.code).decision"
                        :aria-label="d.label + '维度状态'"
                        :disabled="!!busy"
                      >
                        <option value="ENABLED">纳入本期</option>
                        <option value="CLOSED">本期不纳入</option>
                        <option value="PENDING">待确认</option>
                      </select>
                    </header>
                    <textarea
                      v-if="
                        editing &&
                        dimState(d.code) &&
                        dimState(d.code).decision !== 'ENABLED'
                      "
                      v-model="dimState(d.code).decisionReason"
                      class="reason-input"
                      rows="2"
                      :aria-label="d.label + '维度原因'"
                      maxlength="20000"
                      :disabled="!!busy"
                    ></textarea>
                    <p
                      v-else-if="dimState(d.code)?.decisionReason"
                      class="dimension-reason"
                    >
                      {{ dimState(d.code).decisionReason }}
                    </p>
                    <div
                      v-for="(item, index) in dimItems(d.code)"
                      :key="item.id || d.code + index"
                      class="advice"
                      :class="{ unselected: !item.isSelected }"
                    >
                      <div class="advice-heading">
                        <span class="advice-index">{{
                          String(index + 1).padStart(2, "0")
                        }}</span
                        ><input
                          v-if="editing"
                          v-model="item.title"
                          :aria-label="d.label + '建议标题 ' + (index + 1)"
                          class="advice-title-input"
                          maxlength="200"
                          :disabled="!!busy"
                        />
                        <h5 v-else>{{ item.title }}</h5>
                        <label v-if="editing" class="include-toggle"
                          ><input
                            type="checkbox"
                            v-model="item.isSelected"
                            :disabled="!!busy"
                          />纳入</label
                        >
                      </div>
                      <textarea
                        v-if="editing"
                        v-model="item.content"
                        :aria-label="d.label + '建议内容 ' + (index + 1)"
                        class="advice-text-input"
                        rows="4"
                        maxlength="20000"
                        :disabled="!!busy"
                      ></textarea>
                      <p v-else class="advice-text">{{ item.content }}</p>
                      <div class="advice-footer">
                        <span
                          ><Icon name="people" :size="14" />{{
                            actors[item.actorType]
                          }}</span
                        ><label v-if="editing" class="include-toggle"
                          ><input
                            type="checkbox"
                            v-model="item.executionGoal"
                            :disabled="!!busy"
                          />后续派生任务</label
                        ><span v-else-if="item.executionGoal">形成执行目标</span
                        ><button class="text-button" @click="basis = item">
                          <Icon name="link" :size="14" />{{
                            item.basis?.length || 0
                          }}
                          条依据
                        </button>
                      </div>
                    </div>
                  </article>
                </div>
                <div v-if="candidate" class="candidate-footer">
                  <Icon name="info" :size="17" /><span>{{
                    historyPreview
                      ? "此处保留 AI 原始候选，不包含之后的人工修改。"
                      : "先核对候选；采纳为草稿后可编辑。审核与发布在后续流程完成。"
                  }}</span>
                </div>
                <div v-else-if="editing" class="editor-footer">
                  <span>{{ dirty ? "修改尚未保存" : "已保存到本地后端" }}</span
                  ><button
                    class="text-button"
                    :disabled="!!busy"
                    @click="reloadDraft"
                  >
                    重新载入</button
                  ><button
                    class="button primary"
                    :disabled="!dirty || !!busy"
                    @click="save"
                  >
                    <Icon name="save" :size="16" />{{
                      busy === "save" ? "保存中…" : "保存修改"
                    }}
                  </button>
                </div>
              </template>
              <div v-else class="empty-surface">
                <div class="empty-art"><Icon name="spark" :size="34" /></div>
                <h3>
                  {{
                    ["FAILED", "REJECTED"].includes(job?.status)
                      ? "本次生成未形成可用候选"
                      : "准备生成本周期方案"
                  }}
                </h3>
                <p>
                  {{
                    job?.errorCode
                      ? errorLabels[job.errorCode] || job.errorCode
                      : "确认患者资料与周期后，点击右侧“生成候选”。"
                  }}
                </p>
                <button class="text-button" @click="tab = 'profile'">
                  返回患者资料 <Icon name="arrow" :size="14" />
                </button>
              </div>
            </section>
          </template>
        </main>

        <aside class="inspector">
          <div class="inspector-heading">
            <Icon name="spark" :size="19" />
            <h2>方案生成</h2>
            <span class="model-label">DeepSeek</span>
          </div>
          <form class="plan-form" @submit.prevent="generate">
            <label
              >方案名称<input
                v-model="form.title"
                maxlength="200"
                placeholder="先选择患者"
                :disabled="!summary || !!draft || locked"
            /></label>
            <div class="date-fields">
              <label
                >开始日期<input
                  type="date"
                  v-model="form.startDate"
                  :disabled="!summary || !!draft || locked" /></label
              ><label
                >结束日期<input
                  type="date"
                  v-model="form.endDate"
                  :min="form.startDate"
                  :disabled="!summary || !!draft || locked"
              /></label>
            </div>
            <label
              >补充要求<span class="optional">选填</span
              ><textarea
                v-model="form.requirements"
                rows="4"
                maxlength="20000"
                placeholder="例如：先以记录作息为主，建议尽量简单，便于本人执行。"
                :disabled="
                  !summary ||
                  locked ||
                  (draft?.status && draft.status !== 'DRAFT')
                "
              ></textarea>
            </label>
            <button
              v-if="intent"
              type="button"
              class="button primary full"
              :disabled="!!busy"
              @click="recoverSubmission"
            >
              <Icon name="refresh" :size="17" />继续查询此次生成</button
            ><button
              v-else
              type="submit"
              class="button primary full"
              :disabled="
                !summary ||
                locked ||
                dirty ||
                historyPreview ||
                (draft?.status && draft.status !== 'DRAFT')
              "
            >
              <Icon
                :name="busy || activeJob ? 'clock' : 'spark'"
                :size="18"
              />{{
                activeJob
                  ? jobLabels[job.status]
                  : busy === "generate"
                    ? "正在提交…"
                    : job
                      ? "生成新的候选"
                      : "生成候选"
              }}
            </button>
            <p class="form-help">点击后调用 AI，结果先作为待核对候选。</p>
          </form>
          <div v-if="job" class="generation-result">
            <div class="result-label">
              <span
                class="dot"
                :class="{ amber: ['FAILED', 'REJECTED'].includes(job.status) }"
              ></span
              >{{ jobLabels[job.status]
              }}<small>{{ formatDate(job.createdAt) }}</small>
            </div>
            <div v-if="hasPlan" class="plan-stats">
              <div>
                <strong>{{ enabledCount }}<small>/ 7</small></strong
                ><span>维度纳入</span>
              </div>
              <div>
                <strong>{{ selectedCount }}</strong
                ><span>建议条目</span>
              </div>
              <div>
                <strong>{{ pendingCount }}</strong
                ><span>维度待确认</span>
              </div>
            </div>
            <button
              v-if="candidate && !historyPreview"
              class="button primary full"
              :disabled="!canAdopt || !!busy"
              @click="adopt"
            >
              <Icon name="check" :size="17" />{{
                busy === "adopt" ? "采纳中…" : "采纳为草稿并编辑"
              }}
            </button>
            <p
              v-if="candidate && !historyPreview && !canAdopt"
              class="inline-warning"
            >
              草稿已变化，此候选不可覆盖当前版本。请重新载入后生成。
            </p>
            <button
              v-if="activeJob"
              class="text-button"
              :disabled="polling"
              @click="poll"
            >
              重新查询进度
            </button>
          </div>
          <div
            v-if="job?.issues?.missingInformation?.length"
            class="inspector-section"
          >
            <h3>
              <span class="amber-dot"></span>待补充信息<span>{{
                job.issues.missingInformation.length
              }}</span>
            </h3>
            <ol>
              <li v-for="q in job.issues.missingInformation" :key="q">
                {{ q }}
              </li>
            </ol>
          </div>
          <div v-if="job?.issues?.warnings?.length" class="inspector-section">
            <h3><Icon name="info" :size="15" />核对提醒</h3>
            <ul>
              <li v-for="w in job.issues.warnings" :key="w">{{ w }}</li>
            </ul>
          </div>
          <div
            v-if="job?.issues?.validationErrors?.length"
            class="inspector-section"
          >
            <h3>校验未通过</h3>
            <ul>
              <li v-for="e in job.issues.validationErrors" :key="e">{{ e }}</li>
            </ul>
          </div>
          <div class="inspector-section history-summary">
            <h3><Icon name="clock" :size="16" />生成记录</h3>
            <template v-if="history.length"
              ><button class="history-link" @click="historyOpen = true">
                <span>本方案 {{ history.length }} 条记录</span
                ><Icon name="chevron" :size="15" />
              </button>
              <p>
                每次生成保留输入快照与候选，<br />可回看之前的结果。
              </p></template
            >
            <p v-else>生成后可查看历史候选与状态。</p>
            <div v-if="draft" class="version-info">
              <span
                >方案版本 V{{ draft.versionNo }} · 修订
                {{ draft.rowVersion }}</span
              ><span>更新于 {{ formatDate(draft.updatedAt) }}</span>
            </div>
          </div>
        </aside>
      </div>
      <footer class="page-footer">
        <span>本地方案演示 · 所有病例均为虚构</span
        ><span>当前页面覆盖资料 → AI 候选 → 草稿编辑</span>
      </footer>
    </div>

    <div
      v-if="basis || historyOpen || showOpen"
      class="modal-backdrop"
      @click.self="
        basis = null;
        historyOpen = false;
        showOpen = false;
      "
    >
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="
          basis ? '建议依据' : historyOpen ? '生成记录' : '打开已有方案'
        "
      >
        <header>
          <h2>
            {{ basis ? "建议依据" : historyOpen ? "生成记录" : "打开已有方案" }}
          </h2>
          <button
            class="icon-button"
            aria-label="关闭弹窗"
            @click="
              basis = null;
              historyOpen = false;
              showOpen = false;
            "
          >
            <Icon name="close" />
          </button>
        </header>
        <template v-if="basis"
          ><h3>{{ basis.title }}</h3>
          <div v-for="(b, i) in basis.basis" :key="i" class="basis-row">
            <span class="module-label">{{ moduleNames[b.moduleCode] }}</span>
            <p>
              {{
                basisFact(b.factId)
                  ? factText(basisFact(b.factId).value)
                  : "当前页面未载入这条依据对应的输入快照，请以该次生成记录为准。"
              }}
            </p>
            <small>{{ b.note || "关联本次方案输入事实" }}</small
            ><code>{{ b.factId }}</code>
          </div></template
        >
        <template v-else-if="historyOpen"
          ><button
            v-for="h in history"
            :key="h.id"
            class="history-row"
            :disabled="locked"
            @click="viewHistory(h)"
          >
            <div>
              <strong>{{ jobLabels[h.status] }}</strong
              ><small>{{ formatDate(h.createdAt) }} · {{ h.model }}</small>
            </div>
            <Icon name="chevron" :size="17" /></button
        ></template>
        <form v-else @submit.prevent="openExisting">
          <p>填入当前患者的方案版本 ID，继续查看或编辑。</p>
          <label
            >方案版本 ID<input
              v-model="openVersion"
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              required /></label
          ><button class="button primary full" :disabled="!!busy">
            打开方案
          </button>
        </form>
      </section>
    </div>
  </div>
</template>

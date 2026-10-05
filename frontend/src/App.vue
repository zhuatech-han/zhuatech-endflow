<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  ShieldCheck,
  LogOut,
  Plus,
  RefreshCw,
  X,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  decisions,
  commands,
  labels,
  errors,
  actions,
  date,
} from "./schema.js";
const me = ref(null),
  login = ref({ username: "", password: "" }),
  page = ref("workbench"),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  notice = ref(""),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  dialog = ref(null),
  options = ref({
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  }),
  roles = ref([]),
  permissions = ref([]),
  stats = ref({ states: {}, total: 0, overdue: 0, committed: 0 }),
  work = ref([]);
const can = (p) => me.value?.permissions.includes(p),
  record = computed(() => detail.value?.record),
  title = computed(
    () =>
      me.value?.menus.find((m) => m.code === page.value)?.name || "停产工作台",
  ),
  department = (id) =>
    options.value.departments.find((d) => d.id === id)?.name || "#" + id,
  person = (id) =>
    options.value.accounts.find((a) => a.id === id)?.displayName || "#" + id;
const f = (key, type = "text", extra = {}) => ({ key, type, ...extra }),
  choices = (list, label = "name", value = "id") =>
    list.map((a) => ({ label: a[label], value: a[value] })),
  candidates = (p) =>
    options.value.accounts.filter((a) => a.permissions.includes(p)),
  clone = (v) => JSON.parse(JSON.stringify(v));
const editable = computed(
  () =>
    record.value?.status === "DRAFT" &&
    record.value.authorId === me.value?.id &&
    can("case.write"),
);
const fieldLabel = (key, kind = page.value) =>
  key === "scope" && kind === "roles" ? "数据范围" : labels[key] || key;
const filtered = computed(() =>
    rows.value.filter((r) =>
      JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()),
    ),
  ),
  visibleRows = computed(() =>
    page.value === "cases"
      ? rows.value
      : filtered.value.slice(offset.value * 20, offset.value * 20 + 20),
  ),
  pageTotal = computed(() =>
    page.value === "cases" ? total.value : filtered.value.length,
  );
const fields = {
  cases: () => [
    f("code", "text", { readonly: !!dialog.value?.id, max: 60 }),
    f("title", "text", { max: 160 }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
      readonly: !!dialog.value?.id,
    }),
    f("category", "select", {
      options: choices(
        options.value.dictionaries.filter((d) => d.type === "eol"),
        "name",
        "code",
      ),
    }),
    f("manufacturer", "text", { max: 160 }),
    f("partNumber", "text", { max: 160 }),
    f("sourceRef", "text", { max: 300 }),
    f("buyerId", "select", {
      options: choices(candidates("case.fulfill"), "displayName"),
    }),
    f("reviewerId", "select", {
      options: choices(
        candidates("case.review").filter(
          (a) => a.id !== me.value.id && a.id !== dialog.value?.values.buyerId,
        ),
        "displayName",
      ),
    }),
    f("noticeDate", "date"),
    f("lastBuyDate", "date"),
    f("lastShipDate", "date"),
    f("decision", "select", {
      options: Object.entries(decisions).map(([value, label]) => ({
        value,
        label,
      })),
    }),
    f("packSize", "number", { min: 1, max: 1000000 }),
    f("minimumOrder", "number", { min: 0, max: 1000000000 }),
    f("decisionQty", "number", { min: 0, max: 1000000000 }),
    f("unitPrice", "number", { min: 0, max: 1000000, step: "0.0001" }),
    f("budget", "number", { min: 0, max: 1000000000000, step: "0.01" }),
    f("alternativePart", "text", { required: false, max: 160 }),
    f("validationRef", "text", { required: false, max: 300 }),
    f("rationale", "textarea", { minLength: 10 }),
  ],
  lines: () => [
    f("productCode", "text", { max: 100 }),
    f("description", "text", { max: 300 }),
    f("monthlyDemand", "number", { min: 0, max: 1000000 }),
    f("months", "number", { min: 1, max: 120 }),
    f("serviceReserve", "number", { min: 0, max: 100000000 }),
    f("allocatedStock", "number", { min: 0, max: 100000000 }),
    f("confirmedInbound", "number", { min: 0, max: 100000000 }),
    f("evidence", "textarea", { minLength: 10 }),
  ],
  users: () => [
    f("username"),
    f("displayName"),
    f("password", "password", { required: !dialog.value?.id }),
    f("roleId", "select", { options: choices(roles.value) }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
    }),
    f("enabled", "checkbox"),
  ],
  roles: () => [
    f("name"),
    f("scope", "select", {
      options: [
        { value: "ALL", label: "全部部门" },
        { value: "DEPARTMENT", label: "本部门" },
        { value: "SELF", label: "本人相关" },
      ],
    }),
    f("permissions", "permissions", {
      options: choices(permissions.value, "name", "code"),
    }),
  ],
  departments: () => [f("name")],
  menus: () => [
    f("code", "text", { readonly: true }),
    f("name"),
    f("nameEn"),
    f("permissionCode", "select", {
      options: choices(permissions.value, "name", "code"),
    }),
    f("position", "number"),
    f("enabled", "checkbox"),
  ],
  permissions: () => [f("code", "text", { readonly: true }), f("name")],
  dictionaries: () => [f("type"), f("code"), f("name"), f("nameEn")],
  settings: () => [f("code", "text", { readonly: true }), f("value")],
  password: () => [f("oldPassword", "password"), f("newPassword", "password")],
};
const dialogFields = computed(() => {
  const d = dialog.value;
  if (!d) return [];
  if (!d.command) return fields[d.kind]?.() || [];
  const fs = [f("note", "textarea", { minLength: 10 })];
  if (["order", "receive", "implement"].includes(d.command))
    fs.unshift(f("reference", "text", { max: 160 }));
  if (["order", "receive"].includes(d.command)) fs.unshift(f("date", "date"));
  if (d.command === "order") fs.unshift(f("promisedDate", "date"));
  if (d.command === "receive")
    fs.unshift(
      f("quantity", "number", {
        min: 1,
        max: record.value.decisionQty - detail.value.received,
      }),
    );
  return fs;
});
const adminColumns = computed(() =>
  page.value === "audit"
    ? ["actor", "action", "objectId", "createdAt"]
    : (fields[page.value]?.() || [])
        .map((f) => f.key)
        .filter((k) => k !== "password"),
);
function clear() {
  me.value = null;
  detail.value = null;
  dialog.value = null;
  rows.value = [];
  roles.value = [];
  permissions.value = [];
  options.value = {
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  };
  work.value = [];
  page.value = "workbench";
}
function fail(e) {
  error.value = errors[e.message] || "操作未完成，请核对输入后重试";
  if (e.message === "UNAUTHENTICATED") clear();
}
/** 重新加载服务端状态与实时权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    me.value = await api("/auth/me");
    options.value = await api("/options");
    if (can("admin") && me.value.scope === "ALL") {
      roles.value = await api("/admin/roles");
      permissions.value = await api("/admin/permissions");
    }
    if (!me.value.menus.some((m) => m.code === page.value))
      page.value = me.value.menus[0]?.code || "workbench";
    if (detail.value) detail.value = await api("/cases/" + record.value.id);
    else if (page.value === "cases") {
      const v = await api(
        "/cases?" +
          new URLSearchParams({
            search: search.value,
            status: status.value,
            page: offset.value,
            size: 20,
            sort: sort.value,
          }),
      );
      rows.value = v.items;
      total.value = v.total;
    } else if (page.value === "workbench") work.value = await api("/workbench");
    else if (page.value === "dashboard") stats.value = await api("/dashboard");
    else if (page.value === "about") return;
    else
      rows.value = await api(
        page.value === "audit" ? "/audit" : "/admin/" + page.value,
      );
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function signIn() {
  saving.value = true;
  error.value = "";
  try {
    resetCsrf();
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function signOut() {
  saving.value = true;
  try {
    await api("/auth/logout", "POST");
    clear();
    resetCsrf();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function navigate(code) {
  if (loading.value || saving.value) return;
  page.value = code;
  detail.value = null;
  offset.value = 0;
  search.value = "";
  status.value = "";
  notice.value = "";
  await load();
}
async function show(id) {
  if (loading.value || saving.value) return;
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    detail.value = await api("/cases/" + id);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function back() {
  detail.value = null;
  await load();
}
function edit(kind, row) {
  const today = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Shanghai",
  }).format(new Date());
  dialog.value = {
    kind,
    id: row?.id,
    title:
      (row ? "编辑" : "新建") +
      (kind === "cases"
        ? "停产通知"
        : kind === "lines"
          ? "产品需求"
          : title.value),
    requestKey: crypto.randomUUID(),
    values: row
      ? clone(row)
      : {
          enabled: true,
          permissions: [],
          scope: "DEPARTMENT",
          departmentId: me.value.departmentId,
          category: "PDN",
          type: "eol",
          noticeDate: today,
          lastBuyDate: today,
          lastShipDate: today,
          decision: "LAST_BUY",
          packSize: 1,
          minimumOrder: 0,
          decisionQty: 0,
          unitPrice: 0,
          budget: 0,
          monthlyDemand: 0,
          months: 12,
          serviceReserve: 0,
          allocatedStock: 0,
          confirmedInbound: 0,
        },
  };
}
function command(action, receipt) {
  dialog.value = {
    kind: "cases",
    command: action,
    receiptId: receipt?.id,
    title: commands[action],
    requestKey: crypto.randomUUID(),
    values: {
      note: "",
      date: new Intl.DateTimeFormat("sv-SE", {
        timeZone: "Asia/Shanghai",
      }).format(new Date()),
      promisedDate: record.value.lastShipDate,
    },
  };
}
function remove(kind, row) {
  dialog.value = {
    kind,
    id: row?.id,
    title:
      "删除" +
      (row?.productCode || row?.title || row?.name || row?.username || "草稿"),
    delete: true,
    values: {},
  };
}
/** 重试使用固定UUID，版本由当前明细读入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function submitDialog() {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  const d = dialog.value;
  try {
    const v = clone(d.values);
    let path,
      method = "POST";
    if (d.kind === "password") {
      await api("/auth/password", "POST", v);
      clear();
      resetCsrf();
      notice.value = "密码已更新，请重新登录";
      return;
    }
    if (d.delete) {
      method = "DELETE";
      path = ["cases", "lines"].includes(d.kind)
        ? "/cases/" +
          record.value.id +
          (d.kind === "lines" ? "/lines/" + d.id : "") +
          "?version=" +
          record.value.version
        : "/admin/" + d.kind + "/" + d.id;
    } else if (d.command) {
      v.version = record.value.version;
      v.requestKey = d.requestKey;
      path =
        "/cases/" +
        record.value.id +
        (d.command === "reverse"
          ? "/receipts/" + d.receiptId + "/reverse"
          : "/commands/" + d.command);
    } else if (d.kind === "cases") {
      path = "/cases" + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
      v.version = d.id ? record.value.version : undefined;
      v.requestKey = d.requestKey;
    } else if (d.kind === "lines") {
      path = "/cases/" + record.value.id + "/lines" + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
      v.version = record.value.version;
      v.requestKey = d.requestKey;
    } else {
      path = "/admin/" + d.kind + (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
    }
    const result = await api(path, method, v);
    dialog.value = null;
    notice.value = "已保存";
    if (d.delete && d.kind === "cases") detail.value = null;
    if (d.kind === "cases" && !d.delete) detail.value = result;
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function exportReport() {
  loading.value = true;
  try {
    const v = await api("/cases/" + record.value.id + "/report.json");
    const u = URL.createObjectURL(
      new Blob([JSON.stringify(v, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = u;
    a.download = "eol-" + record.value.code + ".json";
    a.click();
    URL.revokeObjectURL(u);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function move(n) {
  offset.value += n;
  await load();
}
const display = (r, k) =>
  k === "roleId"
    ? roles.value.find((x) => x.id === r[k])?.name
    : k === "departmentId"
      ? department(r[k])
      : k === "permissions"
        ? r[k]
            ?.map((v) => permissions.value.find((p) => p.code === v)?.name || v)
            .join("、")
        : k === "enabled"
          ? r[k]
            ? "启用"
            : "停用"
          : k === "scope"
            ? { ALL: "全部部门", DEPARTMENT: "本部门", SELF: "本人相关" }[
                r[k]
              ] || r[k]
            : k === "createdAt"
              ? date(r[k])
              : r[k];
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" />
      <p class="eyebrow">ENDFLOW / 0.1.0</p>
      <h1>电子料号停产与<br />末次采购协作</h1>
      <p>通知 · 决策 · 采购 · 追溯</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >知华科技官网 <ArrowUpRight :size="15"
      /></a>
    </section>
    <section class="login-form">
      <p class="eyebrow">COMPONENT LIFECYCLE</p>
      <h2>登录停产工作台</h2>
      <form @submit.prevent="signIn">
        <label
          >账号<input
            v-model="login.username"
            autocomplete="username"
            required /></label
        ><label
          >密码<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" class="success">{{ notice }}</p>
        <button class="primary" :disabled="saving">登录</button>
      </form>
      <p class="subtle">公开源码学习版 · 未经书面授权不得商用</p>
      <small
        >上海如静知华信息科技有限公司<br />商业咨询微信 zhuatech /
        zhuatech2</small
      >
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><strong>EndFlow</strong>
      </div>
      <p class="sidebar-caption">
        {{
          options.settings.find((s) => s.code === "companyName")?.value ||
          "停产料号协作"
        }}
      </p>
      <nav>
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: page === m.code }"
          :disabled="loading || saving"
          @click="navigate(m.code)"
        >
          <ShieldCheck :size="17" />{{ m.name }}
        </button>
      </nav>
      <footer class="sidebar-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技官网 <ArrowUpRight :size="13" /></a
        ><small>公开源码学习版 0.1.0</small>
      </footer>
    </aside>
    <div class="content-shell">
      <header class="topbar">
        <span>{{ department(me.departmentId) }}</span>
        <div>
          <button
            :disabled="loading || saving"
            @click="
              dialog = { kind: 'password', title: '修改密码', values: {} }
            "
          >
            修改密码</button
          ><span>{{ me.displayName }} · {{ me.role }}</span
          ><button
            aria-label="退出登录"
            :disabled="loading || saving"
            @click="signOut"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <p class="eyebrow">COMPONENT LIFECYCLE</p>
            <h1>{{ detail ? "停产通知与决策" : title }}</h1>
          </div>
          <button :disabled="loading || saving" @click="load">
            <RefreshCw :size="16" />刷新
          </button>
        </div>
        <p v-if="loading" class="subtle">正在读取记录…</p>
        <div v-if="error" role="alert" class="error">{{ error }}</div>
        <div v-if="notice" role="status" class="success">{{ notice }}</div>
        <fieldset class="operations" :disabled="loading || saving">
          <template v-if="detail">
            <button @click="back"><ChevronLeft :size="16" />返回列表</button>
            <section class="panel">
              <div class="detail-head">
                <div>
                  <p class="eyebrow">
                    {{ record.code }} / {{ decisions[record.decision] }}
                  </p>
                  <h2>{{ record.title }}</h2>
                  <p>{{ record.manufacturer }} · {{ record.partNumber }}</p>
                </div>
                <span class="badge" :class="record.status">{{
                  states[record.status]
                }}</span>
              </div>
              <p v-if="detail.overdue" class="error">
                末次订购期限已过，请复核处置路线或取消未执行决策。
              </p>
              <div class="toolbar">
                <button v-if="editable" @click="edit('cases', record)">
                  编辑通知与决策</button
                ><button
                  v-if="editable && !record.submitted"
                  @click="remove('cases', record)"
                >
                  删除草稿</button
                ><button
                  v-for="a in actions(detail, me)"
                  :key="a"
                  class="primary"
                  @click="command(a)"
                >
                  {{ commands[a] }}</button
                ><button v-if="can('export')" @click="exportReport">
                  导出业务记录
                </button>
              </div>
              <dl class="facts">
                <div>
                  <dt>通知来源</dt>
                  <dd>{{ record.sourceRef }}</dd>
                </div>
                <div>
                  <dt>通知 / 末次订购 / 末次交货</dt>
                  <dd>
                    {{ date(record.noticeDate) }} /
                    {{ date(record.lastBuyDate) }} /
                    {{ date(record.lastShipDate) }}
                  </dd>
                </div>
                <div>
                  <dt>编制 / 采购 / 独立复核</dt>
                  <dd>
                    {{ person(record.authorId) }} /
                    {{ person(record.buyerId) }} /
                    {{ person(record.reviewerId) }}
                  </dd>
                </div>
                <div>
                  <dt>决策依据</dt>
                  <dd>{{ record.rationale }}</dd>
                </div>
                <div v-if="record.alternativePart">
                  <dt>替代料及验证凭证</dt>
                  <dd>
                    {{ record.alternativePart }} · {{ record.validationRef }}
                  </dd>
                </div>
                <div v-if="record.orderRef">
                  <dt>订单登记</dt>
                  <dd>
                    {{ record.orderRef }} · {{ record.orderDate }} · 承诺
                    {{ record.promisedDate }}
                  </dd>
                </div>
                <div v-if="record.implementationRef">
                  <dt>实施凭证</dt>
                  <dd>{{ record.implementationRef }}</dd>
                </div>
              </dl>
            </section>
            <div class="metrics">
              <div class="metric">
                <span>支持期总需求 / 净缺口（个）</span
                ><strong
                  >{{ detail.calculation.demand }} /
                  {{ detail.calculation.shortage }}</strong
                >
              </div>
              <div class="metric">
                <span>包装取整建议（个）</span
                ><strong>{{ detail.calculation.recommended }}</strong>
              </div>
              <div class="metric">
                <span>申请采购 / 已登记收货（个）</span
                ><strong
                  >{{ record.decisionQty }} / {{ detail.received }}</strong
                >
              </div>
              <div class="metric">
                <span>申请金额 / 预算（元）</span
                ><strong
                  >{{ detail.calculation.decisionCost }} /
                  {{ record.budget }}</strong
                >
              </div>
            </div>
            <section class="panel">
              <div class="detail-head">
                <h3>受影响产品与需求快照</h3>
                <button v-if="editable" @click="edit('lines')">
                  <Plus :size="16" />新增需求
                </button>
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>用途 / 核对依据</th>
                      <th>月需求 × 月数 + 保留</th>
                      <th>独立分配库存 / 在途</th>
                      <th>净缺口（个）</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="l in detail.lines" :key="l.id">
                      <td class="config-cell">
                        <strong
                          >{{ l.productCode }} · {{ l.description }}</strong
                        ><small>{{ l.evidence }}</small>
                      </td>
                      <td>
                        {{ l.monthlyDemand }} × {{ l.months }} +
                        {{ l.serviceReserve }}
                      </td>
                      <td>{{ l.allocatedStock }} / {{ l.confirmedInbound }}</td>
                      <td>
                        {{
                          Math.max(
                            0,
                            l.monthlyDemand * l.months +
                              l.serviceReserve -
                              l.allocatedStock -
                              l.confirmedInbound,
                          )
                        }}
                      </td>
                      <td v-if="editable">
                        <button @click="edit('lines', l)">编辑</button
                        ><button @click="remove('lines', l)">删除</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!detail.lines.length" class="empty">暂无需求快照</p>
              </div>
            </section>
            <section v-if="record.decision === 'LAST_BUY'" class="panel">
              <h3>分批收货登记</h3>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>凭证 / 日期</th>
                      <th>数量（个）</th>
                      <th>收货依据</th>
                      <th>状态</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in detail.receipts" :key="r.id">
                      <td>
                        {{ r.reference
                        }}<small
                          >{{ r.receivedDate }} · {{ person(r.actorId) }}</small
                        >
                      </td>
                      <td>{{ r.quantity }}</td>
                      <td class="config-cell">
                        {{ r.evidence
                        }}<small v-if="r.reversedBy"
                          >冲正：{{ r.reversalNote }}</small
                        >
                      </td>
                      <td>{{ r.reversedBy ? "已冲正" : "有效登记" }}</td>
                      <td>
                        <button
                          v-if="
                            !r.reversedBy &&
                            record.reviewerId === me.id &&
                            can('case.review') &&
                            ['ORDERED', 'RECEIVED'].includes(record.status)
                          "
                          @click="command('reverse', r)"
                        >
                          独立冲正
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!detail.receipts.length" class="empty">暂无收货登记</p>
              </div>
            </section>
            <section class="panel">
              <h3>决策与执行记录</h3>
              <ol class="timeline">
                <li v-for="e in detail.events" :key="e.id">
                  <strong>{{ commands[e.action] || e.action }}</strong
                  ><span
                    >{{ person(e.actorId) }} · {{ date(e.createdAt) }} ·
                    {{ states[e.beforeStatus] }} →
                    {{ states[e.afterStatus] }}</span
                  >
                  <p>{{ e.evidence }}</p>
                </li>
              </ol>
            </section>
          </template>
          <template v-else-if="page === 'workbench'"
            ><section class="panel">
              <h3>我的未结通知</h3>
              <button
                v-for="v in work"
                :key="v.record.id"
                class="task-row"
                @click="show(v.record.id)"
              >
                <span
                  >{{ v.record.code }} · {{ v.record.title
                  }}<small
                    >{{ v.record.partNumber }} ·
                    {{ decisions[v.record.decision] }} · 末次订购
                    {{ v.record.lastBuyDate }}</small
                  ></span
                ><span class="badge" :class="v.record.status">{{
                  states[v.record.status]
                }}</span>
              </button>
              <p v-if="!work.length" class="empty">暂无待处理通知</p>
            </section></template
          >
          <template v-else-if="page === 'dashboard'"
            ><div class="metrics">
              <div class="metric">
                <span>范围内通知</span><strong>{{ stats.total }}</strong>
              </div>
              <div class="metric">
                <span>未下单且订购逾期</span
                ><strong>{{ stats.overdue }}</strong>
              </div>
              <div class="metric">
                <span>已批准采购金额（元）</span
                ><strong>{{ stats.committed }}</strong>
              </div>
              <div class="metric">
                <span>已关闭</span
                ><strong>{{ stats.states.CLOSED || 0 }}</strong>
              </div>
            </div>
            <section class="panel">
              <h3>通知状态分布</h3>
              <div v-for="(n, s) in stats.states" :key="s" class="stat-row">
                <span>{{ states[s] }}</span
                ><strong>{{ n }}</strong>
              </div>
              <p v-if="!stats.total" class="empty">暂无通知</p>
            </section></template
          >
          <template v-else-if="page === 'about'"
            ><section class="panel">
              <h2>EndFlow · 电子料号停产与末次采购协作</h2>
              <p>公开源码学习版 0.1.0 · 非商业源码版</p>
              <p>上海如静知华信息科技有限公司</p>
              <p>
                未经书面授权不得商用。商业授权、定制开发、部署与系统集成咨询微信：zhuatech
                / zhuatech2。
              </p>
              <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
                >知华科技官网</a
              >
              <div class="contact-qr">
                <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" /><img
                  src="/brand/wechat-zhuatech2.png"
                  alt="微信 zhuatech2"
                />
              </div>
              <a href="/third-party/vue.txt" target="_blank">Vue 许可</a> ·
              <a href="/third-party/lucide.txt" target="_blank">Lucide 许可</a>
            </section></template
          >
          <template v-else
            ><section class="panel">
              <form
                class="filters"
                @submit.prevent="
                  offset = 0;
                  load();
                "
              >
                <input
                  v-model="search"
                  aria-label="搜索记录"
                  placeholder="搜索编号、料号或名称"
                /><template v-if="page === 'cases'"
                  ><select v-model="status" aria-label="状态筛选">
                    <option value="">全部状态</option>
                    <option v-for="(label, s) in states" :key="s" :value="s">
                      {{ label }}
                    </option></select
                  ><select v-model="sort" aria-label="排序">
                    <option value="newest">最近创建</option>
                    <option value="deadline">末次订购日期</option>
                    <option value="code">编号顺序</option>
                  </select></template
                ><button>查询</button
                ><button
                  v-if="
                    page === 'cases'
                      ? can('case.write')
                      : can('admin') &&
                        !['audit', 'menus', 'permissions', 'settings'].includes(
                          page,
                        )
                  "
                  class="primary"
                  type="button"
                  @click="edit(page)"
                >
                  <Plus :size="16" />新建
                </button>
              </form>
              <div class="table-wrap">
                <table v-if="page === 'cases'">
                  <thead>
                    <tr>
                      <th>通知 / 料号</th>
                      <th>路线</th>
                      <th>末次订购</th>
                      <th>建议 / 申请（个）</th>
                      <th>状态</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="v in visibleRows" :key="v.record.id">
                      <td>
                        <strong
                          >{{ v.record.code }} · {{ v.record.title }}</strong
                        ><small
                          >{{ v.record.manufacturer }} ·
                          {{ v.record.partNumber }}</small
                        >
                      </td>
                      <td>{{ decisions[v.record.decision] }}</td>
                      <td>
                        {{ v.record.lastBuyDate
                        }}<small v-if="v.overdue">订购逾期</small>
                      </td>
                      <td>
                        {{ v.calculation.recommended }} /
                        {{ v.record.decisionQty }}
                      </td>
                      <td>
                        <span class="badge" :class="v.record.status">{{
                          states[v.record.status]
                        }}</span>
                      </td>
                      <td>
                        <button @click="show(v.record.id)">查看通知</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <table v-else>
                  <thead>
                    <tr>
                      <th v-for="k in adminColumns" :key="k">
                        {{
                          (k === "scope" && page === "roles"
                            ? "数据范围"
                            : labels[k]) ||
                          {
                            actor: "操作人",
                            action: "操作",
                            objectId: "对象",
                            createdAt: "时间",
                          }[k] ||
                          k
                        }}
                      </th>
                      <th v-if="page !== 'audit'">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in visibleRows" :key="r.id">
                      <td
                        v-for="k in adminColumns"
                        :key="k"
                        :class="{
                          'config-cell': [
                            'permissions',
                            'value',
                            'nameEn',
                          ].includes(k),
                        }"
                      >
                        {{ display(r, k) }}
                      </td>
                      <td v-if="page !== 'audit'">
                        <button @click="edit(page, r)">编辑</button
                        ><button
                          v-if="
                            !['menus', 'permissions', 'settings'].includes(page)
                          "
                          @click="remove(page, r)"
                        >
                          删除
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p v-if="!visibleRows.length" class="empty">暂无记录</p>
              </div>
              <div class="pagination">
                <span>共 {{ pageTotal }} 条 · 第 {{ offset + 1 }} 页</span
                ><button
                  aria-label="上一页"
                  :disabled="offset === 0"
                  @click="move(-1)"
                >
                  <ChevronLeft :size="16" /></button
                ><button
                  aria-label="下一页"
                  :disabled="(offset + 1) * 20 >= pageTotal"
                  @click="move(1)"
                >
                  <ChevronRight :size="16" />
                </button>
              </div></section
          ></template>
        </fieldset>
      </main>
    </div>
  </div>
  <div v-if="dialog" class="overlay" @click.self="!saving && (dialog = null)">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="dialog.title"
    >
      <header>
        <h2>{{ dialog.title }}</h2>
        <button aria-label="关闭" @click="dialog = null" :disabled="saving">
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="submitDialog">
        <p v-if="dialog.delete" class="subtle">
          确认删除这条记录？已引用或留有提交历史的记录将被拒绝。
        </p>
        <div class="form-grid">
          <component
            :is="field.type === 'permissions' ? 'div' : 'label'"
            v-for="field in dialog.delete ? [] : dialogFields"
            :key="field.key"
            :class="{
              full: field.type === 'textarea' || field.type === 'permissions',
            }"
            ><span>{{ fieldLabel(field.key, dialog.kind) }}</span
            ><select
              v-if="field.type === 'select'"
              v-model="dialog.values[field.key]"
              :disabled="field.readonly"
              required
            >
              <option :value="undefined">请选择</option>
              <option
                v-for="o in field.options"
                :key="o.value"
                :value="o.value"
              >
                {{ o.label }}
              </option></select
            ><textarea
              v-else-if="field.type === 'textarea'"
              v-model="dialog.values[field.key]"
              :maxlength="field.max || 2000"
              :minlength="field.minLength || 1"
              required
              rows="3"
            ></textarea>
            <div v-else-if="field.type === 'permissions'" class="checks">
              <label v-for="o in field.options" :key="o.value"
                ><input
                  v-model="dialog.values[field.key]"
                  type="checkbox"
                  :value="o.value"
                />{{ o.label }}</label
              >
            </div>
            <input
              v-else-if="field.type === 'checkbox'"
              v-model="dialog.values[field.key]"
              type="checkbox" /><input
              v-else
              v-model="dialog.values[field.key]"
              :type="field.type"
              :min="field.min"
              :step="field.step || 1"
              :max="field.max"
              :maxlength="field.max || 200"
              :readonly="field.readonly"
              :required="field.required !== false"
              :autocomplete="
                field.type === 'password' ? 'new-password' : 'off'
              "
          /></component>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <footer>
          <button type="button" @click="dialog = null" :disabled="saving">
            取消</button
          ><button class="primary" :disabled="saving || loading">
            {{ saving ? "正在保存…" : "确认保存" }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>

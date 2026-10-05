// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: "草稿",
  REVIEW: "待复核",
  APPROVED: "已批准",
  ORDERED: "已下单登记",
  RECEIVED: "已收齐",
  IMPLEMENTED: "已实施",
  CLOSED: "已关闭",
  CANCELLED: "已取消",
};
export const decisions = {
  LAST_BUY: "末次采购",
  ALTERNATE: "替代料切换",
  RETIRE: "产品退役",
};
export const commands = {
  submit: "提交决策",
  approve: "批准冻结",
  return: "退回编制",
  order: "登记末次订单",
  receive: "登记分批收货",
  implement: "登记实际实施",
  close: "独立核验关闭",
  cancel: "取消未执行决策",
  reverse: "冲正收货登记",
  save: "维护通知",
  line: "维护需求快照",
  "delete-line": "删除需求快照",
};
export const labels = {
  code: "通知编号",
  title: "通知名称",
  category: "通知类别",
  manufacturer: "制造商",
  partNumber: "制造商料号",
  sourceRef: "原始通知凭证编号或链接",
  departmentId: "归属部门",
  buyerId: "指定采购执行员",
  reviewerId: "独立复核员",
  noticeDate: "通知日期",
  lastBuyDate: "末次订购截止日",
  lastShipDate: "末次交货截止日",
  decision: "处置路线",
  alternativePart: "候选替代料号",
  validationRef: "替代验证报告编号",
  rationale: "决策依据及超额采购理由",
  packSize: "包装倍数（个）",
  minimumOrder: "最小订购数量（个）",
  decisionQty: "申请采购数量（个）",
  unitPrice: "单价（人民币元）",
  budget: "本通知批准预算（人民币元）",
  productCode: "产品用途编码",
  description: "用途与支持周期说明",
  evidence: "需求及独立分配供给的核对依据",
  monthlyDemand: "月需求（个）",
  months: "支持月数",
  serviceReserve: "售后保留需求（个）",
  allocatedStock: "独立分配库存（个）",
  confirmedInbound: "独立分配在途（个）",
  note: "实际凭证与核对结论",
  reference: "实际凭证编号",
  date: "实际登记日期",
  promisedDate: "供应商承诺交付日",
  quantity: "本批收货数量（个）",
  username: "账号",
  displayName: "显示名称",
  password: "初始或重置密码",
  roleId: "角色",
  enabled: "启用",
  name: "名称",
  nameEn: "英文名称",
  permissions: "业务权限",
  permissionCode: "所需权限",
  position: "排序",
  type: "字典类型",
  value: "参数值",
  oldPassword: "原密码",
  newPassword: "新密码",
};
export const errors = {
  UNAUTHENTICATED: "登录已失效，请重新登录",
  FORBIDDEN: "没有这项操作权限",
  OUT_OF_SCOPE: "记录超出当前数据范围",
  NOT_ASSIGNED: "仅指定人员可操作",
  INDEPENDENCE_REQUIRED: "复核员须与编制员、采购执行员独立",
  STALE_VERSION: "记录已变化，请刷新后重新操作",
  IDEMPOTENCY_CONFLICT: "同一重试标识的内容已改变，请核对后重新操作",
  INVALID_STATE: "当前状态不允许这项操作",
  EVIDENCE_REQUIRED: "请填写至少10字的实际依据",
  INELIGIBLE_ACCOUNT: "人员未启用、权限或部门不符",
  IMMUTABLE_IDENTITY: "编号与归属部门不能更改",
  HISTORY_PROTECTED: "提交历史须保留，不能删除",
  INVALID_REQUEST_KEY: "请求标识无效，请重新打开窗口",
  INVALID_INPUT: "请检查必填内容和长度",
  INVALID_CODE: "编号须为3至60位字母、数字、点、下划线或连字符",
  INVALID_CATEGORY: "请选择有效通知类别",
  INVALID_STATUS: "状态筛选无效",
  WEAK_PASSWORD: "密码至少12位，含大小写字母与数字，最多72字节",
  LAST_ADMIN: "必须保留可用的全范围管理员",
  CONFLICT: "编号重复或资源已有引用",
  BUILTIN_RESOURCE: "内建资源不能删除或更换标识",
  NETWORK_ERROR: "连接未完成，请检查服务后重试",
  DIRECTORY_LIMIT: "管理目录超过10000条上限",
  REPORT_LIMIT: "统计超过1000方案上限",
  LOGIN_FAILED: "账号或密码不正确",
  LOGIN_THROTTLED: "登录失败较多，请稍后重试",
  NOT_FOUND: "记录不存在",
};
Object.assign(errors, {
  INVALID_DATES: "请核对日期；订单只接受今日登记，承诺交付不得超过末次交货日",
  INVALID_DECISION: "处置路线无效",
  NO_LINES: "请先登记至少一项受影响产品需求",
  INSUFFICIENT_BUY: "申请数量不足以覆盖缺口、最小订购量或包装倍数",
  BUDGET_EXCEEDED: "申请采购金额超过通知预算",
  NON_BUY_QUANTITY: "替代或退役路线的采购数量须为零",
  ALTERNATIVE_REQUIRED: "请提供不同的替代料号及验证报告",
  DEADLINE_REACHED: "末次订购期限已过，不能再批准或登记订单",
  OVER_RECEIPT: "本批数量超过尚未登记的订单余量",
  ALREADY_REVERSED: "这笔收货登记已冲正",
  CASE_LIMIT: "已达到通知数量上限",
  LINE_LIMIT: "每份通知最多100条需求",
  RECEIPT_LIMIT: "每份通知最多200笔原始收货登记",
  QUANTITY_LIMIT: "建议采购数量超过十亿个上限",
});
/** 依据真实角色、指派和状态呈现按钮，服务端仍逐项校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(view, me) {
  if (!view || !me) return [];
  const c = view.record,
    p = me.permissions || [],
    out = [];
  if (
    c.authorId === me.id &&
    p.includes("case.write") &&
    c.status === "DRAFT" &&
    (!view.overdue || c.decision !== "LAST_BUY")
  )
    out.push("submit");
  if (c.reviewerId === me.id && p.includes("case.review")) {
    if (c.status === "REVIEW") {
      if (!view.overdue) out.push("approve");
      out.push("return");
    }
    if (["REVIEW", "APPROVED"].includes(c.status)) out.push("cancel");
    if (["RECEIVED", "IMPLEMENTED"].includes(c.status)) out.push("close");
  }
  if (c.buyerId === me.id && p.includes("case.fulfill")) {
    if (c.status === "APPROVED") {
      if (c.decision !== "LAST_BUY") out.push("implement");
      else if (!view.overdue) out.push("order");
    }
    if (c.status === "ORDERED") out.push("receive");
  }
  return out;
}
/** 固定上海时区，日期型字段保持原始业务日。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const date = (v) =>
  !v
    ? "—"
    : v.length === 10
      ? v
      : new Intl.DateTimeFormat("zh-CN", {
          dateStyle: "medium",
          timeStyle: "short",
          timeZone: "Asia/Shanghai",
        }).format(new Date(v));

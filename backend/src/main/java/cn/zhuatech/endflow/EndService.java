// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 停产采购决策、独立审批和真实履约登记事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class EndService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper mapper;
  static final Set<String> STATES =
      Set.of(
          "DRAFT",
          "REVIEW",
          "APPROVED",
          "ORDERED",
          "RECEIVED",
          "IMPLEMENTED",
          "CLOSED",
          "CANCELLED");

  public EndService(Store d, AccessService a, Clock c, ObjectMapper m) {
    db = d;
    access = a;
    clock = c;
    mapper = m;
  }

  /** 有限通知及决策输入，不允许直接写状态或作者。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String requestKey,
      Long version,
      String code,
      String title,
      String category,
      String manufacturer,
      String partNumber,
      String sourceRef,
      Long departmentId,
      Long buyerId,
      Long reviewerId,
      LocalDate noticeDate,
      LocalDate lastBuyDate,
      LocalDate lastShipDate,
      String decision,
      String alternativePart,
      String validationRef,
      String rationale,
      Integer packSize,
      Integer minimumOrder,
      Integer decisionQty,
      BigDecimal unitPrice,
      BigDecimal budget) {}

  /** 用途需求及已独立分配的供给数量快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record LineInput(
      String requestKey,
      Long version,
      String productCode,
      String description,
      String evidence,
      Integer monthlyDemand,
      Integer months,
      Integer serviceReserve,
      Integer allocatedStock,
      Integer confirmedInbound) {}

  /** 状态命令和实际履约凭证，UUID在失败重试时保持一致。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      String requestKey,
      Long version,
      String note,
      String reference,
      LocalDate date,
      LocalDate promisedDate,
      Integer quantity) {}

  /** 返回当前数据范围内的目录，不暴露密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("case.read");
    var accounts =
        db.all(Account.class).stream()
            .filter(a -> a.enabled && access.visible(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList();
    return Map.of(
        "accounts",
        accounts,
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class));
  }

  /** 构造固定范围过滤并检查通知总量上限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private List<EolCase> visibleCases() {
    access.require("case.read");
    var a = access.current();
    var r = access.role();
    String filter =
        r.scope.equals("ALL")
            ? ""
            : r.scope.equals("SELF")
                ? " where e.departmentId=?1 and (e.authorId=?2 or e.buyerId=?2 or e.reviewerId=?2)"
                : " where e.departmentId=?1";
    var q = db.jpql(EolCase.class, "from EolCase e" + filter + " order by e.id desc");
    if (!filter.isEmpty()) q.setParameter(1, a.departmentId);
    if (r.scope.equals("SELF")) q.setParameter(2, a.id);
    var rows = q.setMaxResults(1001).getResultList();
    if (rows.size() > 1000) throw new Problem(409, "REPORT_LIMIT");
    return rows;
  }

  /** 即使知道编号也要求数据范围与本人指派。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private EolCase readable(Long id) {
    access.require("case.read");
    var c = db.get(EolCase.class, id);
    access.department(c.departmentId);
    if (access.role().scope.equals("SELF")
        && !List.of(c.authorId, c.buyerId, c.reviewerId).contains(access.current().id))
      throw new Problem(403, "OUT_OF_SCOPE");
    return c;
  }

  private List<DemandLine> lines(Long id) {
    return db.query(DemandLine.class, "from DemandLine where caseId=?1 order by id", id);
  }

  private List<Receipt> receipts(Long id) {
    return db.query(Receipt.class, "from Receipt where caseId=?1 order by id", id);
  }

  private int received(Long id) {
    return receipts(id).stream().filter(r -> r.reversedBy == null).mapToInt(r -> r.quantity).sum();
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  }

  private Map<String, Object> view(EolCase c) {
    var result = new LinkedHashMap<String, Object>();
    result.put("record", c);
    result.put("calculation", DemandPolicy.calculate(c, lines(c.id)));
    result.put("received", received(c.id));
    result.put(
        "overdue",
        today().isAfter(c.lastBuyDate)
            && Set.of("DRAFT", "REVIEW", "APPROVED").contains(c.status)
            && c.decision.equals("LAST_BUY"));
    return result;
  }

  /** 范围内搜索、排序及分页，当前上限1000通知。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    if (search == null
        || search.length() > 160
        || page < 0
        || page > 1000
        || size < 1
        || size > 100
        || !Set.of("newest", "deadline", "code").contains(sort))
      throw new Problem(400, "INVALID_INPUT");
    if (!status.isEmpty() && !STATES.contains(status)) throw new Problem(400, "INVALID_STATUS");
    String term = search.toLowerCase(Locale.ROOT);
    var rows =
        new ArrayList<>(
            visibleCases().stream()
                .filter(
                    c ->
                        (status.isEmpty() || status.equals(c.status))
                            && (c.code + " " + c.title + " " + c.partNumber + " " + c.manufacturer)
                                .toLowerCase(Locale.ROOT)
                                .contains(term))
                .toList());
    if (sort.equals("deadline"))
      rows.sort(Comparator.comparing((EolCase c) -> c.lastBuyDate).thenComparing(c -> c.id));
    if (sort.equals("code")) rows.sort(Comparator.comparing((EolCase c) -> c.code));
    int start = Math.min(page * size, rows.size());
    return Map.of(
        "items",
        rows.subList(start, Math.min(start + size, rows.size())).stream().map(this::view).toList(),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 通知明细含不可变证据、需求和收货账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var c = readable(id);
    var v = view(c);
    v.put("lines", lines(id));
    v.put("receipts", receipts(id));
    v.put(
        "events", db.query(CaseEvent.class, "from CaseEvent where caseId=?1 order by id desc", id));
    return v;
  }

  /** 本人需要参与的未结通知。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    var id = access.current().id;
    return visibleCases().stream()
        .filter(
            c ->
                !Set.of("CLOSED", "CANCELLED").contains(c.status)
                    && List.of(c.authorId, c.buyerId, c.reviewerId).contains(id))
        .map(this::view)
        .toList();
  }

  /** 实际状态统计及冻结末次采购金额，不将替代路线当采购额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows = visibleCases();
    var states = new TreeMap<String, Long>();
    long overdue = 0;
    BigDecimal committed = BigDecimal.ZERO;
    for (var c : rows) {
      states.merge(c.status, 1L, Long::sum);
      if ((boolean) view(c).get("overdue")) overdue++;
      if (c.decision.equals("LAST_BUY")
          && !Set.of("DRAFT", "REVIEW", "CANCELLED").contains(c.status))
        committed = committed.add(c.unitPrice.multiply(BigDecimal.valueOf(c.decisionQty)));
    }
    return Map.of(
        "total",
        rows.size(),
        "states",
        states,
        "overdue",
        overdue,
        "committed",
        committed.setScale(2, RoundingMode.HALF_UP));
  }

  /** 审计仅供部门范围；SELF仅本人动作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .limit(500)
        .toList();
  }

  /** 导出原始业务记录，不添加广告或账号凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report(Long id) {
    access.require("export");
    return detail(id);
  }

  /** 共享写锁后刷新账号和角色，避免权限撤销竞态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
  }

  /** 计算绑定操作人及完整结构输入的幂等指纹。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private String hash(Object payload) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 相同UUID只接受同一载荷，不同内容拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private Long replay(String key, String fingerprint) {
    try {
      if (key == null || !UUID.fromString(key).toString().equals(key))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "INVALID_REQUEST_KEY");
    }
    var rows = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (rows.isEmpty()) return null;
    if (!rows.getFirst().fingerprint.equals(fingerprint))
      throw new Problem(409, "IDEMPOTENCY_CONFLICT");
    return rows.getFirst().resultId;
  }

  private void remember(String key, String fp, Long id) {
    var r = new CommandRecord();
    r.requestKey = key;
    r.fingerprint = fp;
    r.resultId = id;
    db.save(r);
    db.flush();
  }

  /** 共享锁内拒绝旧版本写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void version(EolCase c, Long version) {
    if (version == null || version != c.version) throw new Problem(409, "STALE_VERSION");
  }

  private void assigned(Long id, String perm) {
    access.require(perm);
    if (!access.current().id.equals(id)) throw new Problem(403, "NOT_ASSIGNED");
  }

  private void draft(EolCase c) {
    assigned(c.authorId, "case.write");
    if (!c.status.equals("DRAFT")) throw new Problem(409, "INVALID_STATE");
  }

  private String text(String s, int n) {
    return AdminService.text(s, n);
  }

  private String optional(String s, int n) {
    return s == null || s.isBlank() ? "" : text(s, n);
  }

  private String evidence(String s) {
    var v = text(s, 2000);
    if (v.length() < 10) throw new Problem(400, "EVIDENCE_REQUIRED");
    return v;
  }

  private int number(Integer n, int min, int max) {
    if (n == null || n < min || n > max) throw new Problem(400, "INVALID_INPUT");
    return n;
  }

  private BigDecimal money(BigDecimal n, int scale, BigDecimal max) {
    if (n == null || n.signum() < 0 || n.scale() > scale || n.compareTo(max) > 0)
      throw new Problem(400, "INVALID_INPUT");
    return n;
  }

  private void candidate(Long id, Long department, String perm) {
    var a = db.get(Account.class, id);
    var r = db.get(AccessRole.class, a.roleId);
    if (!a.enabled || !r.permissions.contains(perm) || !department.equals(a.departmentId))
      throw new Problem(400, "INELIGIBLE_ACCOUNT");
  }

  /** 将业务事实、状态与审计作为同一事务保存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void event(EolCase c, String action, String before, String evidence) {
    var e = new CaseEvent();
    e.caseId = c.id;
    e.actorId = access.current().id;
    e.action = action;
    e.beforeStatus = before;
    e.afterStatus = c.status;
    e.evidence = evidence;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit("EOL_" + action, c.id, c.departmentId);
    c.version++;
  }

  /** 创建或维护本人草稿，已提交记录保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(Long id, Input v) {
    lock();
    access.require("case.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String fp = hash(Arrays.asList(access.current().id, "case", id, v));
    var cached = replay(v.requestKey, fp);
    if (cached != null) return detail(cached);
    var c = id == null ? new EolCase() : readable(id);
    if (id != null) {
      draft(c);
      version(c, v.version);
    } else {
      int cap =
          Integer.parseInt(
              db.query(SystemSetting.class, "from SystemSetting where code=?1", "maxCases")
                  .getFirst()
                  .value);
      if (db.all(EolCase.class).size() >= cap) throw new Problem(409, "CASE_LIMIT");
      c.authorId = access.current().id;
      c.createdAt = clock.instant();
    }
    String code = text(v.code, 60);
    if (!code.matches("[A-Za-z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_CODE");
    if (id != null && (!c.code.equals(code) || !c.departmentId.equals(v.departmentId)))
      throw new Problem(409, "IMMUTABLE_IDENTITY");
    c.code = code;
    c.departmentId = db.get(Department.class, v.departmentId).id;
    access.department(c.departmentId);
    c.title = text(v.title, 160);
    c.category = text(v.category, 60);
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type=?1 and code=?2",
            "eol",
            c.category)
        .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    c.manufacturer = text(v.manufacturer, 160);
    c.partNumber = text(v.partNumber, 160);
    c.sourceRef = text(v.sourceRef, 300);
    c.buyerId = v.buyerId;
    c.reviewerId = v.reviewerId;
    candidate(c.buyerId, c.departmentId, "case.fulfill");
    candidate(c.reviewerId, c.departmentId, "case.review");
    if (c.reviewerId.equals(c.authorId) || c.reviewerId.equals(c.buyerId))
      throw new Problem(400, "INDEPENDENCE_REQUIRED");
    if (v.noticeDate == null
        || v.lastBuyDate == null
        || v.lastShipDate == null
        || v.noticeDate.isAfter(today())
        || v.lastBuyDate.isBefore(v.noticeDate)
        || v.lastShipDate.isBefore(v.lastBuyDate)) throw new Problem(400, "INVALID_DATES");
    c.noticeDate = v.noticeDate;
    c.lastBuyDate = v.lastBuyDate;
    c.lastShipDate = v.lastShipDate;
    if (v.decision == null || !Set.of("LAST_BUY", "ALTERNATE", "RETIRE").contains(v.decision))
      throw new Problem(400, "INVALID_DECISION");
    c.decision = v.decision;
    c.alternativePart = optional(v.alternativePart, 160);
    c.validationRef = optional(v.validationRef, 300);
    c.rationale = evidence(v.rationale);
    c.packSize = number(v.packSize, 1, 1000000);
    c.minimumOrder = number(v.minimumOrder, 0, 1000000000);
    c.decisionQty = number(v.decisionQty, 0, 1000000000);
    c.unitPrice = money(v.unitPrice, 4, new BigDecimal("1000000"));
    c.budget = money(v.budget, 2, new BigDecimal("1000000000000"));
    if (id == null) db.save(c);
    event(c, "save", "DRAFT", "维护停产通知及人工决策草稿");
    remember(v.requestKey, fp, c.id);
    return detail(c.id);
  }

  /** 维护用途快照，冻结之后禁止改变计算基线。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveLine(Long id, Long lineId, LineInput v) {
    lock();
    var c = readable(id);
    draft(c);
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String fp = hash(Arrays.asList(access.current().id, "line", id, lineId, v));
    var cached = replay(v.requestKey, fp);
    if (cached != null) return detail(cached);
    version(c, v.version);
    if (lineId == null && lines(id).size() >= 100) throw new Problem(409, "LINE_LIMIT");
    var l = lineId == null ? new DemandLine() : db.get(DemandLine.class, lineId);
    if (lineId != null && !id.equals(l.caseId)) throw new Problem(404, "NOT_FOUND");
    l.caseId = id;
    l.productCode = text(v.productCode, 100);
    l.description = text(v.description, 300);
    l.evidence = evidence(v.evidence);
    l.monthlyDemand = number(v.monthlyDemand, 0, 1000000);
    l.months = number(v.months, 1, 120);
    l.serviceReserve = number(v.serviceReserve, 0, 100000000);
    l.allocatedStock = number(v.allocatedStock, 0, 100000000);
    l.confirmedInbound = number(v.confirmedInbound, 0, 100000000);
    if (lineId == null) db.save(l);
    db.flush();
    DemandPolicy.calculate(c, lines(id));
    event(c, "line", "DRAFT", l.evidence);
    remember(v.requestKey, fp, id);
    return detail(id);
  }

  /** 删除未提交草稿或未冻结用途，保留提交历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, Long lineId, Long version) {
    lock();
    var c = readable(id);
    draft(c);
    version(c, version);
    if (lineId == null) {
      if (c.submitted) throw new Problem(409, "HISTORY_PROTECTED");
      for (var l : lines(id)) db.delete(l);
      for (var e : db.query(CaseEvent.class, "from CaseEvent where caseId=?1", id)) db.delete(e);
      db.delete(c);
      access.audit("EOL_DELETE", id, c.departmentId);
    } else {
      var l = db.get(DemandLine.class, lineId);
      if (!id.equals(l.caseId)) throw new Problem(404, "NOT_FOUND");
      db.delete(l);
      event(c, "delete-line", "DRAFT", "删除草稿用途快照");
    }
  }

  /** 冻结前重新核对人员、期限、包装、数量和预算。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void decision(EolCase c) {
    if (lines(c.id).isEmpty()) throw new Problem(409, "NO_LINES");
    candidate(c.authorId, c.departmentId, "case.write");
    candidate(c.buyerId, c.departmentId, "case.fulfill");
    candidate(c.reviewerId, c.departmentId, "case.review");
    var calc = DemandPolicy.calculate(c, lines(c.id));
    if (c.decision.equals("LAST_BUY")) {
      if (today().isAfter(c.lastBuyDate)) throw new Problem(409, "DEADLINE_REACHED");
      long suggested = (long) calc.get("recommended");
      if (c.decisionQty < 1
          || c.decisionQty < suggested
          || c.decisionQty < c.minimumOrder
          || c.decisionQty % c.packSize != 0) throw new Problem(409, "INSUFFICIENT_BUY");
      if (((BigDecimal) calc.get("decisionCost")).compareTo(c.budget) > 0)
        throw new Problem(409, "BUDGET_EXCEEDED");
    } else {
      if (c.decisionQty != 0) throw new Problem(400, "NON_BUY_QUANTITY");
      if (c.decision.equals("ALTERNATE")
          && (c.alternativePart.isBlank()
              || c.validationRef.isBlank()
              || c.partNumber.equals(c.alternativePart)))
        throw new Problem(409, "ALTERNATIVE_REQUIRED");
    }
  }

  /** 经过指派、状态、日期、数量和预算校验的命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Command v) {
    lock();
    var c = readable(id);
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var permission =
        switch (action) {
          case "submit" -> "case.write";
          case "approve", "return", "close", "cancel" -> "case.review";
          case "order", "implement", "receive" -> "case.fulfill";
          default -> throw new Problem(400, "INVALID_ACTION");
        };
    access.require(permission);
    String fp = hash(Arrays.asList(access.current().id, id, action, v));
    var cached = replay(v.requestKey, fp);
    if (cached != null) return detail(cached);
    version(c, v.version);
    String note = evidence(v.note), before = c.status;
    switch (action) {
      case "submit" -> {
        draft(c);
        decision(c);
        c.status = "REVIEW";
        c.submitted = true;
      }
      case "approve", "return" -> {
        assigned(c.reviewerId, "case.review");
        if (!c.status.equals("REVIEW")) throw new Problem(409, "INVALID_STATE");
        if (action.equals("approve")) {
          decision(c);
          c.status = "APPROVED";
        } else c.status = "DRAFT";
      }
      case "order" -> {
        assigned(c.buyerId, "case.fulfill");
        if (!c.status.equals("APPROVED") || !c.decision.equals("LAST_BUY"))
          throw new Problem(409, "INVALID_STATE");
        decision(c);
        if (v.date == null
            || v.promisedDate == null
            || !v.date.equals(today())
            || v.promisedDate.isBefore(v.date)
            || v.promisedDate.isAfter(c.lastShipDate)) throw new Problem(400, "INVALID_DATES");
        c.orderRef = text(v.reference, 160);
        c.orderDate = v.date;
        c.promisedDate = v.promisedDate;
        c.status = "ORDERED";
      }
      case "receive" -> {
        assigned(c.buyerId, "case.fulfill");
        if (!c.status.equals("ORDERED")) throw new Problem(409, "INVALID_STATE");
        if (receipts(id).size() >= 200) throw new Problem(409, "RECEIPT_LIMIT");
        int qty = number(v.quantity, 1, 1000000000);
        if ((long) received(id) + qty > c.decisionQty) throw new Problem(409, "OVER_RECEIPT");
        if (v.date == null || v.date.isBefore(c.orderDate) || v.date.isAfter(today()))
          throw new Problem(400, "INVALID_DATES");
        var r = new Receipt();
        r.caseId = id;
        r.actorId = access.current().id;
        r.quantity = qty;
        r.reference = text(v.reference, 160);
        r.evidence = note;
        r.receivedDate = v.date;
        r.createdAt = clock.instant();
        db.save(r);
        db.flush();
        if (received(id) == c.decisionQty) c.status = "RECEIVED";
      }
      case "implement" -> {
        assigned(c.buyerId, "case.fulfill");
        if (!c.status.equals("APPROVED") || c.decision.equals("LAST_BUY"))
          throw new Problem(409, "INVALID_STATE");
        c.implementationRef = text(v.reference, 300);
        c.status = "IMPLEMENTED";
      }
      case "close" -> {
        assigned(c.reviewerId, "case.review");
        if (!Set.of("RECEIVED", "IMPLEMENTED").contains(c.status))
          throw new Problem(409, "INVALID_STATE");
        c.status = "CLOSED";
      }
      case "cancel" -> {
        assigned(c.reviewerId, "case.review");
        if (!Set.of("REVIEW", "APPROVED").contains(c.status))
          throw new Problem(409, "INVALID_STATE");
        c.status = "CANCELLED";
      }
      default -> throw new Problem(400, "INVALID_ACTION");
    }
    event(c, action, before, note);
    remember(v.requestKey, fp, id);
    return detail(id);
  }

  /** 独立复核员冲正错误登记，已关闭记录禁止冲正，不代表物理退货。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object reverse(Long id, Long receiptId, Command v) {
    lock();
    var c = readable(id);
    assigned(c.reviewerId, "case.review");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String fp = hash(Arrays.asList(access.current().id, id, receiptId, "reverse", v));
    var cached = replay(v.requestKey, fp);
    if (cached != null) return detail(cached);
    version(c, v.version);
    if (!Set.of("ORDERED", "RECEIVED").contains(c.status)) throw new Problem(409, "INVALID_STATE");
    var r = db.get(Receipt.class, receiptId);
    if (!id.equals(r.caseId)) throw new Problem(404, "NOT_FOUND");
    if (r.reversedBy != null) throw new Problem(409, "ALREADY_REVERSED");
    r.reversedBy = access.current().id;
    r.reversedAt = clock.instant();
    r.reversalNote = evidence(v.note);
    String before = c.status;
    c.status = "ORDERED";
    event(c, "reverse", before, r.reversalNote);
    remember(v.requestKey, fp, id);
    return detail(id);
  }
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP层完整决策、实际履约、冲正、日期、并发和权限验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(EndIntegrationTest.TimeConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("endflow.admin-password", () -> password);
  }

  /** 可控时钟，上海日期边界显式检验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    volatile Instant value = Instant.parse("2026-10-05T02:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return Clock.fixed(value, z);
    }

    public Instant instant() {
      return value;
    }
  }

  /** 测试时钟注入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, author, buyer, reviewer, other, outside;
  long dep, authorId, buyerId, reviewerId, buyerRole, id;
  String suffix;

  @BeforeAll
  void users() throws Exception {
    admin = login("admin");
    suffix = key().substring(0, 8);
    dep =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST 停产协作" + suffix))
            .path("id")
            .asLong();
    long d2 =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST 另一部门" + suffix))
            .path("id")
            .asLong();
    var r = new HashMap<String, Long>();
    for (var x : ok(admin, "GET", "/admin/roles", null))
      r.put(x.path("name").asString(), x.path("id").asLong());
    authorId = user("author", r.get("需求编制员"), dep);
    buyerRole = r.get("采购执行员");
    buyerId = user("buyer", buyerRole, dep);
    reviewerId = user("reviewer", r.get("独立复核员"), dep);
    user("other", buyerRole, dep);
    user("outside", r.get("需求编制员"), d2);
    author = login("author-" + suffix);
    buyer = login("buyer-" + suffix);
    reviewer = login("reviewer-" + suffix);
    other = login("other-" + suffix);
    outside = login("outside-" + suffix);
  }

  @BeforeEach
  void setup() throws Exception {
    clock.value = Instant.parse("2026-10-05T02:00:00Z");
    id = ok(author, "POST", "/cases", input()).path("record").path("id").asLong();
    addLine();
  }

  Map<String, Object> input() {
    var m = new HashMap<String, Object>();
    m.put("requestKey", key());
    m.put("code", "TEST-" + key().substring(0, 8));
    m.put("title", "TEST 料号停产通知");
    m.put("category", "PDN");
    m.put("manufacturer", "TEST 制造商");
    m.put("partNumber", "TEST-MPN-01");
    m.put("sourceRef", "TEST 停产原始通知凭证");
    m.put("departmentId", dep);
    m.put("buyerId", buyerId);
    m.put("reviewerId", reviewerId);
    m.put("noticeDate", "2026-10-05");
    m.put("lastBuyDate", "2026-10-06");
    m.put("lastShipDate", "2026-11-06");
    m.put("decision", "LAST_BUY");
    m.put("alternativePart", "");
    m.put("validationRef", "");
    m.put("rationale", "TEST 固定需求及独立分配库存的实际核对依据");
    m.put("packSize", 25);
    m.put("minimumOrder", 100);
    m.put("decisionQty", 125);
    m.put("unitPrice", 2);
    m.put("budget", 250);
    return m;
  }

  Map<String, Object> line() throws Exception {
    return new HashMap<>(
        Map.of(
            "requestKey",
            key(),
            "version",
            version(),
            "productCode",
            "TEST-P-01",
            "description",
            "TEST 月度需求及售后支持",
            "evidence",
            "TEST 库存和在途已独立分配不得重复计入",
            "monthlyDemand",
            10,
            "months",
            12,
            "serviceReserve",
            5,
            "allocatedStock",
            20,
            "confirmedInbound",
            0));
  }

  void addLine() throws Exception {
    ok(author, "POST", "/cases/" + id + "/lines", line());
  }

  JsonNode detail() throws Exception {
    return ok(author, "GET", "/cases/" + id, null);
  }

  long version() throws Exception {
    return detail().path("record").path("version").asLong();
  }

  Map<String, Object> cmd() throws Exception {
    return new HashMap<>(
        Map.of("requestKey", key(), "version", version(), "note", "TEST 已对原始凭证及所需数量完成核对"));
  }

  JsonNode action(MockHttpSession who, String action) throws Exception {
    return ok(who, "POST", "/cases/" + id + "/commands/" + action, cmd());
  }

  void approved() throws Exception {
    action(author, "submit");
    action(reviewer, "approve");
  }

  void ordered() throws Exception {
    approved();
    var v = cmd();
    v.put("reference", "TEST-PO-" + key());
    v.put("date", "2026-10-05");
    v.put("promisedDate", "2026-11-01");
    ok(buyer, "POST", "/cases/" + id + "/commands/order", v);
  }

  JsonNode receipt(int qty) throws Exception {
    var v = cmd();
    v.put("reference", "TEST-R-" + key());
    v.put("date", "2026-10-05");
    v.put("quantity", qty);
    return ok(buyer, "POST", "/cases/" + id + "/commands/receive", v);
  }

  @Test
  void partialReceiptsIndependentReversalAndClosure() throws Exception {
    assertEquals(125, detail().path("calculation").path("recommended").asInt());
    ordered();
    receipt(50);
    var d = receipt(75);
    assertEquals("RECEIVED", d.path("record").path("status").asString());
    long r = d.path("receipts").get(0).path("id").asLong();
    expect(buyer, "POST", "/cases/" + id + "/commands/close", cmd(), 403, "FORBIDDEN");
    ok(reviewer, "POST", "/cases/" + id + "/receipts/" + r + "/reverse", cmd());
    assertEquals(75, detail().path("received").asInt());
    receipt(50);
    action(reviewer, "close");
    assertEquals("CLOSED", detail().path("record").path("status").asString());
    expect(
        reviewer,
        "POST",
        "/cases/" + id + "/receipts/" + r + "/reverse",
        cmd(),
        409,
        "INVALID_STATE");
  }

  @Test
  void alternateAndRetireRoutes() throws Exception {
    for (String route : List.of("ALTERNATE", "RETIRE")) {
      var v = input();
      v.put("version", version());
      v.put("code", detail().path("record").path("code").asString());
      v.put("decision", route);
      v.put("decisionQty", 0);
      v.put("alternativePart", "TEST-ALT");
      v.put("validationRef", "TEST 受控替代验证报告");
      ok(author, "PUT", "/cases/" + id, v);
      approved();
      var a = cmd();
      a.put("reference", "TEST 实际工程变更签发编号");
      ok(buyer, "POST", "/cases/" + id + "/commands/implement", a);
      action(reviewer, "close");
      id = ok(author, "POST", "/cases", input()).path("record").path("id").asLong();
      addLine();
    }
  }

  @Test
  void returnProtectsSubmissionHistory() throws Exception {
    action(author, "submit");
    action(reviewer, "return");
    expect(
        author, "DELETE", "/cases/" + id + "?version=" + version(), null, 409, "HISTORY_PROTECTED");
  }

  @Test
  void deletesUnsubmittedDraftAndLines() throws Exception {
    ok(author, "DELETE", "/cases/" + id + "?version=" + version(), null);
    expect(author, "GET", "/cases/" + id, null, 404, "NOT_FOUND");
  }

  @Test
  void blocksUnderBuyAndBudgetOverrun() throws Exception {
    var v = input();
    v.put("code", detail().path("record").path("code").asString());
    v.put("version", version());
    v.put("decisionQty", 100);
    ok(author, "PUT", "/cases/" + id, v);
    expect(author, "POST", "/cases/" + id + "/commands/submit", cmd(), 409, "INSUFFICIENT_BUY");
    v.put("version", version());
    v.put("requestKey", key());
    v.put("decisionQty", 150);
    ok(author, "PUT", "/cases/" + id, v);
    expect(author, "POST", "/cases/" + id + "/commands/submit", cmd(), 409, "BUDGET_EXCEEDED");
  }

  @Test
  void frozenDemandCannotChange() throws Exception {
    approved();
    expect(author, "POST", "/cases/" + id + "/lines", line(), 409, "INVALID_STATE");
  }

  @Test
  void duplicateAndChangedIdempotency() throws Exception {
    ordered();
    var v = cmd();
    v.put("reference", "TEST-EXACT");
    v.put("date", "2026-10-05");
    v.put("quantity", 50);
    String path = "/cases/" + id + "/commands/receive";
    ok(buyer, "POST", path, v);
    ok(buyer, "POST", path, v);
    assertEquals(50, detail().path("received").asInt());
    v.put("quantity", 60);
    expect(buyer, "POST", path, v, 409, "IDEMPOTENCY_CONFLICT");
  }

  @Test
  void parallelDuplicateDoesNotDoubleReceipt() throws Exception {
    ordered();
    var v = cmd();
    v.put("reference", "TEST-PARALLEL");
    v.put("date", "2026-10-05");
    v.put("quantity", 50);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () ->
                  request(buyer, "POST", "/cases/" + id + "/commands/receive", v)
                      .getResponse()
                      .getStatus());
      var two =
          pool.submit(
              () ->
                  request(buyer, "POST", "/cases/" + id + "/commands/receive", v)
                      .getResponse()
                      .getStatus());
      assertEquals(200, one.get());
      assertEquals(200, two.get());
    }
    assertEquals(50, detail().path("received").asInt());
  }

  @Test
  void enforcesScopeAssignmentsAndExport() throws Exception {
    expect(other, "GET", "/cases/" + id, null, 403, "OUT_OF_SCOPE");
    expect(outside, "GET", "/cases/" + id + "/report.json", null, 403, "OUT_OF_SCOPE");
    assertEquals(0, ok(other, "GET", "/cases", null).path("total").asInt());
    assertFalse(ok(author, "GET", "/options", null).toString().contains("passwordHash"));
  }

  @Test
  void noSelfReview() throws Exception {
    var v = input();
    v.put("reviewerId", buyerId);
    expect(author, "POST", "/cases", v, 400, "INELIGIBLE_ACCOUNT");
  }

  @Test
  void dateBoundaryAndExpiredApproval() throws Exception {
    action(author, "submit");
    clock.value = Instant.parse("2026-10-06T16:01:00Z");
    expect(reviewer, "POST", "/cases/" + id + "/commands/approve", cmd(), 409, "DEADLINE_REACHED");
    action(reviewer, "cancel");
  }

  @Test
  void rejectsLatePromiseAndFutureReceipt() throws Exception {
    approved();
    var v = cmd();
    v.put("reference", "TEST-PO");
    v.put("date", "2026-10-05");
    v.put("promisedDate", "2026-12-01");
    expect(buyer, "POST", "/cases/" + id + "/commands/order", v, 400, "INVALID_DATES");
    v.put("promisedDate", "2026-11-01");
    ok(buyer, "POST", "/cases/" + id + "/commands/order", v);
    var r = cmd();
    r.put("reference", "TEST-R");
    r.put("date", "2026-10-06");
    r.put("quantity", 20);
    expect(buyer, "POST", "/cases/" + id + "/commands/receive", r, 400, "INVALID_DATES");
  }

  @Test
  void staleAndExcessReceiptsRejected() throws Exception {
    ordered();
    var stale = cmd();
    receipt(100);
    stale.put("reference", "TEST-STALE");
    stale.put("date", "2026-10-05");
    stale.put("quantity", 20);
    expect(buyer, "POST", "/cases/" + id + "/commands/receive", stale, 409, "STALE_VERSION");
    var v = cmd();
    v.put("reference", "TEST-OVER");
    v.put("date", "2026-10-05");
    v.put("quantity", 30);
    expect(buyer, "POST", "/cases/" + id + "/commands/receive", v, 409, "OVER_RECEIPT");
  }

  @Test
  void anonymousCsrfAndLastAdmin() throws Exception {
    assertEquals(401, mvc.perform(get("/api/cases")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/cases")
                    .session(author)
                    .contentType("application/json")
                    .content(json.writeValueAsString(input())))
            .andReturn()
            .getResponse()
            .getStatus());
    expect(admin, "DELETE", "/admin/users/1", null, 409, "LAST_ADMIN");
  }

  @Test
  void rejectsFractionalCounts() throws Exception {
    var v = line();
    v.put("monthlyDemand", 1.5);
    expect(author, "POST", "/cases/" + id + "/lines", v, 400, "INVALID_INPUT");
  }

  @Test
  void sameAuthorAndReviewerCannotApproveEvenWithBothPermissions() throws Exception {
    var role =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of(
                    "name",
                    "TEST mixed-" + key(),
                    "scope",
                    "DEPARTMENT",
                    "permissions",
                    List.of("case.read", "case.write", "case.review", "case.fulfill")))
            .path("id")
            .asLong();
    var authorRow = ok(admin, "GET", "/admin/users", null);
    JsonNode account = null;
    for (var row : authorRow) if (row.path("id").asLong() == authorId) account = row;
    var original = new HashMap<String, Object>();
    original.put("username", account.path("username").asString());
    original.put("displayName", account.path("displayName").asString());
    original.put("departmentId", dep);
    original.put("roleId", account.path("roleId").asLong());
    original.put("enabled", true);
    var changed = new HashMap<>(original);
    changed.put("roleId", role);
    ok(admin, "PUT", "/admin/users/" + authorId, changed);
    try {
      var v = input();
      v.put("reviewerId", authorId);
      expect(author, "POST", "/cases", v, 400, "INDEPENDENCE_REQUIRED");
    } finally {
      ok(admin, "PUT", "/admin/users/" + authorId, original);
    }
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  long user(String n, long role, long department) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                n + "-" + suffix,
                "displayName",
                "TEST " + n,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                department,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  MockHttpSession login(String name) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession();
  }

  MvcResult request(MockHttpSession s, String m, String p, Object b) throws Exception {
    var builder =
        switch (m) {
          case "POST" -> post("/api" + p);
          case "PUT" -> put("/api" + p);
          case "DELETE" -> delete("/api" + p);
          default -> get("/api" + p);
        };
    builder.session(s).with(csrf());
    if (b != null) builder.contentType("application/json").content(json.writeValueAsString(b));
    return mvc.perform(builder).andReturn();
  }

  JsonNode ok(MockHttpSession s, String m, String p, Object b) throws Exception {
    var r = request(s, m, p, b);
    assertEquals(200, r.getResponse().getStatus(), p + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void expect(MockHttpSession s, String m, String p, Object b, int status, String code)
      throws Exception {
    var r = request(s, m, p, b);
    assertEquals(
        status, r.getResponse().getStatus(), p + " " + r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }
}

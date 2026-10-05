// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库仅创建管理目录、岗位及管理员，不生成通知或采购事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(Store d, BCryptPasswordEncoder e, @Value("${endflow.admin-password}") String p) {
    db = d;
    encoder = e;
    password = p;
  }

  /** 已有库重启不覆盖账号和业务记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var dep = new Department();
    dep.name = "总部";
    db.save(dep);
    var names =
        Map.of(
            "case.read",
            "查看停产通知",
            "case.write",
            "通知与需求编制",
            "case.review",
            "独立决策复核",
            "case.fulfill",
            "采购与实施登记",
            "dashboard",
            "通知统计",
            "export",
            "导出记录",
            "audit",
            "操作审计",
            "admin",
            "系统管理");
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", names.keySet());
    role("需求编制员", "DEPARTMENT", Set.of("case.read", "case.write", "dashboard", "export", "audit"));
    role("独立复核员", "SELF", Set.of("case.read", "case.review", "dashboard", "export"));
    role("采购执行员", "SELF", Set.of("case.read", "case.fulfill", "dashboard", "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.departmentId = dep.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.passwordHash = encoder.encode(password);
    a.enabled = true;
    db.save(a);
    String[][] menu = {
      {"workbench", "停产工作台", "Workbench", "case.read"},
      {"cases", "停产通知", "Notices", "case.read"},
      {"dashboard", "通知统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "通知类别", "Categories", "admin"},
      {"settings", "系统参数", "Settings", "admin"},
      {"about", "关于系统", "About", "case.read"}
    };
    for (int i = 0; i < menu.length; i++) {
      var m = new NavMenu();
      m.code = menu[i][0];
      m.name = menu[i][1];
      m.nameEn = menu[i][2];
      m.permissionCode = menu[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "知华停产料号协作", "maxCases", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    for (var k :
        new String[][] {
          {"PDN", "停产通知", "Discontinuance"},
          {"NRND", "不建议新设计", "NRND"},
          {"OTHER", "其他停供通知", "Other"}
        }) {
      var e = new DictionaryEntry();
      e.type = "eol";
      e.code = k[0];
      e.name = k[1];
      e.nameEn = k[2];
      db.save(e);
    }
  }

  private void role(String n, String s, Set<String> p) {
    var r = new AccessRole();
    r.name = n;
    r.scope = s;
    r.permissions = new HashSet<>(p);
    db.save(r);
  }
}

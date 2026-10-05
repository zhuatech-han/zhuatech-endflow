// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 停产协作 HTTP 接口，事务服务再次校验范围与业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final EndService service;
  final AdminService admin;

  public ApiController(EndService s, AdminService a) {
    service = s;
    admin = a;
  }

  /** /options 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** /workbench 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** /dashboard 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** /audit 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** /cases 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, page, size, sort);
  }

  /** /cases/{id} 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** /cases 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases")
  public Object create(@RequestBody EndService.Input v) {
    return service.save(null, v);
  }

  /** /cases/{id} 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/cases/{id}")
  public Object save(@PathVariable Long id, @RequestBody EndService.Input v) {
    return service.save(id, v);
  }

  /** /cases/{id} 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/cases/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, null, version);
    return Map.of("ok", true);
  }

  /** /cases/{id}/lines 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases/{id}/lines")
  public Object line(@PathVariable Long id, @RequestBody EndService.LineInput v) {
    return service.saveLine(id, null, v);
  }

  /** /cases/{id}/lines/{line} 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/cases/{id}/lines/{line}")
  public Object lineUpdate(
      @PathVariable Long id, @PathVariable Long line, @RequestBody EndService.LineInput v) {
    return service.saveLine(id, line, v);
  }

  /** /cases/{id}/lines/{line} 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/cases/{id}/lines/{line}")
  public Object lineDelete(
      @PathVariable Long id, @PathVariable Long line, @RequestParam Long version) {
    service.delete(id, line, version);
    return Map.of("ok", true);
  }

  /** /cases/{id}/commands/{action} 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/cases/{id}/commands/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody EndService.Command v) {
    return service.command(id, action, v);
  }

  /**
   * /cases/{id}/receipts/{receipt}/reverse 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech /
   * zhuatech2。
   */
  @PostMapping("/cases/{id}/receipts/{receipt}/reverse")
  public Object reverse(
      @PathVariable Long id, @PathVariable Long receipt, @RequestBody EndService.Command v) {
    return service.reverse(id, receipt, v);
  }

  /** /cases/{id}/report.json 业务操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/cases/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=eol-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report(id));
  }

  /** ALL系统管理员目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建校验后的管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 编辑管理资源并保护系统管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminSave(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 外键保护已引用管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}

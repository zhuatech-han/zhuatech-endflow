// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import jakarta.persistence.*;
import java.time.*;

/** 按产品用途独立分配的需求快照，固定个数单位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "demand_line")
public class DemandLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "product_code", length = 100)
  public String productCode;

  @Column(name = "description", length = 300)
  public String description;

  @Column(name = "evidence", length = 2000)
  public String evidence;

  @Column(name = "monthly_demand")
  public int monthlyDemand;

  @Column(name = "months")
  public int months;

  @Column(name = "service_reserve")
  public int serviceReserve;

  @Column(name = "allocated_stock")
  public int allocatedStock;

  @Column(name = "confirmed_inbound")
  public int confirmedInbound;
}

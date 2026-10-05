// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 停产通知、冻结决策及履约状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "eol_case")
public class EolCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "version")
  public long version;

  @Column(name = "code", length = 60)
  public String code;

  @Column(name = "title", length = 160)
  public String title;

  @Column(name = "category", length = 60)
  public String category;

  @Column(name = "manufacturer", length = 160)
  public String manufacturer;

  @Column(name = "part_number", length = 160)
  public String partNumber;

  @Column(name = "source_ref", length = 300)
  public String sourceRef;

  @Column(name = "department_id")
  public Long departmentId;

  @Column(name = "author_id")
  public Long authorId;

  @Column(name = "buyer_id")
  public Long buyerId;

  @Column(name = "reviewer_id")
  public Long reviewerId;

  @Column(name = "notice_date")
  public LocalDate noticeDate;

  @Column(name = "last_buy_date")
  public LocalDate lastBuyDate;

  @Column(name = "last_ship_date")
  public LocalDate lastShipDate;

  @Column(name = "decision", length = 30)
  public String decision;

  @Column(name = "alternative_part", length = 160)
  public String alternativePart;

  @Column(name = "validation_ref", length = 300)
  public String validationRef;

  @Column(name = "rationale", length = 2000)
  public String rationale;

  @Column(name = "pack_size")
  public int packSize;

  @Column(name = "minimum_order")
  public int minimumOrder;

  @Column(name = "decision_qty")
  public int decisionQty;

  @Column(name = "unit_price", precision = 18, scale = 4)
  public BigDecimal unitPrice;

  @Column(name = "budget", precision = 18, scale = 2)
  public BigDecimal budget;

  @Column(name = "status", length = 30)
  public String status = "DRAFT";

  @Column(name = "submitted")
  public boolean submitted;

  @Column(name = "order_ref", length = 160)
  public String orderRef = "";

  @Column(name = "implementation_ref", length = 300)
  public String implementationRef = "";

  @Column(name = "order_date")
  public LocalDate orderDate;

  @Column(name = "promised_date")
  public LocalDate promisedDate;

  @Column(name = "created_at")
  public Instant createdAt;
}

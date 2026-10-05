// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import jakarta.persistence.*;
import java.time.*;

/** 分批实际收货登记，冲正保留原行及依据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "receipt")
public class Receipt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "actor_id")
  public Long actorId;

  @Column(name = "reversed_by")
  public Long reversedBy;

  @Column(name = "quantity")
  public int quantity;

  @Column(name = "reference", length = 160)
  public String reference;

  @Column(name = "evidence", length = 2000)
  public String evidence;

  @Column(name = "reversal_note", length = 2000)
  public String reversalNote = "";

  @Column(name = "received_date")
  public LocalDate receivedDate;

  @Column(name = "created_at")
  public Instant createdAt;

  @Column(name = "reversed_at")
  public Instant reversedAt;
}

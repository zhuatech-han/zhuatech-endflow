// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import jakarta.persistence.*;
import java.time.*;

/** 单据内不可变决策依据和状态留痕。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "case_event")
public class CaseEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "case_id")
  public Long caseId;

  @Column(name = "actor_id")
  public Long actorId;

  @Column(name = "action", length = 60)
  public String action;

  @Column(name = "before_status", length = 30)
  public String beforeStatus;

  @Column(name = "after_status", length = 30)
  public String afterStatus;

  @Column(name = "evidence", length = 2000)
  public String evidence;

  @Column(name = "created_at")
  public Instant createdAt;
}

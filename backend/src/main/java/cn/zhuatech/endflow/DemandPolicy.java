// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import java.math.*;
import java.util.*;

/** 固定输入快照的个数缺口和包装取整，逐用途独立核算防止跨用途抵扣。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class DemandPolicy {
  /** 返回总需求、短缺、采购建议及金额；仅为人工决策计算依据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Map<String, Object> calculate(EolCase c, List<DemandLine> lines) {
    long demand = 0, shortage = 0;
    for (var l : lines) {
      long gross = (long) l.monthlyDemand * l.months + l.serviceReserve;
      demand += gross;
      shortage += Math.max(0, gross - l.allocatedStock - l.confirmedInbound);
    }
    long recommended =
        shortage == 0
            ? 0
            : ((Math.max(shortage, c.minimumOrder) + c.packSize - 1) / c.packSize) * c.packSize;
    if (recommended > 1000000000L) throw new Problem(400, "QUANTITY_LIMIT");
    return Map.of(
        "demand",
        demand,
        "shortage",
        shortage,
        "recommended",
        recommended,
        "recommendedCost",
        c.unitPrice.multiply(BigDecimal.valueOf(recommended)).setScale(2, RoundingMode.HALF_UP),
        "decisionCost",
        c.unitPrice.multiply(BigDecimal.valueOf(c.decisionQty)).setScale(2, RoundingMode.HALF_UP));
  }

  private DemandPolicy() {}
}

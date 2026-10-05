// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.endflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 算术边界、独立用途和包装数量的业务验证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class DemandPolicyTest {
  EolCase c() {
    var c = new EolCase();
    c.packSize = 25;
    c.minimumOrder = 100;
    c.unitPrice = new BigDecimal("1.1250");
    return c;
  }

  DemandLine line(int demand, int stock) {
    var l = new DemandLine();
    l.monthlyDemand = demand;
    l.months = 1;
    l.allocatedStock = stock;
    return l;
  }

  @Test
  void roundsPackAfterMinimum() {
    var r = DemandPolicy.calculate(c(), List.of(line(102, 0)));
    assertEquals(125L, r.get("recommended"));
    assertEquals(new BigDecimal("140.63"), r.get("recommendedCost"));
  }

  @Test
  void surplusCannotOffsetOtherProduct() {
    var r = DemandPolicy.calculate(c(), List.of(line(10, 500), line(99, 0)));
    assertEquals(99L, r.get("shortage"));
    assertEquals(100L, r.get("recommended"));
  }

  @Test
  void noShortageDoesNotForceMinimum() {
    assertEquals(0L, DemandPolicy.calculate(c(), List.of(line(10, 10))).get("recommended"));
  }

  @Test
  void refusesOversizedCombinedDemand() {
    var a = line(1000000, 0);
    a.months = 120;
    a.serviceReserve = 100000000;
    assertThrows(Problem.class, () -> DemandPolicy.calculate(c(), Collections.nCopies(100, a)));
  }
}

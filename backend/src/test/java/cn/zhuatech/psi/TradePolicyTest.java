// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import org.junit.jupiter.api.Test;

/** 数量、尾差与公式防护的独立会计边界测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class TradePolicyTest {
  @Test
  void fractionalBatchesKeepInvoiceTotal() {
    var p = new BigDecimal("0.01");
    var first = TradePolicy.increment(BigDecimal.ZERO, new BigDecimal("0.333"), p);
    var second = TradePolicy.increment(new BigDecimal("0.333"), new BigDecimal("0.667"), p);
    assertEquals(new BigDecimal("0.01"), first.add(second));
  }

  @Test
  void finalUnitRemovesResidual() {
    assertEquals(
        new BigDecimal("0.01"),
        TradePolicy.cost(new BigDecimal("1"), new BigDecimal("0.01"), new BigDecimal("1")));
  }

  @Test
  void negativeAndExcessQuantityRejected() {
    assertThrows(Problem.class, () -> TradePolicy.quantity(new BigDecimal("-1"), false));
    assertThrows(
        Problem.class,
        () -> TradePolicy.cost(new BigDecimal("2"), new BigDecimal("5"), new BigDecimal("3")));
  }

  @Test
  void noSilentPrecisionLoss() {
    assertThrows(Problem.class, () -> TradePolicy.quantity(new BigDecimal("0.0001"), true));
    assertThrows(Problem.class, () -> TradePolicy.money(new BigDecimal("1.001")));
  }

  @Test
  void csvFormulaQuoted() {
    assertEquals("\"'=SUM(A1)\"", TradePolicy.csv("=SUM(A1)"));
    assertEquals("\"a\"\"b\"", TradePolicy.csv("a\"b"));
  }
}

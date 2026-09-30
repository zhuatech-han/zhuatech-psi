// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import java.math.*;

/** 数量精度、金额舍入与移动平均出库规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class TradePolicy {
  private TradePolicy() {}

  /** 校验最多三位数量精度，拒绝数据库静默舍入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal quantity(BigDecimal n, boolean positive) {
    if (n == null
        || n.scale() > 3
        || n.compareTo(new BigDecimal("1000000")) > 0
        || n.signum() < (positive ? 1 : 0)) throw new Problem(400, "INVALID_QUANTITY");
    return n.setScale(3);
  }

  /** 校验两位币种金额及有限范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal money(BigDecimal n) {
    if (n == null
        || n.scale() > 2
        || n.signum() < 0
        || n.compareTo(new BigDecimal("1000000000000")) > 0)
      throw new Problem(400, "INVALID_MONEY");
    return n.setScale(2);
  }

  /** 按已履约总量计算累计金额的增量，保证分批与整批金额一致。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal increment(BigDecimal previous, BigDecimal quantity, BigDecimal price) {
    return previous
        .add(quantity)
        .multiply(price)
        .setScale(2, RoundingMode.HALF_UP)
        .subtract(previous.multiply(price).setScale(2, RoundingMode.HALF_UP));
  }

  /** 按移动平均计价，最后一件带走剩余价值以避免零库存残值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal cost(BigDecimal available, BigDecimal value, BigDecimal out) {
    if (out.compareTo(available) > 0) throw new Problem(409, "INSUFFICIENT_STOCK");
    return out.compareTo(available) == 0
        ? value
        : value.multiply(out).divide(available, 2, RoundingMode.HALF_UP);
  }

  /** CSV 文本转义并防护公式前缀，业务导出不添加品牌广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String csv(Object o) {
    var s = String.valueOf(o);
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}

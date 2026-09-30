// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 商品仓库实时结存与移动平均库存价值。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "stock_balance")
public class StockBalance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
  public BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "inventory_value", nullable = false, precision = 18, scale = 2)
  public BigDecimal value = BigDecimal.ZERO;
}

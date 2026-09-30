// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 单据商品快照与累计履约、退货量。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "order_line")
public class OrderLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Column(name = "product_code", nullable = false, length = 60)
  public String productCode;

  @Column(name = "product_name", nullable = false, length = 120)
  public String productName;

  @Column(name = "unit", nullable = false, length = 20)
  public String unit;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
  public BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "price", nullable = false, precision = 18, scale = 2)
  public BigDecimal price = BigDecimal.ZERO;

  @Column(name = "fulfilled", nullable = false, precision = 18, scale = 3)
  public BigDecimal fulfilled = BigDecimal.ZERO;

  @Column(name = "returned", nullable = false, precision = 18, scale = 3)
  public BigDecimal returned = BigDecimal.ZERO;
}

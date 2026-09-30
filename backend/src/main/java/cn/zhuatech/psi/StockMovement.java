// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 不可修改的收发货、退货、调拨与盘点流水，保留价值和单据金额。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "stock_movement")
public class StockMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "order_id", nullable = true)
  public Long orderId;

  @Column(name = "line_id", nullable = true)
  public Long lineId;

  @Column(name = "source_id", nullable = true)
  public Long sourceId;

  @Column(name = "kind", nullable = false, length = 30)
  public String kind;

  @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
  public BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "inventory_value", nullable = false, precision = 18, scale = 2)
  public BigDecimal value = BigDecimal.ZERO;

  @Column(name = "amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "note", nullable = false, length = 500)
  public String note = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy;
}

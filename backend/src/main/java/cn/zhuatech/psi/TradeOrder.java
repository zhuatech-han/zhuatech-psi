// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 采购与销售单，订单确认、实物过账和收付款分别记录。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "trade_order")
public class TradeOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "number", nullable = false, length = 60)
  public String number;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "party_id", nullable = false)
  public Long partyId;

  @Column(name = "party_name", nullable = false, length = 120)
  public String partyName;

  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "status", nullable = false, length = 20)
  public String status = "DRAFT";

  @Column(name = "currency", nullable = false, length = 3)
  public String currency;

  @Column(name = "notes", nullable = false, length = 1000)
  public String notes = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy;

  @Column(name = "net_amount", nullable = false, precision = 18, scale = 2)
  public BigDecimal netAmount = BigDecimal.ZERO;

  @Column(name = "net_cost", nullable = false, precision = 18, scale = 2)
  public BigDecimal netCost = BigDecimal.ZERO;

  @Column(name = "net_paid", nullable = false, precision = 18, scale = 2)
  public BigDecimal netPaid = BigDecimal.ZERO;
}

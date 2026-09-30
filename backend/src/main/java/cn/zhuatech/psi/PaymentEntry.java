// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 人工核对的收付款、退款凭证；不调用支付网关。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "payment_entry")
public class PaymentEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reversal_of")
  public Long reversalOf;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

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

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 商品及基本计量单位，价格不回写历史单据。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "product")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "barcode", nullable = true, length = 100)
  public String barcode;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "specification", nullable = false, length = 200)
  public String specification = "";

  @Column(name = "unit", nullable = false, length = 20)
  public String unit;

  @Column(name = "category_id", nullable = false)
  public Long categoryId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "sale_price", nullable = false, precision = 18, scale = 2)
  public BigDecimal salePrice = BigDecimal.ZERO;

  @Column(name = "purchase_price", nullable = false, precision = 18, scale = 2)
  public BigDecimal purchasePrice = BigDecimal.ZERO;

  @Column(name = "reorder_level", nullable = false, precision = 18, scale = 3)
  public BigDecimal reorderLevel = BigDecimal.ZERO;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}

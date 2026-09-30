// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 主数据维护；部门归属和历史引用保护在服务端校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MasterService {
  final Store db;
  final AccessService access;

  public MasterService(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 有限字段，不接受任意实体或库存数量写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String code,
      String barcode,
      String name,
      String nameEn,
      String specification,
      String unit,
      Long categoryId,
      Long departmentId,
      String kind,
      String contact,
      String notes,
      Boolean enabled,
      BigDecimal salePrice,
      BigDecimal purchasePrice,
      BigDecimal reorderLevel) {}

  /** 列出授权范围内主数据，最多一万条供选择器使用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> list(String type) {
    access.require("master.read");
    return db.all(type(type)).stream().filter(this::visible).toList();
  }

  /** 新建或修改档案；引用后的基本单位、部门与往来类型冻结。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Input v) {
    access.require("master.write");
    db.lock(Department.class, 1L);
    Object obj = id == null ? null : db.get(type(type), id);
    if (obj != null && !visible(obj)) throw new Problem(403, "OUT_OF_SCOPE");
    Long dept = v.departmentId == null ? access.current().departmentId : v.departmentId;
    db.get(Department.class, dept);
    access.department(dept);
    Object result;
    switch (type) {
      case "categories" -> {
        var e = id == null ? new Category() : (Category) obj;
        e.name = AdminService.text(v.name, 120);
        e.nameEn = AdminService.text(v.nameEn, 120);
        result = id == null ? db.save(e) : e;
      }
      case "parties" -> {
        var e = id == null ? new Party() : (Party) obj;
        if (v.kind == null || !Set.of("CUSTOMER", "SUPPLIER").contains(v.kind))
          throw new Problem(400, "INVALID_KIND");
        if (id != null && (!Objects.equals(e.departmentId, dept) || !e.kind.equals(v.kind)))
          throw new Problem(409, "IMMUTABLE_FIELD");
        e.code = AdminService.text(v.code, 60);
        e.name = AdminService.text(v.name, 120);
        e.kind = v.kind;
        e.contact = optional(v.contact, 200);
        e.notes = optional(v.notes, 1000);
        e.departmentId = dept;
        e.enabled = Boolean.TRUE.equals(v.enabled);
        result = id == null ? db.save(e) : e;
      }
      case "warehouses" -> {
        var e = id == null ? new Warehouse() : (Warehouse) obj;
        if (id != null && !Objects.equals(e.departmentId, dept))
          throw new Problem(409, "IMMUTABLE_FIELD");
        e.code = AdminService.text(v.code, 60);
        e.name = AdminService.text(v.name, 120);
        e.departmentId = dept;
        e.enabled = Boolean.TRUE.equals(v.enabled);
        result = id == null ? db.save(e) : e;
      }
      case "products" -> {
        var e = id == null ? new Product() : (Product) obj;
        String unit = AdminService.text(v.unit, 20);
        if (id != null && (!Objects.equals(e.departmentId, dept) || !e.unit.equals(unit)))
          throw new Problem(409, "IMMUTABLE_FIELD");
        e.code = AdminService.text(v.code, 60);
        e.barcode =
            v.barcode == null || v.barcode.isBlank() ? null : AdminService.text(v.barcode, 100);
        e.name = AdminService.text(v.name, 120);
        e.specification = optional(v.specification, 200);
        e.unit = unit;
        e.categoryId = db.get(Category.class, v.categoryId).id;
        e.departmentId = dept;
        e.salePrice = TradePolicy.money(v.salePrice);
        e.purchasePrice = TradePolicy.money(v.purchasePrice);
        e.reorderLevel = TradePolicy.quantity(v.reorderLevel, false);
        e.enabled = Boolean.TRUE.equals(v.enabled);
        result = id == null ? db.save(e) : e;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("MASTER_SAVE_" + type, id == null ? "NEW" : id, dept);
    return result;
  }

  /** 仅删除没有订单、流水或库存引用的主数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    access.require("master.write");
    db.lock(Department.class, 1L);
    var e = db.get(type(type), id);
    if (!visible(e)) throw new Problem(403, "OUT_OF_SCOPE");
    db.delete(e);
    access.audit("MASTER_DELETE_" + type, id, access.current().departmentId);
  }

  /** 主数据可见性由归属部门决定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Object v) {
    if (v instanceof Party p) return access.visible(p.departmentId);
    if (v instanceof Product p) return access.visible(p.departmentId);
    if (v instanceof Warehouse w) return access.visible(w.departmentId);
    return true;
  }

  /** 非必填文本长度检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String optional(String s, int max) {
    if (s == null) return "";
    if (s.length() > max) throw new Problem(400, "INVALID_INPUT");
    return s.trim();
  }

  private Class<?> type(String type) {
    return switch (type) {
      case "categories" -> Category.class;
      case "parties" -> Party.class;
      case "products" -> Product.class;
      case "warehouses" -> Warehouse.class;
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }
}

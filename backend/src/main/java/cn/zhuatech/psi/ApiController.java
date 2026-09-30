// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 分页业务端、管理端、对账与导出，所有资源均重新检查权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final Store db;
  final AccessService access;
  final MasterService masters;
  final AdminService admin;
  final TradeService trade;

  public ApiController(
      Store db,
      AccessService access,
      MasterService masters,
      AdminService admin,
      TradeService trade) {
    this.db = db;
    this.access = access;
    this.masters = masters;
    this.admin = admin;
    this.trade = trade;
  }

  /** 受控参考资料；业务主数据按部门和读取权限返回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    access.current();
    var out = new HashMap<String, Object>();
    out.put(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList());
    out.put("settings", db.all(SystemSetting.class));
    out.put("dictionaries", db.all(DictionaryEntry.class));
    if (access.role().permissions.contains("admin") && access.role().scope.equals("ALL")) {
      out.put("roles", db.all(AccessRole.class));
      out.put("permissions", db.all(Permission.class));
    }
    if (access.role().permissions.contains("master.read")) {
      for (var type : List.of("products", "parties", "warehouses", "categories"))
        out.put(type, masters.list(type));
    }
    return out;
  }

  /** 搜索、分页、状态筛选与白名单排序，过滤先于分页。小型学习版资源上限一万条。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lists/{resource}")
  @Transactional(readOnly = true)
  public Map<String, Object> list(
      @PathVariable String resource,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "id") String sort,
      @RequestParam(defaultValue = "true") boolean desc,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0
        || size < 1
        || size > 100
        || search.length() > 200
        || !Set.of("id", "name", "code", "number", "createdAt").contains(sort))
      throw new Problem(400, "INVALID_PAGE");
    List<?> rows = rows(resource);
    String q = search.toLowerCase(Locale.ROOT);
    var filtered =
        rows.stream()
            .filter(r -> searchText(r).toLowerCase(Locale.ROOT).contains(q))
            .filter(
                r ->
                    status.isBlank()
                        || Objects.equals(field(r, "status"), status)
                        || Objects.equals(field(r, "kind"), status))
            .sorted(
                (a, b) -> {
                  int n =
                      sort.equals("id")
                          ? Long.compare(
                              ((Number) field(a, "id")).longValue(),
                              ((Number) field(b, "id")).longValue())
                          : String.valueOf(field(a, sort))
                              .compareTo(String.valueOf(field(b, sort)));
                  return desc ? -n : n;
                })
            .toList();
    return Map.of(
        "items",
        filtered.stream().skip((long) page * size).limit(size).toList(),
        "total",
        filtered.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 主数据创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/{type}")
  public Object createMaster(@PathVariable String type, @RequestBody MasterService.Input v) {
    return masters.save(type, null, v);
  }

  /** 主数据编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/{type}/{id}")
  public Object updateMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody MasterService.Input v) {
    return masters.save(type, id, v);
  }

  /** 引用保护的主数据删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Object deleteMaster(@PathVariable String type, @PathVariable Long id) {
    masters.delete(type, id);
    return Map.of("ok", true);
  }

  /** 商品 JSON 批量导入，任一项失败整个批次回滚。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/products/import")
  @Transactional
  public Object importProducts(@RequestBody List<MasterService.Input> items) {
    access.require("master.write");
    if (items == null || items.isEmpty() || items.size() > 500)
      throw new Problem(400, "INVALID_INPUT");
    for (var item : items) masters.save("products", null, item);
    return Map.of("created", items.size());
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 管理资源删除，保留最后管理员和内建目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }

  /** 商品扫码带出真实档案，精确匹配条码或编码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/barcode")
  @Transactional(readOnly = true)
  public Object barcode(@RequestParam String value) {
    access.require("master.read");
    if (value.length() > 100) throw new Problem(400, "INVALID_INPUT");
    return db
        .query(Product.class, "from Product where (barcode=?1 or code=?1) and enabled=true", value)
        .stream()
        .filter(p -> access.visible(p.departmentId))
        .findFirst()
        .orElseThrow(() -> new Problem(404, "NOT_FOUND"));
  }

  /** 草稿创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders")
  public Object createOrder(@RequestBody TradeService.Draft v) {
    return trade.draft(null, v);
  }

  /** 只编辑草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/orders/{id}")
  public Object editOrder(@PathVariable Long id, @RequestBody TradeService.Draft v) {
    return trade.draft(id, v);
  }

  /** 删除草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/orders/{id}")
  public Object deleteOrder(@PathVariable Long id) {
    trade.delete(id);
    return Map.of("ok", true);
  }

  /** 查询完整单据与台账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}")
  public Object detail(@PathVariable Long id) {
    return trade.detail(id);
  }

  /** 单据状态与货款动作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/{action}")
  public Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody TradeService.Action v) {
    return trade.action(id, action, v);
  }

  /** 库存期初、调拨及盘点过账。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/stock/{action}")
  public Object stock(@PathVariable String action, @RequestBody TradeService.StockAction v) {
    return trade.stockAction(action, v);
  }

  /** 工作台按可见岗位汇总，财务汇总仅对财务或报表角色开放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Map<String, Object> dashboard() {
    access.require("dashboard");
    var perms = access.role().permissions;
    var orders =
        db.all(TradeOrder.class).stream()
            .filter(
                d ->
                    access.visible(d.departmentId)
                        && perms.contains(d.kind.equals("SALES") ? "sales.read" : "purchase.read"))
            .toList();
    var out = new HashMap<String, Object>();
    out.put(
        "pending",
        orders.stream().filter(d -> Set.of("CONFIRMED", "PARTIAL").contains(d.status)).count());
    out.put(
        "recent",
        orders.stream()
            .sorted(Comparator.comparing((TradeOrder d) -> d.createdAt).reversed())
            .limit(8)
            .toList());
    out.put("currency", trade.setting("currency"));
    if (perms.contains("stock.read")) {
      var stock = trade.stockList();
      out.put(
          "inventoryValue",
          stock.stream().map(s -> s.value).reduce(BigDecimal.ZERO, BigDecimal::add));
      out.put(
          "lowStock",
          stock.stream()
              .filter(
                  s -> s.quantity.compareTo(db.get(Product.class, s.productId).reorderLevel) < 0)
              .toList());
    }
    if (perms.contains("finance") || perms.contains("report")) {
      out.put("finance", summary(orders));
    }
    return out;
  }

  /** 报表保留往来余额、净发货销售额和出库成本，非税务利润报表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  @Transactional(readOnly = true)
  public Object reports() {
    access.require("report");
    var orders =
        db.all(TradeOrder.class).stream().filter(d -> access.visible(d.departmentId)).toList();
    return Map.of(
        "summary", summary(orders), "orders", orders, "currency", trade.setting("currency"));
  }

  /** 依照同一计算口径导出全部授权订单，不导出未获授权的部门。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports.csv")
  @Transactional
  public ResponseEntity<byte[]> export() {
    access.require("report");
    var out =
        new StringBuilder(
            "\ufeffOrder,Type,Party,Status,Currency,Posted amount,Cost,Net paid,Balance,Gross margin\r\n");
    for (var d : db.all(TradeOrder.class)) {
      if (!access.visible(d.departmentId)) continue;
      out.append(
              List.of(
                      d.number,
                      d.kind,
                      d.partyName,
                      d.status,
                      d.currency,
                      d.netAmount,
                      d.netCost,
                      d.netPaid,
                      d.netAmount.subtract(d.netPaid),
                      d.kind.equals("SALES") ? d.netAmount.subtract(d.netCost) : BigDecimal.ZERO)
                  .stream()
                  .map(TradePolicy::csv)
                  .collect(Collectors.joining(",")))
          .append("\r\n");
    }
    access.audit("REPORT_EXPORT", "ORDERS", access.current().departmentId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trade.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(out.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** 单一往来单位的货款流水；退款与冲销使用符号变化，余额可逐行核对。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/statement")
  @Transactional(readOnly = true)
  public Map<String, Object> statement(@RequestParam Long partyId) {
    access.require("finance");
    var party = db.get(Party.class, partyId);
    access.department(party.departmentId);
    var orders =
        db.query(TradeOrder.class, "from TradeOrder where partyId=?1 order by id", partyId);
    var entries = new ArrayList<Map<String, Object>>();
    BigDecimal balance = BigDecimal.ZERO;
    for (var d : orders) {
      for (var m :
          db.query(StockMovement.class, "from StockMovement where orderId=?1 order by id", d.id)) {
        if (m.amount.signum() == 0) continue;
        entries.add(
            Map.of(
                "at",
                m.createdAt,
                "order",
                d.number,
                "kind",
                m.kind,
                "reference",
                m.reference,
                "change",
                m.amount,
                "sequence",
                m.id));
      }
      for (var p :
          db.query(PaymentEntry.class, "from PaymentEntry where orderId=?1 order by id", d.id)) {
        var change =
            Set.of("PAY", "REVERSAL_REFUND").contains(p.kind) ? p.amount.negate() : p.amount;
        entries.add(
            Map.of(
                "at",
                p.createdAt,
                "order",
                d.number,
                "kind",
                p.kind,
                "reference",
                p.reference,
                "change",
                change,
                "sequence",
                p.id));
      }
    }
    entries.sort(Comparator.comparing(e -> (java.time.Instant) e.get("at")));
    var output = new ArrayList<Map<String, Object>>();
    for (var entry : entries) {
      balance = balance.add((BigDecimal) entry.get("change"));
      var item = new HashMap<>(entry);
      item.put("balance", balance);
      output.add(item);
    }
    return Map.of(
        "party",
        party,
        "entries",
        output,
        "balance",
        balance,
        "currency",
        trade.setting("currency"));
  }

  /** 权限与部门检查后的对账导出，包含与界面同口径的累计余额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/statement.csv")
  @Transactional
  @SuppressWarnings("unchecked")
  public ResponseEntity<byte[]> statementExport(@RequestParam Long partyId) {
    var view = statement(partyId);
    var out = new StringBuilder("\ufeffTime UTC,Order,Type,Reference,Change,Balance\r\n");
    for (var e : (List<Map<String, Object>>) view.get("entries"))
      out.append(
              List.of(
                      e.get("at"),
                      e.get("order"),
                      e.get("kind"),
                      e.get("reference"),
                      e.get("change"),
                      e.get("balance"))
                  .stream()
                  .map(TradePolicy::csv)
                  .collect(Collectors.joining(",")))
          .append("\r\n");
    access.audit("STATEMENT_EXPORT", partyId, ((Party) view.get("party")).departmentId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement.csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(out.toString().getBytes(StandardCharsets.UTF_8));
  }

  private Map<String, BigDecimal> summary(List<TradeOrder> orders) {
    var out = new TreeMap<String, BigDecimal>();
    for (var key :
        List.of(
            "receivable",
            "payable",
            "customerCredit",
            "supplierCredit",
            "netSales",
            "salesCost",
            "grossMargin")) out.put(key, BigDecimal.ZERO);
    for (var d : orders) {
      var balance = d.netAmount.subtract(d.netPaid);
      String key =
          d.kind.equals("SALES")
              ? (balance.signum() >= 0 ? "receivable" : "customerCredit")
              : (balance.signum() >= 0 ? "payable" : "supplierCredit");
      out.merge(key, balance.abs(), BigDecimal::add);
      if (d.kind.equals("SALES")) {
        out.merge("netSales", d.netAmount, BigDecimal::add);
        out.merge("salesCost", d.netCost, BigDecimal::add);
        out.merge("grossMargin", d.netAmount.subtract(d.netCost), BigDecimal::add);
      }
    }
    return out;
  }

  private List<?> rows(String resource) {
    return switch (resource) {
      case "products", "parties", "categories", "warehouses" -> masters.list(resource);
      case "sales", "purchase" -> {
        access.require(resource + ".read");
        yield db.all(TradeOrder.class).stream()
            .filter(
                d ->
                    d.kind.equals(resource.equals("sales") ? "SALES" : "PURCHASE")
                        && access.visible(d.departmentId))
            .toList();
      }
      case "finance" -> {
        access.require("finance");
        yield db.all(TradeOrder.class).stream()
            .filter(
                d ->
                    access.visible(d.departmentId)
                        && !Set.of("DRAFT", "CANCELLED").contains(d.status))
            .toList();
      }
      case "stock" -> trade.stockList();
      case "movements" -> trade.movements();
      case "audit" -> {
        access.require("audit");
        yield db.all(AuditEvent.class).stream()
            .filter(e -> access.visible(e.departmentId))
            .toList();
      }
      default -> admin.list(resource);
    };
  }

  private Object field(Object r, String f) {
    try {
      return r.getClass().getField(f).get(r);
    } catch (NoSuchFieldException e) {
      return "";
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private String searchText(Object r) {
    if (r instanceof Account a) return a.username + " " + a.displayName;
    if (r instanceof TradeOrder d) return d.number + " " + d.partyName + " " + d.status;
    if (r instanceof Product p)
      return p.code + " " + p.barcode + " " + p.name + " " + p.specification;
    if (r instanceof StockBalance s)
      return db.get(Product.class, s.productId).code
          + " "
          + db.get(Product.class, s.productId).name
          + " "
          + db.get(Warehouse.class, s.warehouseId).name;
    if (r instanceof StockMovement m)
      return m.kind
          + " "
          + m.reference
          + " "
          + m.createdBy
          + " "
          + db.get(Product.class, m.productId).name;
    if (r instanceof AuditEvent a) return a.actor + " " + a.action + " " + a.objectId;
    return field(r, "name") + " " + field(r, "code");
  }
}

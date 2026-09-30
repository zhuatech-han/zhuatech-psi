// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 货物与往来款原子过账；确认单不改写，退货与纠错以新增凭证追溯。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class TradeService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public TradeService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 开单商品基本单位数量与单价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record LineInput(Long productId, BigDecimal quantity, BigDecimal price) {}

  /** 草稿保存输入，确认后不允许修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      String kind, Long partyId, Long warehouseId, String notes, List<LineInput> lines) {}

  /** 每次履约对应的原单行与数量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record PostLine(Long lineId, BigDecimal quantity) {}

  /** 明确的动作载荷；请求标识用于网络重试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Action(
      String requestKey,
      List<PostLine> lines,
      Long sourceId,
      BigDecimal quantity,
      BigDecimal amount,
      String reference,
      String note) {}

  /** 库存期初、调拨与盘点输入，盘点需提交观察到的账面量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record StockAction(
      String requestKey,
      Long productId,
      Long warehouseId,
      Long destinationId,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal expectedQuantity,
      String reference,
      String note) {}

  /** 查看采购或销售单，后端校验岗位和部门。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var d = authorized(id);
    return view(d);
  }

  /** 保存草稿；主数据归属一致且无失效商品，不写库存或应收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> draft(Long id, Draft v) {
    if (v.kind == null || !Set.of("PURCHASE", "SALES").contains(v.kind))
      throw new Problem(400, "INVALID_KIND");
    access.require(permission(v.kind) + ".write");
    db.lock(Department.class, 1L);
    var party = db.get(Party.class, v.partyId);
    access.department(party.departmentId);
    var w = db.get(Warehouse.class, v.warehouseId);
    access.department(w.departmentId);
    if (!party.enabled
        || !w.enabled
        || !Objects.equals(party.departmentId, w.departmentId)
        || !party.kind.equals(v.kind.equals("SALES") ? "CUSTOMER" : "SUPPLIER"))
      throw new Problem(400, "INVALID_MASTER");
    if (v.lines == null || v.lines.isEmpty() || v.lines.size() > 100)
      throw new Problem(400, "INVALID_LINES");
    var d = id == null ? new TradeOrder() : authorized(id);
    if (!d.status.equals("DRAFT") || id != null && !d.kind.equals(v.kind))
      throw new Problem(409, "INVALID_STATE");
    if (id == null) {
      d.number =
          (v.kind.equals("SALES") ? "SO-" : "PO-")
              + UUID.randomUUID().toString().substring(0, 18).toUpperCase(Locale.ROOT);
      d.createdAt = clock.instant();
      d.createdBy = access.current().username;
      d.currency = setting("currency");
      d.kind = v.kind;
    }
    d.partyId = party.id;
    d.partyName = party.name;
    d.warehouseId = w.id;
    d.departmentId = w.departmentId;
    d.notes = MasterService.optional(v.notes, 1000);
    if (id == null) db.save(d);
    else for (var l : lines(d.id)) db.delete(l);
    var known = new HashSet<Long>();
    var total = BigDecimal.ZERO;
    for (var input : v.lines) {
      if (!known.add(input.productId)) throw new Problem(400, "DUPLICATE_PRODUCT");
      var p = db.get(Product.class, input.productId);
      access.department(p.departmentId);
      if (!p.enabled || !Objects.equals(p.departmentId, d.departmentId))
        throw new Problem(400, "INVALID_MASTER");
      var l = new OrderLine();
      l.orderId = d.id;
      l.productId = p.id;
      l.productCode = p.code;
      l.productName = p.name;
      l.unit = p.unit;
      l.quantity = TradePolicy.quantity(input.quantity, true);
      l.price = TradePolicy.money(input.price);
      total = total.add(l.quantity.multiply(l.price).setScale(2, RoundingMode.HALF_UP));
      db.save(l);
    }
    TradePolicy.money(total);
    access.audit("ORDER_DRAFT", d.id, d.departmentId);
    return view(d);
  }

  /** 删除无履约、无凭证的草稿，历史单据只可作废。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id) {
    db.lock(Department.class, 1L);
    var d = authorized(id);
    access.require(permission(d.kind) + ".write");
    if (!d.status.equals("DRAFT")) throw new Problem(409, "INVALID_STATE");
    for (var l : lines(id)) db.delete(l);
    db.delete(d);
    access.audit("DELETE_DRAFT", id, d.departmentId);
  }

  /** 确认、部分过账、原履约退货、价格贷记、收付款和退款均原子且幂等。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> action(Long id, String action, Action v) {
    db.lock(Department.class, 1L);
    var d = authorized(id);
    access.require(
        switch (action) {
          case "confirm", "cancel" -> permission(d.kind) + ".write";
          case "post", "return" -> "stock.write";
          case "pay", "refund", "credit", "reversePayment" -> "finance";
          default -> throw new Problem(404, "NOT_FOUND");
        });
    if (repeated("ORDER:" + id, v.requestKey, action + ":" + v)) return view(d);
    switch (action) {
      case "confirm" -> {
        if (!d.status.equals("DRAFT")) throw new Problem(409, "INVALID_STATE");
        var p = db.get(Party.class, d.partyId);
        var w = db.get(Warehouse.class, d.warehouseId);
        if (!p.enabled || !w.enabled) throw new Problem(409, "INACTIVE_MASTER");
        for (var l : lines(d.id))
          if (!db.get(Product.class, l.productId).enabled)
            throw new Problem(409, "INACTIVE_MASTER");
        d.status = "CONFIRMED";
      }
      case "cancel" -> {
        if (!Set.of("DRAFT", "CONFIRMED").contains(d.status)
            || d.netAmount.signum() != 0
            || d.netPaid.signum() != 0) throw new Problem(409, "INVALID_STATE");
        d.status = "CANCELLED";
      }
      case "post" -> {
        post(d, v);
      }
      case "return" -> {
        returned(d, v);
      }
      case "credit" -> {
        active(d);
        var source = db.get(StockMovement.class, v.sourceId);
        if (!Objects.equals(source.orderId, id)
            || !Set.of("SHIPMENT", "RECEIPT").contains(source.kind))
          throw new Problem(400, "INVALID_SOURCE");
        var changes =
            db.query(StockMovement.class, "from StockMovement where sourceId=?1", source.id);
        if (changes.stream().anyMatch(m -> m.quantity.signum() != 0))
          throw new Problem(409, "RETURN_ALREADY_POSTED");
        var credited =
            changes.stream().map(m -> m.amount.abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var n = TradePolicy.money(v.amount);
        if (n.signum() <= 0 || n.add(credited).compareTo(source.amount) > 0)
          throw new Problem(400, "INVALID_AMOUNT");
        movement(
            source.productId,
            d.warehouseId,
            d.departmentId,
            d.id,
            source.lineId,
            source.id,
            "PRICE_CREDIT",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            n.negate(),
            AdminService.text(v.reference, 120),
            AdminService.text(v.note, 500));
        d.netAmount = d.netAmount.subtract(n);
      }
      case "reversePayment" -> {
        active(d);
        var source = db.get(PaymentEntry.class, v.sourceId);
        if (!source.orderId.equals(id) || !Set.of("PAY", "REFUND").contains(source.kind))
          throw new Problem(400, "INVALID_SOURCE");
        if (!db.query(PaymentEntry.class, "from PaymentEntry where reversalOf=?1", source.id)
            .isEmpty()) throw new Problem(409, "ALREADY_REVERSED");
        var signed = source.kind.equals("PAY") ? source.amount.negate() : source.amount;
        if (d.netPaid.add(signed).signum() < 0) throw new Problem(409, "INVALID_STATE");
        var p = new PaymentEntry();
        p.orderId = id;
        p.reversalOf = source.id;
        p.kind = "REVERSAL_" + source.kind;
        p.amount = source.amount;
        p.reference = AdminService.text(v.reference, 120);
        p.note = AdminService.text(v.note, 500);
        p.createdAt = clock.instant();
        p.createdBy = access.current().username;
        db.save(p);
        d.netPaid = d.netPaid.add(signed);
      }
      case "pay", "refund" -> {
        active(d);
        var n = TradePolicy.money(v.amount);
        var balance = d.netAmount.subtract(d.netPaid);
        var limit = action.equals("pay") ? balance : balance.negate();
        if (n.signum() <= 0 || n.compareTo(limit) > 0) throw new Problem(409, "EXCEEDS_BALANCE");
        var p = new PaymentEntry();
        p.orderId = id;
        p.kind = action.equals("pay") ? "PAY" : "REFUND";
        p.amount = n;
        p.reference = AdminService.text(v.reference, 120);
        p.note = MasterService.optional(v.note, 500);
        p.createdAt = clock.instant();
        p.createdBy = access.current().username;
        db.save(p);
        d.netPaid = d.netPaid.add(action.equals("pay") ? n : n.negate());
      }
    }
    access.audit("ORDER_" + action.toUpperCase(Locale.ROOT), id, d.departmentId);
    return view(d);
  }

  private void post(TradeOrder d, Action v) {
    active(d);
    if (d.status.equals("FULFILLED")) throw new Problem(409, "INVALID_STATE");
    if (v.lines == null || v.lines.isEmpty() || v.lines.size() > 100)
      throw new Problem(400, "INVALID_LINES");
    var done = new HashSet<Long>();
    String reference = AdminService.text(v.reference, 120);
    for (var item : v.lines) {
      if (!done.add(item.lineId)) throw new Problem(400, "DUPLICATE_LINE");
      var l = db.get(OrderLine.class, item.lineId);
      if (!l.orderId.equals(d.id)) throw new Problem(400, "INVALID_LINE");
      var q = TradePolicy.quantity(item.quantity, true);
      if (q.add(l.fulfilled).compareTo(l.quantity) > 0) throw new Problem(409, "EXCEEDS_QUANTITY");
      var s = stock(l.productId, d.warehouseId);
      var amount = TradePolicy.increment(l.fulfilled, q, l.price);
      var val = d.kind.equals("PURCHASE") ? amount : TradePolicy.cost(s.quantity, s.value, q);
      var sign = d.kind.equals("PURCHASE") ? BigDecimal.ONE : BigDecimal.ONE.negate();
      change(s, q.multiply(sign), val.multiply(sign));
      movement(
          l.productId,
          d.warehouseId,
          d.departmentId,
          d.id,
          l.id,
          null,
          d.kind.equals("PURCHASE") ? "RECEIPT" : "SHIPMENT",
          q.multiply(sign),
          val.multiply(sign),
          amount,
          reference,
          MasterService.optional(v.note, 500));
      l.fulfilled = l.fulfilled.add(q);
      d.netAmount = d.netAmount.add(amount);
      if (d.kind.equals("SALES")) d.netCost = d.netCost.add(val);
    }
    d.status =
        lines(d.id).stream().allMatch(l -> l.quantity.compareTo(l.fulfilled) == 0)
            ? "FULFILLED"
            : "PARTIAL";
    TradePolicy.money(d.netAmount);
  }

  private void returned(TradeOrder d, Action v) {
    active(d);
    var src = db.get(StockMovement.class, v.sourceId);
    if (!Objects.equals(src.orderId, d.id) || !Set.of("SHIPMENT", "RECEIPT").contains(src.kind))
      throw new Problem(400, "INVALID_SOURCE");
    var q = TradePolicy.quantity(v.quantity, true);
    var previous =
        db.query(StockMovement.class, "from StockMovement where sourceId=?1", src.id).stream()
            .map(m -> m.quantity.abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var total = previous.add(q);
    if (total.compareTo(src.quantity.abs()) > 0) throw new Problem(409, "EXCEEDS_QUANTITY");
    var credited =
        db
            .query(
                StockMovement.class,
                "from StockMovement where sourceId=?1 and kind=?2",
                src.id,
                "PRICE_CREDIT")
            .stream()
            .map(m -> m.amount.abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var amount = proportion(src.amount.subtract(credited), src.quantity.abs(), previous, total);
    if (amount.compareTo(d.netAmount) > 0) throw new Problem(409, "CREDIT_ALREADY_APPLIED");
    var l = db.get(OrderLine.class, src.lineId);
    var s = stock(src.productId, src.warehouseId);
    var val =
        d.kind.equals("SALES")
            ? proportion(src.value.abs(), src.quantity.abs(), previous, total)
            : TradePolicy.cost(s.quantity, s.value, q);
    var sign = d.kind.equals("SALES") ? BigDecimal.ONE : BigDecimal.ONE.negate();
    change(s, q.multiply(sign), val.multiply(sign));
    movement(
        l.productId,
        d.warehouseId,
        d.departmentId,
        d.id,
        l.id,
        src.id,
        d.kind.equals("SALES") ? "SALES_RETURN" : "PURCHASE_RETURN",
        q.multiply(sign),
        val.multiply(sign),
        amount.negate(),
        AdminService.text(v.reference, 120),
        AdminService.text(v.note, 500));
    l.returned = l.returned.add(q);
    d.netAmount = d.netAmount.subtract(amount);
    if (d.kind.equals("SALES")) d.netCost = d.netCost.subtract(val);
  }

  private BigDecimal proportion(
      BigDecimal value, BigDecimal all, BigDecimal before, BigDecimal after) {
    return value
        .multiply(after)
        .divide(all, 2, RoundingMode.HALF_UP)
        .subtract(value.multiply(before).divide(all, 2, RoundingMode.HALF_UP));
  }

  /** 期初入库、带快照检查的盘点及同部门调拨；每次保留不可修改流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<StockBalance> stockAction(String action, StockAction v) {
    access.require("stock.write");
    db.lock(Department.class, 1L);
    var p = db.get(Product.class, v.productId);
    var w = db.get(Warehouse.class, v.warehouseId);
    access.department(p.departmentId);
    access.department(w.departmentId);
    if (!p.enabled || !w.enabled || !Objects.equals(p.departmentId, w.departmentId))
      throw new Problem(400, "INVALID_MASTER");
    if (!Set.of("opening", "count", "transfer").contains(action))
      throw new Problem(404, "NOT_FOUND");
    if (repeated("STOCK:" + w.departmentId, v.requestKey, action + ":" + v)) return stockList();
    var s = stock(p.id, w.id);
    var q = TradePolicy.quantity(v.quantity, !action.equals("count"));
    var reference = AdminService.text(v.reference, 120);
    var note = AdminService.text(v.note, 500);
    switch (action) {
      case "opening" -> {
        if (!db.query(
                StockMovement.class,
                "from StockMovement where productId=?1 and warehouseId=?2",
                p.id,
                w.id)
            .isEmpty()) throw new Problem(409, "OPENING_EXISTS");
        var value = q.multiply(TradePolicy.money(v.unitCost)).setScale(2, RoundingMode.HALF_UP);
        TradePolicy.money(value);
        change(s, q, value);
        movement(
            p.id,
            w.id,
            w.departmentId,
            null,
            null,
            null,
            "OPENING",
            q,
            value,
            BigDecimal.ZERO,
            reference,
            note);
      }
      case "count" -> {
        if (v.expectedQuantity == null || s.quantity.compareTo(v.expectedQuantity) != 0)
          throw new Problem(409, "STALE_STOCK");
        var delta = q.subtract(s.quantity);
        if (delta.signum() == 0) throw new Problem(400, "NO_DIFFERENCE");
        var value =
            delta.signum() > 0
                ? delta.multiply(TradePolicy.money(v.unitCost)).setScale(2, RoundingMode.HALF_UP)
                : TradePolicy.cost(s.quantity, s.value, delta.abs()).negate();
        TradePolicy.money(value.abs());
        change(s, delta, value);
        movement(
            p.id,
            w.id,
            w.departmentId,
            null,
            null,
            null,
            "COUNT",
            delta,
            value,
            BigDecimal.ZERO,
            reference,
            note);
      }
      case "transfer" -> {
        var dest = db.get(Warehouse.class, v.destinationId);
        access.department(dest.departmentId);
        if (!dest.enabled || dest.id.equals(w.id) || !dest.departmentId.equals(w.departmentId))
          throw new Problem(400, "INVALID_DESTINATION");
        var value = TradePolicy.cost(s.quantity, s.value, q);
        change(s, q.negate(), value.negate());
        var other = stock(p.id, dest.id);
        change(other, q, value);
        var original =
            movement(
                p.id,
                w.id,
                w.departmentId,
                null,
                null,
                null,
                "TRANSFER_OUT",
                q.negate(),
                value.negate(),
                BigDecimal.ZERO,
                reference,
                note);
        movement(
            p.id,
            dest.id,
            dest.departmentId,
            null,
            null,
            original.id,
            "TRANSFER_IN",
            q,
            value,
            BigDecimal.ZERO,
            reference,
            note);
      }
    }
    access.audit("STOCK_" + action.toUpperCase(Locale.ROOT), p.id, w.departmentId);
    return stockList();
  }

  /** 按仓库归属限制库存响应，不泄漏其他部门结存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<StockBalance> stockList() {
    access.require("stock.read");
    return db.all(StockBalance.class).stream()
        .filter(s -> access.visible(db.get(Warehouse.class, s.warehouseId).departmentId))
        .toList();
  }

  /** 读取授权范围内原始库存流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<StockMovement> movements() {
    access.require("stock.read");
    return db.all(StockMovement.class).stream()
        .filter(m -> access.visible(m.departmentId))
        .toList();
  }

  /** 单据查询必须先通过业务权限与部门检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public TradeOrder authorized(Long id) {
    var d = db.get(TradeOrder.class, id);
    access.require(permission(d.kind) + ".read");
    access.department(d.departmentId);
    return d;
  }

  /** 固定单据行查询，参数绑定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<OrderLine> lines(Long id) {
    return db.query(OrderLine.class, "from OrderLine where orderId=?1 order by id", id);
  }

  /** 返回单据、行、货物及资金凭证与余额，负余额表示待退款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> view(TradeOrder d) {
    return Map.of(
        "order",
        d,
        "lines",
        lines(d.id),
        "movements",
        db.query(StockMovement.class, "from StockMovement where orderId=?1 order by id", d.id),
        "payments",
        db.query(PaymentEntry.class, "from PaymentEntry where orderId=?1 order by id", d.id),
        "balance",
        d.netAmount.subtract(d.netPaid),
        "grossMargin",
        d.netAmount.subtract(d.netCost));
  }

  private StockBalance stock(Long product, Long warehouse) {
    var rows =
        db.query(
            StockBalance.class,
            "from StockBalance where productId=?1 and warehouseId=?2",
            product,
            warehouse);
    if (!rows.isEmpty()) return rows.getFirst();
    var s = new StockBalance();
    s.productId = product;
    s.warehouseId = warehouse;
    return db.save(s);
  }

  private void change(StockBalance s, BigDecimal q, BigDecimal value) {
    s.quantity = s.quantity.add(q);
    s.value = s.value.add(value);
    if (s.quantity.signum() < 0 || s.value.signum() < 0)
      throw new Problem(409, "INSUFFICIENT_STOCK");
    TradePolicy.money(s.value);
  }

  private StockMovement movement(
      Long p,
      Long w,
      Long dept,
      Long order,
      Long line,
      Long source,
      String kind,
      BigDecimal q,
      BigDecimal value,
      BigDecimal amount,
      String ref,
      String note) {
    var m = new StockMovement();
    m.productId = p;
    m.warehouseId = w;
    m.departmentId = dept;
    m.orderId = order;
    m.lineId = line;
    m.sourceId = source;
    m.kind = kind;
    m.quantity = q;
    m.value = value;
    m.amount = amount;
    m.reference = ref;
    m.note = note;
    m.createdAt = clock.instant();
    m.createdBy = access.current().username;
    return db.save(m);
  }

  private void active(TradeOrder d) {
    if (!Set.of("CONFIRMED", "PARTIAL", "FULFILLED").contains(d.status))
      throw new Problem(409, "INVALID_STATE");
  }

  private String permission(String kind) {
    return kind.equals("SALES") ? "sales" : "purchase";
  }

  /** 获取经过管理员限制的单币种与显示参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }

  private boolean repeated(String resource, String key, String input) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{12,80}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    var old =
        db.query(
            MutationStamp.class,
            "from MutationStamp where resource=?1 and requestKey=?2",
            resource,
            key);
    if (!old.isEmpty()) {
      if (!old.getFirst().fingerprint.equals(hash)) throw new Problem(409, "KEY_REUSED");
      return true;
    }
    var m = new MutationStamp();
    m.resource = resource;
    m.requestKey = key;
    m.fingerprint = hash;
    db.save(m);
    return false;
  }
}

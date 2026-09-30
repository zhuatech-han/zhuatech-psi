// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import java.math.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 初始化账号、岗位权限、导航和可选虚构档案，重启不覆盖已有数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;
  final boolean demo;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${psi.admin-username}") String username,
      @Value("${psi.admin-password}") String password,
      @Value("${psi.seed-demo}") boolean demo) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
    this.demo = demo;
  }

  /** 首次空库初始化；管理员密码必须由环境提供。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_USERNAME");
    var dept = new Department();
    dept.name = "总部 / Main office";
    db.save(dept);
    String[][] perms = {
      {"dashboard", "工作台 / Overview"},
      {"master.read", "查看主数据 / View masters"},
      {"master.write", "维护主数据 / Edit masters"},
      {"purchase.read", "查看采购 / View purchasing"},
      {"purchase.write", "采购开单 / Manage purchasing"},
      {"sales.read", "查看销售 / View sales"},
      {"sales.write", "销售开单 / Manage sales"},
      {"stock.read", "查看库存 / View stock"},
      {"stock.write", "收发货与盘点 / Post stock"},
      {"finance", "收付款与价格调整 / Finance"},
      {"report", "报表与导出 / Reports"},
      {"audit", "操作审计 / Audit"},
      {"admin", "系统管理 / Administration"}
    };
    var all = new HashSet<String>();
    for (var p : perms) {
      var e = new Permission();
      e.code = p[0];
      e.name = p[1];
      db.save(e);
      all.add(p[0]);
    }
    var admin = role("管理员 / Administrator", "ALL", all);
    role(
        "采购 / Purchasing",
        "DEPARTMENT",
        Set.of("dashboard", "master.read", "purchase.read", "purchase.write", "stock.read"));
    role(
        "销售 / Sales",
        "DEPARTMENT",
        Set.of("dashboard", "master.read", "sales.read", "sales.write", "stock.read"));
    role(
        "仓管 / Warehouse operator",
        "DEPARTMENT",
        Set.of(
            "dashboard",
            "master.read",
            "purchase.read",
            "sales.read",
            "stock.read",
            "stock.write"));
    role(
        "财务 / Finance",
        "DEPARTMENT",
        Set.of(
            "dashboard",
            "master.read",
            "purchase.read",
            "sales.read",
            "finance",
            "report",
            "stock.read"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.departmentId = dept.id;
    a.roleId = admin.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "工作台", "Overview", "dashboard"},
      {"sales", "销售订单", "Sales orders", "sales.read"},
      {"purchase", "采购订单", "Purchase orders", "purchase.read"},
      {"stock", "库存与流水", "Stock & ledger", "stock.read"},
      {"finance", "往来对账", "Receivables & payables", "finance"},
      {"reports", "经营报表", "Reports", "report"},
      {"products", "商品", "Products", "master.read"},
      {"parties", "客户与供应商", "Customers & suppliers", "master.read"},
      {"warehouses", "仓库", "Warehouses", "master.read"},
      {"categories", "商品分类", "Categories", "master.read"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles & permissions", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "菜单管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
      {"settings", "系统参数", "Settings", "admin"},
      {"audit", "操作审计", "Audit trail", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of("currency", "CNY", "timezone", "Asia/Shanghai", "companyName", "知华商贸进销存")
            .entrySet()) {
      var p = new SystemSetting();
      p.code = e.getKey();
      p.value = e.getValue();
      db.save(p);
    }
    for (var e : new String[][] {{"bank", "银行转账", "Bank transfer"}, {"cash", "现金", "Cash"}}) {
      var d = new DictionaryEntry();
      d.type = "payment_method";
      d.code = e[0];
      d.name = e[1];
      d.nameEn = e[2];
      db.save(d);
    }
    if (demo) {
      var c = new Category();
      c.name = "五金耗材";
      c.nameEn = "Hardware & supplies";
      db.save(c);
      for (var k : new String[] {"CUSTOMER", "SUPPLIER"}) {
        var p = new Party();
        p.kind = k;
        p.code = k + "-DEMO";
        p.name = k.equals("CUSTOMER") ? "示例批发客户 / Demo customer" : "示例供货商 / Demo supplier";
        p.contact = "demo@example.invalid";
        p.notes = "虚构学习数据 / Fictional learning data";
        p.departmentId = dept.id;
        db.save(p);
      }
      for (int i = 1; i <= 2; i++) {
        var w = new Warehouse();
        w.name = i == 1 ? "主仓 / Main warehouse" : "备用仓 / Secondary warehouse";
        w.code = "WH-0" + i;
        w.departmentId = dept.id;
        db.save(w);
      }
      String[][] products = {
        {"HW-001", "紧固件套装 / Fastener kit", "盒 / box", "10", "15"},
        {"HW-002", "防护手套 / Work gloves", "双 / pair", "6", "12"},
        {"HW-003", "工业胶带 / Industrial tape", "卷 / roll", "8", "16"}
      };
      for (var p : products) {
        var e = new Product();
        e.code = p[0];
        e.barcode = "DEMO-" + p[0];
        e.name = p[1];
        e.unit = p[2];
        e.categoryId = c.id;
        e.departmentId = dept.id;
        e.purchasePrice = new BigDecimal(p[3]);
        e.salePrice = new BigDecimal(p[4]);
        e.reorderLevel = new BigDecimal("10");
        db.save(e);
      }
    }
  }

  private AccessRole role(String name, String scope, Set<String> perms) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(perms);
    return db.save(r);
  }
}

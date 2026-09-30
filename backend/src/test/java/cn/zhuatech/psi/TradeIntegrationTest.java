// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.psi;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 HTTP 登录、单据过账、库存资金一致性、隔离与并发集成测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
class TradeIntegrationTest {
  static final String PASSWORD = "Test" + UUID.randomUUID() + "Aa9";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:psi;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("psi.admin-password", () -> PASSWORD);
    r.add("psi.seed-demo", () -> false);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession session;
  Long customer, supplier, warehouse, product, category;
  String suffix;

  @BeforeEach
  void setup() throws Exception {
    session = login("admin", PASSWORD);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    category =
        postOk("/master/categories", Map.of("name", "Category" + suffix, "nameEn", "Category"))
            .get("id")
            .asLong();
    warehouse =
        postOk(
                "/master/warehouses",
                Map.of(
                    "code",
                    "W" + suffix,
                    "name",
                    "Warehouse" + suffix,
                    "departmentId",
                    1,
                    "enabled",
                    true))
            .get("id")
            .asLong();
    customer = party("CUSTOMER", 1);
    supplier = party("SUPPLIER", 1);
    product = product("P" + suffix, 1);
  }

  Long party(String kind, long dept) throws Exception {
    return postOk(
            "/master/parties",
            Map.of(
                "code",
                kind + UUID.randomUUID(),
                "name",
                kind + suffix,
                "kind",
                kind,
                "departmentId",
                dept,
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  Long product(String code, long dept) throws Exception {
    return postOk(
            "/master/products",
            Map.of(
                "code",
                code,
                "name",
                "Product" + code,
                "unit",
                "unit",
                "categoryId",
                category,
                "departmentId",
                dept,
                "salePrice",
                "15.00",
                "purchasePrice",
                "10.00",
                "reorderLevel",
                "5.000",
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  MockHttpSession login(String name, String password) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", name, "password", password))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  JsonNode send(String path, Object body, int expected) throws Exception {
    return sendAs(session, path, body, expected);
  }

  JsonNode sendAs(MockHttpSession s, String path, Object body, int expected) throws Exception {
    var r =
        mvc.perform(
                post("/api" + path)
                    .session(s)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andReturn()
            .getResponse();
    assertEquals(expected, r.getStatus(), r.getContentAsString());
    return json.readTree(r.getContentAsString());
  }

  JsonNode getOk(String path) throws Exception {
    var r = mvc.perform(get("/api" + path).session(session)).andReturn().getResponse();
    assertEquals(200, r.getStatus(), r.getContentAsString());
    return json.readTree(r.getContentAsString());
  }

  JsonNode postOk(String path, Object body) throws Exception {
    return send(path, body, 200);
  }

  Map<String, Object> command() {
    var m = new HashMap<String, Object>();
    m.put("requestKey", UUID.randomUUID().toString());
    m.put("reference", "REF-" + suffix);
    m.put("note", "Test verification");
    return m;
  }

  JsonNode order(String kind, String quantity, String price) throws Exception {
    return postOk(
        "/orders",
        Map.of(
            "kind",
            kind,
            "partyId",
            kind.equals("SALES") ? customer : supplier,
            "warehouseId",
            warehouse,
            "lines",
            List.of(Map.of("productId", product, "quantity", quantity, "price", price))));
  }

  JsonNode confirm(JsonNode d) throws Exception {
    return postOk("/orders/" + id(d) + "/confirm", command());
  }

  long id(JsonNode d) {
    return d.get("order").get("id").asLong();
  }

  JsonNode postGoods(JsonNode d, String q) throws Exception {
    var m = command();
    m.put(
        "lines",
        List.of(Map.of("lineId", d.get("lines").get(0).get("id").asLong(), "quantity", q)));
    return postOk("/orders/" + id(d) + "/post", m);
  }

  JsonNode pay(JsonNode d, String amount, String kind) throws Exception {
    var m = command();
    m.put("amount", amount);
    return postOk("/orders/" + id(d) + "/" + kind, m);
  }

  JsonNode returned(JsonNode d, long source, String q) throws Exception {
    var m = command();
    m.put("sourceId", source);
    m.put("quantity", q);
    return postOk("/orders/" + id(d) + "/return", m);
  }

  void opening(String q) throws Exception {
    var m = command();
    m.put("productId", product);
    m.put("warehouseId", warehouse);
    m.put("quantity", q);
    m.put("unitCost", "10.00");
    postOk("/stock/opening", m);
  }

  void amount(JsonNode n, String field, String expected) {
    assertEquals(
        0, new BigDecimal(expected).compareTo(new BigDecimal(n.get(field).asText())), field);
  }

  JsonNode balance(long wh) throws Exception {
    for (var s : getOk("/lists/stock?size=100").get("items")) {
      if (s.get("warehouseId").asLong() == wh && s.get("productId").asLong() == product) return s;
    }
    throw new AssertionError("Missing balance");
  }

  @Test
  void completePartialPurchaseSalesReturnAndCollection() throws Exception {
    var buy = confirm(order("PURCHASE", "100", "10.00"));
    buy = postGoods(buy, "60");
    assertEquals("PARTIAL", buy.get("order").get("status").asText());
    buy = postGoods(buy, "40");
    buy = pay(buy, "600.00", "pay");
    amount(buy, "balance", "400");
    var sale = confirm(order("SALES", "30", "15.00"));
    sale = postGoods(sale, "20");
    long source = sale.get("movements").get(0).get("id").asLong();
    sale = postGoods(sale, "10");
    sale = returned(sale, source, "2");
    sale = pay(sale, "200.00", "pay");
    amount(sale.get("order"), "netAmount", "420");
    amount(sale.get("order"), "netCost", "280");
    amount(sale, "balance", "220");
    amount(sale, "grossMargin", "140");
    amount(balance(warehouse), "quantity", "72");
    amount(balance(warehouse), "value", "720");
  }

  @Test
  void confirmedOrderDoesNotCreateReceivableOrStock() throws Exception {
    var d = confirm(order("PURCHASE", "100", "10"));
    amount(d.get("order"), "netAmount", "0");
    assertEquals(0, d.get("movements").size());
  }

  @Test
  void paidSaleReturnCreatesRefundableBalance() throws Exception {
    opening("10");
    var d = postGoods(confirm(order("SALES", "10", "15")), "10");
    d = pay(d, "150", "pay");
    d = returned(d, d.get("movements").get(0).get("id").asLong(), "2");
    amount(d, "balance", "-30");
    d = pay(d, "30", "refund");
    amount(d, "balance", "0");
    amount(balance(warehouse), "quantity", "2");
  }

  @Test
  void purchaseReturnReducesPayable() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "10", "10")), "10");
    d = returned(d, d.get("movements").get(0).get("id").asLong(), "2");
    amount(d, "balance", "80");
    amount(balance(warehouse), "value", "80");
  }

  @Test
  void repeatedShipmentIsExactlyOnce() throws Exception {
    opening("10");
    var d = confirm(order("SALES", "10", "15"));
    var v = command();
    v.put(
        "lines",
        List.of(Map.of("lineId", d.get("lines").get(0).get("id").asLong(), "quantity", "10")));
    postOk("/orders/" + id(d) + "/post", v);
    var second = postOk("/orders/" + id(d) + "/post", v);
    assertEquals(1, second.get("movements").size());
    amount(balance(warehouse), "quantity", "0");
  }

  @Test
  void repeatedPaymentIsExactlyOnce() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "10", "10")), "10");
    var v = command();
    v.put("amount", "50");
    postOk("/orders/" + id(d) + "/pay", v);
    var second = postOk("/orders/" + id(d) + "/pay", v);
    assertEquals(1, second.get("payments").size());
    amount(second, "balance", "50");
  }

  @Test
  void changedIdempotencyPayloadRejected() throws Exception {
    var d = confirm(order("PURCHASE", "10", "10"));
    var v = command();
    v.put(
        "lines",
        List.of(Map.of("lineId", d.get("lines").get(0).get("id").asLong(), "quantity", "5")));
    postOk("/orders/" + id(d) + "/post", v);
    v.put(
        "lines",
        List.of(Map.of("lineId", d.get("lines").get(0).get("id").asLong(), "quantity", "1")));
    send("/orders/" + id(d) + "/post", v, 409);
  }

  @Test
  void returnCannotExceedOriginalShipment() throws Exception {
    opening("10");
    var d = postGoods(confirm(order("SALES", "10", "15")), "10");
    var v = command();
    v.put("sourceId", d.get("movements").get(0).get("id").asLong());
    v.put("quantity", "11");
    send("/orders/" + id(d) + "/return", v, 409);
    amount(balance(warehouse), "quantity", "0");
  }

  @Test
  void returnCannotReferenceOtherOrder() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "10", "10")), "10");
    var other = confirm(order("PURCHASE", "2", "10"));
    var v = command();
    v.put("sourceId", d.get("movements").get(0).get("id").asLong());
    v.put("quantity", "1");
    send("/orders/" + id(other) + "/return", v, 400);
  }

  @Test
  void paymentCannotExceedBalance() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "1", "10")), "1");
    var v = command();
    v.put("amount", "11");
    send("/orders/" + id(d) + "/pay", v, 409);
  }

  @Test
  void confirmedOrderCannotEditOrDelete() throws Exception {
    var d = confirm(order("PURCHASE", "10", "10"));
    var response =
        mvc.perform(
                put("/api/orders/" + id(d))
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "kind",
                                "PURCHASE",
                                "partyId",
                                supplier,
                                "warehouseId",
                                warehouse,
                                "lines",
                                List.of(
                                    Map.of(
                                        "productId", product, "quantity", "5", "price", "10"))))))
            .andReturn()
            .getResponse();
    assertEquals(409, response.getStatus());
    assertEquals(
        409,
        mvc.perform(delete("/api/orders/" + id(d)).session(session).with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void fulfilledOrderCannotCancel() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "1", "10")), "1");
    send("/orders/" + id(d) + "/cancel", command(), 409);
  }

  @Test
  void cancelledOrderCannotPost() throws Exception {
    var d = confirm(order("PURCHASE", "1", "10"));
    postOk("/orders/" + id(d) + "/cancel", command());
    var v = command();
    v.put(
        "lines",
        List.of(Map.of("lineId", d.get("lines").get(0).get("id").asLong(), "quantity", "1")));
    send("/orders/" + id(d) + "/post", v, 409);
  }

  @Test
  void transferKeepsQuantityAndValue() throws Exception {
    opening("10");
    long wh2 =
        postOk(
                "/master/warehouses",
                Map.of(
                    "code",
                    "W2" + suffix,
                    "name",
                    "Second" + suffix,
                    "departmentId",
                    1,
                    "enabled",
                    true))
            .get("id")
            .asLong();
    var v = command();
    v.put("productId", product);
    v.put("warehouseId", warehouse);
    v.put("destinationId", wh2);
    v.put("quantity", "4");
    postOk("/stock/transfer", v);
    amount(balance(warehouse), "quantity", "6");
    amount(balance(wh2), "value", "40");
  }

  @Test
  void staleCountRejected() throws Exception {
    opening("10");
    var v = command();
    v.put("productId", product);
    v.put("warehouseId", warehouse);
    v.put("quantity", "8");
    v.put("expectedQuantity", "9");
    send("/stock/count", v, 409);
    amount(balance(warehouse), "quantity", "10");
  }

  @Test
  void stockCountRecordsDifferenceOnly() throws Exception {
    opening("10");
    var v = command();
    v.put("productId", product);
    v.put("warehouseId", warehouse);
    v.put("quantity", "8");
    v.put("expectedQuantity", "10");
    postOk("/stock/count", v);
    amount(balance(warehouse), "quantity", "8");
    amount(balance(warehouse), "value", "80");
  }

  @Test
  void secondOpeningRejected() throws Exception {
    opening("10");
    var v = command();
    v.put("productId", product);
    v.put("warehouseId", warehouse);
    v.put("quantity", "8");
    v.put("unitCost", "10");
    send("/stock/opening", v, 409);
  }

  @Test
  void precisionRejectedBeforeDatabaseRounding() throws Exception {
    send(
        "/orders",
        Map.of(
            "kind",
            "SALES",
            "partyId",
            customer,
            "warehouseId",
            warehouse,
            "lines",
            List.of(Map.of("productId", product, "quantity", "0.0001", "price", "10"))),
        400);
  }

  @Test
  void concurrentSalesCannotOversell() throws Exception {
    opening("5");
    var a = confirm(order("SALES", "5", "15"));
    var b = confirm(order("SALES", "5", "15"));
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      var tasks = new ArrayList<Future<Integer>>();
      for (var d : List.of(a, b)) {
        tasks.add(
            pool.submit(
                () -> {
                  gate.await();
                  var v = command();
                  v.put(
                      "lines",
                      List.of(
                          Map.of(
                              "lineId",
                              d.get("lines").get(0).get("id").asLong(),
                              "quantity",
                              "5")));
                  return mvc.perform(
                          post("/api/orders/" + id(d) + "/post")
                              .session(session)
                              .with(csrf())
                              .contentType("application/json")
                              .content(json.writeValueAsString(v)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      gate.countDown();
      var statuses = new ArrayList<Integer>();
      for (var t : tasks) statuses.add(t.get(15, TimeUnit.SECONDS));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      amount(balance(warehouse), "quantity", "0");
    } finally {
      pool.shutdownNow();
    }
  }

  MockHttpSession restricted(Set<String> perms, long dept) throws Exception {
    var r =
        postOk(
            "/admin/roles",
            Map.of(
                "name", "Role" + UUID.randomUUID(), "scope", "DEPARTMENT", "permissions", perms));
    var account =
        postOk(
            "/admin/users",
            Map.of(
                "username",
                "u" + suffix,
                "displayName",
                "Test",
                "password",
                PASSWORD,
                "roleId",
                r.get("id").asLong(),
                "departmentId",
                dept,
                "enabled",
                true));
    assertNull(account.get("passwordHash"));
    return login("u" + suffix, PASSWORD);
  }

  @Test
  void warehouseCannotRecordMoney() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "1", "10")), "1");
    var staff = restricted(Set.of("purchase.read", "stock.read", "stock.write"), 1);
    var v = command();
    v.put("amount", "1");
    sendAs(staff, "/orders/" + id(d) + "/pay", v, 403);
  }

  @Test
  void departmentIsolationAppliesToDetailListAndExport() throws Exception {
    long dept = postOk("/admin/departments", Map.of("name", "Other" + suffix)).get("id").asLong();
    var d = confirm(order("PURCHASE", "1", "10"));
    var staff = restricted(Set.of("purchase.read", "stock.read", "report", "master.read"), dept);
    assertEquals(
        403,
        mvc.perform(get("/api/orders/" + id(d)).session(staff))
            .andReturn()
            .getResponse()
            .getStatus());
    var list = mvc.perform(get("/api/lists/purchase").session(staff)).andReturn().getResponse();
    assertEquals(0, json.readTree(list.getContentAsString()).get("total").asInt());
    var csv = mvc.perform(get("/api/reports.csv").session(staff)).andReturn().getResponse();
    assertEquals(200, csv.getStatus());
    assertFalse(csv.getContentAsString().contains(d.get("order").get("number").asText()));
  }

  @Test
  void anonymousAndMissingCsrfDenied() throws Exception {
    assertEquals(401, mvc.perform(get("/api/lists/users")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/orders").session(session).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void referencedProductProtected() throws Exception {
    order("PURCHASE", "1", "10");
    assertEquals(
        409,
        mvc.perform(delete("/api/master/products/" + product).session(session).with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void currencyChangeAfterOrdersRejected() throws Exception {
    order("PURCHASE", "1", "10");
    long id = 0;
    for (var s : getOk("/lists/settings").get("items"))
      if (s.get("code").asText().equals("currency")) id = s.get("id").asLong();
    assertEquals(
        409,
        mvc.perform(
                put("/api/admin/settings/" + id)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"value\":\"USD\"}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void barcodeLookupExactAndScoped() throws Exception {
    var p = getOk("/barcode?value=P" + suffix);
    assertEquals(product, p.get("id").asLong());
    assertEquals(
        404,
        mvc.perform(get("/api/barcode?value=does-not-exist").session(session))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void mixedAverageCostAndReturnUseOriginalShipmentCost() throws Exception {
    opening("10");
    var purchase = postGoods(confirm(order("PURCHASE", "10", "20")), "10");
    assertNotNull(purchase);
    var sale = postGoods(confirm(order("SALES", "4", "30")), "4");
    amount(sale.get("order"), "netCost", "60");
    sale = returned(sale, sale.get("movements").get(0).get("id").asLong(), "1");
    amount(sale.get("order"), "netCost", "45");
    amount(balance(warehouse), "value", "255");
  }

  @Test
  void invalidImportRollsBackWholeBatch() throws Exception {
    String c = "Import" + suffix;
    send(
        "/master/products/import",
        List.of(
            Map.of(
                "code",
                c,
                "name",
                c,
                "unit",
                "unit",
                "categoryId",
                category,
                "departmentId",
                1,
                "salePrice",
                "1",
                "purchasePrice",
                "1",
                "reorderLevel",
                "0",
                "enabled",
                true),
            Map.of("code", "invalid")),
        400);
    assertEquals(0, getOk("/lists/products?search=" + c).get("total").asInt());
  }

  @Test
  void draftCanBeUpdatedWithoutMovingStock() throws Exception {
    var d = order("PURCHASE", "10", "10");
    var response =
        mvc.perform(
                put("/api/orders/" + id(d))
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "kind",
                                "PURCHASE",
                                "partyId",
                                supplier,
                                "warehouseId",
                                warehouse,
                                "lines",
                                List.of(
                                    Map.of(
                                        "productId", product, "quantity", "12", "price", "10"))))))
            .andReturn()
            .getResponse();
    assertEquals(200, response.getStatus(), response.getContentAsString());
    amount(json.readTree(response.getContentAsString()).get("lines").get(0), "quantity", "12");
  }

  @Test
  void priceCreditThenReturnDoesNotDuplicateRefund() throws Exception {
    opening("10");
    var d = postGoods(confirm(order("SALES", "10", "15")), "10");
    d = pay(d, "150", "pay");
    long source = d.get("movements").get(0).get("id").asLong();
    var v = command();
    v.put("sourceId", source);
    v.put("amount", "10");
    d = postOk("/orders/" + id(d) + "/credit", v);
    amount(d, "balance", "-10");
    d = returned(d, source, "10");
    amount(d.get("order"), "netAmount", "0");
    amount(d.get("order"), "netCost", "0");
    amount(d, "balance", "-150");
  }

  @Test
  void paymentCorrectionRetainsOriginalVoucher() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "10", "10")), "10");
    d = pay(d, "50", "pay");
    long payment = d.get("payments").get(0).get("id").asLong();
    var v = command();
    v.put("sourceId", payment);
    d = postOk("/orders/" + id(d) + "/reversePayment", v);
    assertEquals(2, d.get("payments").size());
    amount(d, "balance", "100");
    assertEquals(payment, d.get("payments").get(1).get("reversalOf").asLong());
    v = command();
    v.put("sourceId", payment);
    send("/orders/" + id(d) + "/reversePayment", v, 409);
  }

  @Test
  void statementRunningBalanceMatchesOrder() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "10", "10")), "10");
    d = pay(d, "50", "pay");
    var s = getOk("/statement?partyId=" + supplier);
    amount(s, "balance", "50");
    assertEquals(2, s.get("entries").size());
    amount(s.get("entries").get(0), "balance", "100");
  }

  @Test
  void nullKindAndNullRoleScopeReturnValidationError() throws Exception {
    send("/orders", Map.of("partyId", customer, "warehouseId", warehouse, "lines", List.of()), 400);
    send("/admin/roles", Map.of("name", "Invalid" + suffix, "permissions", List.of()), 400);
  }

  @Test
  void lastAdminCannotBeDisabled() throws Exception {
    var admin = getOk("/lists/users?search=admin").get("items").get(0);
    var payload =
        Map.of(
            "username",
            "admin",
            "displayName",
            "Administrator",
            "roleId",
            admin.get("roleId").asLong(),
            "departmentId",
            1,
            "enabled",
            false);
    var r =
        mvc.perform(
                put("/api/admin/users/" + admin.get("id").asLong())
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(payload)))
            .andReturn()
            .getResponse();
    assertEquals(409, r.getStatus());
    assertEquals("admin", getOk("/auth/me").get("username").asText());
  }

  @Test
  void disabledAccountSessionStopsImmediately() throws Exception {
    var staff = restricted(Set.of("master.read"), 1);
    var account = getOk("/lists/users?search=u" + suffix).get("items").get(0);
    var payload =
        Map.of(
            "username",
            "u" + suffix,
            "displayName",
            "Test",
            "roleId",
            account.get("roleId").asLong(),
            "departmentId",
            1,
            "enabled",
            false);
    assertEquals(
        200,
        mvc.perform(
                put("/api/admin/users/" + account.get("id").asLong())
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(payload)))
            .andReturn()
            .getResponse()
            .getStatus());
    assertEquals(
        401, mvc.perform(get("/api/catalog").session(staff)).andReturn().getResponse().getStatus());
  }

  @Test
  void failedMultiLinePostingRollsBackEveryLineAndRequestKey() throws Exception {
    opening("10");
    long second = product("Empty" + suffix, 1);
    var d =
        postOk(
            "/orders",
            Map.of(
                "kind",
                "SALES",
                "partyId",
                customer,
                "warehouseId",
                warehouse,
                "lines",
                List.of(
                    Map.of("productId", product, "quantity", "5", "price", "15"),
                    Map.of("productId", second, "quantity", "1", "price", "10"))));
    d = confirm(d);
    var v = command();
    v.put(
        "lines",
        List.of(
            Map.of("lineId", d.get("lines").get(0).get("id").asLong(), "quantity", "5"),
            Map.of("lineId", d.get("lines").get(1).get("id").asLong(), "quantity", "1")));
    send("/orders/" + id(d) + "/post", v, 409);
    amount(balance(warehouse), "quantity", "10");
    amount(getOk("/orders/" + id(d)).get("order"), "netAmount", "0");
    var init = command();
    init.put("productId", second);
    init.put("warehouseId", warehouse);
    init.put("quantity", "1");
    init.put("unitCost", "10");
    postOk("/stock/opening", init);
    postOk("/orders/" + id(d) + "/post", v);
    amount(balance(warehouse), "quantity", "5");
  }

  @Test
  void spreadsheetExportsContainExactReconciliationValues() throws Exception {
    var d = postGoods(confirm(order("PURCHASE", "10", "10")), "10");
    d = pay(d, "50", "pay");
    var csv = mvc.perform(get("/api/reports.csv").session(session)).andReturn().getResponse();
    assertEquals(200, csv.getStatus());
    assertTrue(csv.getContentAsString().contains(d.get("order").get("number").asText()));
    assertTrue(csv.getContentAsString().contains("\"50.00\""));
    var statement =
        mvc.perform(get("/api/statement.csv?partyId=" + supplier).session(session))
            .andReturn()
            .getResponse();
    assertEquals(200, statement.getStatus());
    assertFalse(statement.getContentAsString().contains("zhuatech"));
  }

  @Test
  void scopedRoleCannotGrantGlobalAdministration() throws Exception {
    send(
        "/admin/roles",
        Map.of(
            "name", "ScopedAdmin" + suffix, "scope", "DEPARTMENT", "permissions", List.of("admin")),
        400);
  }
}

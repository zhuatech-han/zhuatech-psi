<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import {
  Package,
  LayoutDashboard,
  ShoppingCart,
  Truck,
  Warehouse,
  ReceiptText,
  Users,
  Settings,
  ChevronRight,
  Plus,
  Search,
  LogOut,
  RefreshCw,
  Download,
  Printer,
  X,
  Menu,
  ArrowLeft,
  Check,
} from "@lucide/vue";
import { api, resetCsrf, downloadReport } from "./api.js";
import { money, quantity, decimal } from "./format.js";
const lang = ref(localStorage.getItem("psi-language") || "zh");
const L = (zh, en) => (lang.value === "en" ? en : zh);
const user = ref(null),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  page = ref("dashboard"),
  mobileNav = ref(false),
  catalog = ref({}),
  rows = ref([]),
  total = ref(0),
  index = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("id"),
  desc = ref(true),
  dash = ref({}),
  reports = ref({}),
  stockTab = ref("stock"),
  detail = ref(null),
  dialog = ref(null),
  form = ref({}),
  editing = ref(null),
  barcode = ref(""),
  actionData = ref({});
const statementData = ref(null);
const statementParty = ref("");
const login = ref({ username: "admin", password: "" });
const can = (p) => user.value?.permissions.includes(p);
const masterPages = ["products", "parties", "warehouses", "categories"];
const adminPages = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const settings = computed(() =>
  Object.fromEntries(
    (catalog.value.settings || []).map((s) => [s.code, s.value]),
  ),
);
const currency = computed(() => settings.value.currency || "CNY");
const cash = (v, c = currency.value) => money(v, c, lang.value);
const qty = quantity;
const title = computed(() => {
  const m = user.value?.menus.find((m) => m.code === page.value);
  return m ? (lang.value === "en" ? m.nameEn : m.name) : L("关于系统", "About");
});
const isOrders = computed(() =>
  ["sales", "purchase", "finance"].includes(page.value),
);
const isMaster = computed(() => masterPages.includes(page.value));
const isAdmin = computed(() => adminPages.includes(page.value));
const menus = computed(() => user.value?.menus || []);
const resource = computed(() =>
  page.value === "stock" ? stockTab.value : page.value,
);
const kindLabel = (k) =>
  ({
    SALES: L("销售", "Sales"),
    PURCHASE: L("采购", "Purchase"),
    CUSTOMER: L("客户", "Customer"),
    SUPPLIER: L("供应商", "Supplier"),
    PAY: L("收付款", "Payment"),
    REFUND: L("退款", "Refund"),
    REVERSAL_PAY: L("收付款冲销", "Payment reversal"),
    REVERSAL_REFUND: L("退款冲销", "Refund reversal"),
  })[k] || k;
const stateLabel = (k) =>
  ({
    DRAFT: L("草稿", "Draft"),
    CONFIRMED: L("待收发货", "Confirmed"),
    PARTIAL: L("部分履约", "Partially fulfilled"),
    FULFILLED: L("全部履约", "Fulfilled"),
    CANCELLED: L("已作废", "Cancelled"),
  })[k] || k;
const movementLabel = (k) =>
  ({
    RECEIPT: L("采购入库", "Purchase receipt"),
    SHIPMENT: L("销售出库", "Sales shipment"),
    SALES_RETURN: L("销售退货", "Sales return"),
    PURCHASE_RETURN: L("采购退货", "Purchase return"),
    OPENING: L("期初入库", "Opening stock"),
    COUNT: L("盘点调整", "Stock count"),
    TRANSFER_OUT: L("调拨出库", "Transfer out"),
    TRANSFER_IN: L("调拨入库", "Transfer in"),
    PRICE_CREDIT: L("价格调整", "Price credit"),
  })[k] || k;
const errorLabels = {
  UNAUTHENTICATED: [
    "登录已失效，请重新登录",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect username or password."],
  FORBIDDEN: ["没有此操作权限", "You do not have permission."],
  OUT_OF_SCOPE: [
    "记录不属于你的部门",
    "This record is outside your department.",
  ],
  INVALID_STATE: [
    "当前状态不能执行此操作，请刷新核对",
    "This action is unavailable in the current state. Refresh to verify.",
  ],
  INSUFFICIENT_STOCK: [
    "库存不足，请核对仓库和数量",
    "Insufficient stock. Check warehouse and quantity.",
  ],
  EXCEEDS_QUANTITY: [
    "数量超过尚可履约或可退数量",
    "Quantity exceeds the remaining or returnable quantity.",
  ],
  EXCEEDS_BALANCE: [
    "金额超过未结或可退余额",
    "Amount exceeds the outstanding or refundable balance.",
  ],
  CONFLICT: [
    "编码重复或记录已被业务引用",
    "Duplicate code or a record referenced by existing transactions.",
  ],
  INVALID_INPUT: [
    "请核对必填项、格式和长度",
    "Check required fields, formats and lengths.",
  ],
  INVALID_MONEY: [
    "金额需非负、最多两位小数",
    "Amount must be nonnegative with at most two decimals.",
  ],
  INVALID_QUANTITY: [
    "数量需符合三位小数精度和范围",
    "Quantity must fit the three-decimal precision and range.",
  ],
  LAST_ADMIN: [
    "必须保留一名启用的全范围管理员",
    "Keep at least one enabled administrator with full scope.",
  ],
  WEAK_PASSWORD: [
    "密码需12–72位，包含大小写字母及数字",
    "Use 12–72 characters with uppercase, lowercase and numbers.",
  ],
  STALE_STOCK: [
    "库存已变化，请刷新后重新盘点",
    "Stock changed. Refresh and recount.",
  ],
  KEY_REUSED: [
    "这次提交已使用不同内容，请先核对实际结果",
    "This request was used with different content. Verify the recorded result.",
  ],
  OPENING_EXISTS: [
    "已有流水，不能再次录入期初",
    "Opening stock already exists; use stock count.",
  ],
  IMMUTABLE_FIELD: [
    "已建立档案的部门、类型或基本单位不能修改",
    "Department, type or base unit is immutable.",
  ],
  CURRENCY_LOCKED: [
    "已有单据，不能切换记账币种",
    "Currency is locked after the first order.",
  ],
  INVALID_MASTER: [
    "请使用同部门的启用档案",
    "Use enabled records from the same department.",
  ],
  NO_DIFFERENCE: [
    "盘点数量与账面一致，无需调整",
    "The counted and book quantities match.",
  ],
  NOT_FOUND: ["没有找到记录或条码", "Record or barcode not found."],
  CREDIT_ALREADY_APPLIED: [
    "已做价格调整，请核对原单可退金额",
    "A price credit was applied. Check the remaining return value.",
  ],
};
function message(e) {
  const v = errorLabels[e.message];
  return v
    ? L(...v)
    : L(
        "操作未完成，请核对输入或连接后重试",
        "Action failed. Check the input or connection and retry.",
      ) +
        " (" +
        e.message +
        ")";
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    error.value = message(e);
    if (e.message === "UNAUTHENTICATED") {
      user.value = null;
      resetCsrf();
    }
  } finally {
    busy.value = false;
  }
}
async function signIn() {
  await run(async () => {
    user.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    resetCsrf();
    await init();
  });
}
async function init() {
  catalog.value = await api("/catalog");
  if (!menus.value.some((m) => m.code === page.value))
    page.value = menus.value[0]?.code || "about";
  await load();
}
async function load() {
  loading.value = true;
  try {
    if (page.value === "dashboard") dash.value = await api("/dashboard");
    else if (page.value === "reports") reports.value = await api("/reports");
    else if (page.value !== "about") {
      const result = await api(
        `/lists/${resource.value}?search=${encodeURIComponent(search.value)}&status=${status.value}&page=${index.value}&size=20&sort=${sort.value}&desc=${desc.value}`,
      );
      rows.value = result.items;
      total.value = result.total;
    }
  } finally {
    loading.value = false;
  }
}
function navigate(p) {
  page.value = p;
  index.value = 0;
  search.value = "";
  status.value = "";
  detail.value = null;
  mobileNav.value = false;
  location.hash = p;
  run(load);
}
const icons = {
  dashboard: LayoutDashboard,
  sales: ShoppingCart,
  purchase: Truck,
  stock: Warehouse,
  finance: ReceiptText,
  reports: LayoutDashboard,
  products: Package,
  parties: Users,
};
function by(type, id) {
  return (catalog.value[type] || []).find((x) => x.id === id);
}
const name = (type, id) => by(type, id)?.name || "#" + id;
const departments = computed(() => catalog.value.departments || []);
const products = computed(() => catalog.value.products || []);
const parties = computed(() => catalog.value.parties || []);
const warehouses = computed(() => catalog.value.warehouses || []);
const option = (key, label, type = "text", extra = {}) => ({
  key,
  label,
  type,
  ...extra,
});
const fields = computed(() => {
  const department = option("departmentId", L("部门", "Department"), "select", {
    options: departments.value,
  });
  const enabled = option("enabled", L("启用", "Enabled"), "checkbox");
  const code = option("code", L("编码", "Code"));
  const display = option("name", L("名称", "Name"));
  const english = option("nameEn", L("英文名称", "English name"));
  const notes = option("notes", L("备注", "Notes"), "textarea", {
    optional: true,
  });
  switch (page.value) {
    case "products":
      return [
        code,
        display,
        option("barcode", L("条码", "Barcode"), "text", { optional: true }),
        option("specification", L("规格", "Specification"), "text", {
          optional: true,
        }),
        option("unit", L("基本单位", "Base unit")),
        option("categoryId", L("分类", "Category"), "select", {
          options: catalog.value.categories || [],
        }),
        department,
        option(
          "purchasePrice",
          L("参考进价", "Reference purchase price"),
          "number",
          { step: "0.01" },
        ),
        option("salePrice", L("参考售价", "Reference sale price"), "number", {
          step: "0.01",
        }),
        option(
          "reorderLevel",
          L("库存预警量", "Low stock threshold"),
          "number",
          { step: "0.001" },
        ),
        enabled,
      ];
    case "parties":
      return [
        code,
        display,
        option("kind", L("类型", "Type"), "select", {
          options: [
            { id: "CUSTOMER", name: L("客户", "Customer") },
            { id: "SUPPLIER", name: L("供应商", "Supplier") },
          ],
        }),
        option("contact", L("联系人或联系方式", "Contact"), "text", {
          optional: true,
        }),
        department,
        enabled,
        notes,
      ];
    case "warehouses":
      return [code, display, department, enabled];
    case "categories":
      return [display, english];
    case "users":
      return [
        option("username", L("登录账号", "Username")),
        option("displayName", L("姓名", "Display name")),
        option(
          "password",
          L(
            editing.value ? "重置密码（留空保留）" : "初始密码",
            editing.value
              ? "New password (blank keeps current)"
              : "Initial password",
          ),
          "password",
          { optional: !!editing.value },
        ),
        option("roleId", L("角色", "Role"), "select", {
          options: catalog.value.roles || [],
        }),
        department,
        enabled,
      ];
    case "roles":
      return [
        display,
        option("scope", L("数据范围", "Data scope"), "select", {
          options: [
            { id: "ALL", name: L("全部部门", "All departments") },
            { id: "DEPARTMENT", name: L("本部门", "Own department") },
          ],
        }),
        option("permissions", L("业务权限", "Permissions"), "permissions", {
          options: catalog.value.permissions || [],
        }),
      ];
    case "departments":
      return [display];
    case "permissions":
      return [display];
    case "menus":
      return [
        display,
        english,
        option(
          "permissionCode",
          L("可见权限", "Required permission"),
          "select",
          {
            options: (catalog.value.permissions || []).map((p) => ({
              id: p.code,
              name: p.name,
            })),
          },
        ),
        option("position", L("排序", "Position"), "number", { step: "1" }),
        enabled,
      ];
    case "dictionaries":
      return [
        option("type", L("字典类型", "Dictionary type")),
        code,
        display,
        english,
      ];
    case "settings":
      return [option("value", L("参数值", "Value"))];
    default:
      return [];
  }
});
const columns = computed(() => {
  if (page.value === "products")
    return [
      option("code", L("编码", "Code")),
      option("name", L("商品 / 规格", "Product / specification")),
      option("unit", L("基本单位", "Base unit")),
      option("categoryId", L("分类", "Category")),
      option("purchasePrice", L("参考进价", "Purchase price")),
      option("salePrice", L("参考售价", "Sale price")),
      option("enabled", L("状态", "Status")),
    ];
  if (page.value === "parties")
    return [
      option("code", L("编码", "Code")),
      option("name", L("名称", "Name")),
      option("kind", L("类型", "Type")),
      option("contact", L("联系方式", "Contact")),
      option("departmentId", L("部门", "Department")),
      option("enabled", L("状态", "Status")),
    ];
  if (page.value === "warehouses")
    return [
      option("code", L("编码", "Code")),
      option("name", L("仓库", "Warehouse")),
      option("departmentId", L("部门", "Department")),
      option("enabled", L("状态", "Status")),
    ];
  if (page.value === "stock")
    return stockTab.value === "stock"
      ? [
          option("productId", L("商品", "Product")),
          option("warehouseId", L("仓库", "Warehouse")),
          option("quantity", L("账面数量", "Book quantity")),
          option("value", L("库存价值", "Stock value")),
        ]
      : [
          option("createdAt", L("时间", "Time")),
          option("kind", L("业务", "Type")),
          option("productId", L("商品", "Product")),
          option("warehouseId", L("仓库", "Warehouse")),
          option("quantity", L("数量变动", "Quantity change")),
          option("value", L("价值变动", "Value change")),
          option("reference", L("凭证", "Reference")),
          option("createdBy", L("操作人", "Operator")),
        ];
  if (page.value === "audit")
    return [
      option("createdAt", L("时间", "Time")),
      option("actor", L("操作人", "Actor")),
      option("action", L("操作", "Action")),
      option("objectId", L("记录", "Record")),
      option("departmentId", L("部门", "Department")),
    ];
  if (page.value === "users")
    return [
      option("username", L("账号", "Username")),
      option("displayName", L("姓名", "Display name")),
      option("roleId", L("角色", "Role")),
      option("departmentId", L("部门", "Department")),
      option("enabled", L("状态", "Status")),
    ];
  if (page.value === "roles")
    return [
      option("name", L("角色", "Role")),
      option("scope", L("数据范围", "Scope")),
      option("permissions", L("权限数", "Permissions")),
    ];
  if (page.value === "settings")
    return [
      option("code", L("参数", "Parameter")),
      option("value", L("当前值", "Value")),
    ];
  return fields.value
    .filter((f) => !["password", "permissions"].includes(f.key))
    .slice(0, 6);
});
function displayValue(row, key) {
  const v = row[key];
  if (
    ["salePrice", "purchasePrice", "value"].includes(key) &&
    page.value !== "settings"
  )
    return cash(v);
  if (["quantity", "reorderLevel"].includes(key)) return qty(v);
  if (key === "enabled")
    return v ? L("启用", "Enabled") : L("停用", "Disabled");
  if (key === "kind") return movementLabel(kindLabel(v));
  if (key === "categoryId") return name("categories", v);
  if (key === "productId") return name("products", v);
  if (key === "warehouseId") return name("warehouses", v);
  if (key === "departmentId") return name("departments", v);
  if (key === "roleId") return name("roles", v);
  if (key === "scope")
    return v === "ALL"
      ? L("全部部门", "All departments")
      : L("本部门", "Own department");
  if (key === "permissions") return v.length;
  if (key === "createdAt") return date(v);
  return v || "—";
}
function date(v) {
  return v
    ? new Intl.DateTimeFormat(lang.value === "en" ? "en-GB" : "zh-CN", {
        timeZone: settings.value.timezone || "Asia/Shanghai",
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(v))
    : "—";
}
async function edit(row) {
  await run(async () => {
    if (isAdmin.value && ["users", "roles", "menus"].includes(page.value)) {
      for (const r of ["roles", "permissions"])
        catalog.value[r] = (await api(`/lists/${r}?size=100&desc=false`)).items;
    }
    editing.value = row?.id || null;
    form.value = row
      ? JSON.parse(JSON.stringify(row))
      : {
          enabled: true,
          departmentId: user.value.departmentId,
          kind: "CUSTOMER",
          scope: "DEPARTMENT",
          permissions: [],
          purchasePrice: "0.00",
          salePrice: "0.00",
          reorderLevel: "0.000",
        };
    form.value.password = "";
    dialog.value = "master";
  });
}
async function saveMaster() {
  await run(async () => {
    const input = { ...form.value };
    if (input.password === "") delete input.password;
    const base = isMaster.value ? "/master/" : "/admin/";
    await api(
      base + page.value + (editing.value ? "/" + editing.value : ""),
      editing.value ? "PUT" : "POST",
      input,
    );
    dialog.value = null;
    user.value = await api("/auth/me");
    await init();
    notice.value = L("已保存", "Saved");
  });
}
async function remove(row) {
  editing.value = row.id;
  form.value = { name: row.name || row.number || row.username };
  dialog.value = "delete";
}
async function confirmDelete() {
  await run(async () => {
    await api(
      (isOrders.value
        ? "/orders/"
        : (isMaster.value ? "/master/" : "/admin/") + page.value + "/") +
        editing.value,
      "DELETE",
    );
    dialog.value = null;
    catalog.value = await api("/catalog");
    await load();
    notice.value = L("已删除", "Deleted");
  });
}
function newOrder(
  kind = page.value === "purchase" ? "PURCHASE" : "SALES",
  existing = null,
) {
  editing.value = existing?.order.id || null;
  form.value = existing
    ? {
        kind: existing.order.kind,
        partyId: existing.order.partyId,
        warehouseId: existing.order.warehouseId,
        notes: existing.order.notes,
        lines: existing.lines.map((l) => ({
          productId: l.productId,
          quantity: String(l.quantity),
          price: String(l.price),
        })),
      }
    : {
        kind,
        partyId: "",
        warehouseId: warehouses.value.find((w) => w.enabled)?.id || "",
        notes: "",
        lines: [],
      };
  barcode.value = "";
  dialog.value = "order";
}
const orderWarehouse = computed(() => by("warehouses", form.value.warehouseId));
const orderProducts = computed(() =>
  products.value.filter(
    (p) => p.enabled && p.departmentId === orderWarehouse.value?.departmentId,
  ),
);
const orderParties = computed(() =>
  parties.value.filter(
    (p) =>
      p.enabled &&
      p.departmentId === orderWarehouse.value?.departmentId &&
      p.kind === (form.value.kind === "SALES" ? "CUSTOMER" : "SUPPLIER"),
  ),
);
function addLine(product) {
  const p =
    product ||
    orderProducts.value.find(
      (p) => !form.value.lines.some((l) => l.productId === p.id),
    );
  if (!p) return;
  const prior = form.value.lines.find((l) => l.productId === p.id);
  if (prior) {
    prior.quantity = String(Number(prior.quantity) + 1);
    return;
  }
  form.value.lines.push({
    productId: p.id,
    quantity: "1",
    price: String(form.value.kind === "SALES" ? p.salePrice : p.purchasePrice),
  });
}
function selectProduct(l) {
  const p = by("products", l.productId);
  if (p)
    l.price = String(
      form.value.kind === "SALES" ? p.salePrice : p.purchasePrice,
    );
}
async function scan() {
  await run(async () => {
    const p = await api("/barcode?value=" + encodeURIComponent(barcode.value));
    if (!orderProducts.value.some((v) => v.id === p.id))
      throw new Error("INVALID_MASTER");
    addLine(p);
    barcode.value = "";
  });
}
const draftTotal = computed(() =>
  (form.value.lines || []).reduce(
    (t, l) =>
      t +
      Math.round(Number(l.quantity || 0) * Number(l.price || 0) * 100) / 100,
    0,
  ),
);
async function saveOrder() {
  await run(async () => {
    const input = {
      ...form.value,
      lines: form.value.lines.map((l) => ({
        ...l,
        quantity: decimal(l.quantity),
        price: String(l.price),
      })),
    };
    detail.value = await api(
      "/orders" + (editing.value ? "/" + editing.value : ""),
      editing.value ? "PUT" : "POST",
      input,
    );
    dialog.value = null;
    await load();
  });
}
async function openOrder(row) {
  await run(async () => {
    detail.value = await api("/orders/" + row.id);
  });
}
function action(type, source = null) {
  actionData.value = { type, source };
  const d = detail.value;
  form.value = {
    requestKey: crypto.randomUUID(),
    reference: "",
    note: "",
    amount: String(
      type === "refund"
        ? Math.max(0, -Number(d.balance))
        : Math.max(0, Number(d.balance)),
    ),
    quantity: "",
    sourceId: source?.id,
    lines: d.lines
      .filter((l) => Number(l.quantity) > Number(l.fulfilled))
      .map((l) => ({
        lineId: l.id,
        quantity: String(Number(l.quantity) - Number(l.fulfilled)),
        name: l.productName,
        unit: l.unit,
      })),
  };
  dialog.value = "action";
}
async function submitAction() {
  await run(async () => {
    const type = actionData.value.type;
    const body = { ...form.value };
    if (type === "post")
      body.lines = body.lines
        .filter((l) => Number(l.quantity) > 0)
        .map((l) => ({ lineId: l.lineId, quantity: decimal(l.quantity) }));
    else delete body.lines;
    if (["return"].includes(type)) body.quantity = decimal(body.quantity);
    else delete body.quantity;
    if (!["pay", "refund", "credit"].includes(type)) delete body.amount;
    detail.value = await api(
      `/orders/${detail.value.order.id}/${type}`,
      "POST",
      body,
    );
    dialog.value = null;
    await load();
    notice.value = L(
      "已登记，请核对单据与台账",
      "Recorded. Verify the order and ledger.",
    );
  });
}
const actionTitle = computed(
  () =>
    ({
      confirm: L("确认订单", "Confirm order"),
      cancel: L("作废订单", "Cancel order"),
      post: L(
        detail.value?.order.kind === "SALES" ? "登记出库" : "登记入库",
        detail.value?.order.kind === "SALES"
          ? "Record shipment"
          : "Record receipt",
      ),
      return: L("登记原单退货", "Record return"),
      pay: L(
        detail.value?.order.kind === "SALES" ? "登记收款" : "登记付款",
        detail.value?.order.kind === "SALES"
          ? "Record receipt of payment"
          : "Record payment",
      ),
      refund: L(
        detail.value?.order.kind === "SALES"
          ? "登记客户退款"
          : "登记供应商退款",
        detail.value?.order.kind === "SALES"
          ? "Record customer refund"
          : "Record supplier refund",
      ),
      credit: L("价格调整", "Price credit"),
      reversePayment: L("冲销款项", "Reverse payment"),
    })[actionData.value.type] || "",
);
function stockAction(type, row = null) {
  actionData.value = { type };
  form.value = {
    requestKey: crypto.randomUUID(),
    productId: row?.productId || products.value[0]?.id,
    warehouseId: row?.warehouseId || warehouses.value[0]?.id,
    destinationId: "",
    quantity: type === "count" ? String(row?.quantity || "0") : "",
    expectedQuantity: String(row?.quantity || "0"),
    unitCost: "0.00",
    reference: "",
    note: "",
  };
  dialog.value = "stock";
}
async function submitStock() {
  await run(async () => {
    const body = { ...form.value, quantity: decimal(form.value.quantity) };
    await api("/stock/" + actionData.value.type, "POST", body);
    dialog.value = null;
    await load();
    notice.value = L("库存已登记", "Stock recorded");
  });
}
async function importFile(e) {
  const f = e.target.files[0];
  if (!f) return;
  await run(async () => {
    if (f.size > 500000) throw new Error("INVALID_INPUT");
    const items = JSON.parse(await f.text());
    const result = await api("/master/products/import", "POST", items);
    catalog.value = await api("/catalog");
    await load();
    notice.value = L("已导入 ", "Imported ") + result.created;
  });
  e.target.value = "";
}
function template() {
  const data = [
    {
      code: "EXAMPLE-001",
      name: L("示例商品", "Example product"),
      nameEn: "Example",
      barcode: null,
      specification: "",
      unit: L("件", "unit"),
      categoryId: catalog.value.categories?.[0]?.id,
      departmentId: user.value.departmentId,
      salePrice: "15.00",
      purchasePrice: "10.00",
      reorderLevel: "10.000",
      enabled: true,
    },
  ];
  const a = document.createElement("a");
  a.href = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
  );
  a.download = "products-template.json";
  a.click();
  URL.revokeObjectURL(a.href);
}
async function logout() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    resetCsrf();
    user.value = null;
    detail.value = null;
  });
}
async function openStatement() {
  await run(async () => {
    statementData.value = await api(
      "/statement?partyId=" + statementParty.value,
    );
    dialog.value = "statement";
  });
}
async function exportStatement() {
  await run(async () => {
    const r = await fetch(
      "/api/statement.csv?partyId=" + statementData.value.party.id,
    );
    if (!r.ok) throw new Error("FORBIDDEN");
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = "statement.csv";
    a.click();
    URL.revokeObjectURL(url);
  });
}
function changePassword() {
  form.value = { oldPassword: "", newPassword: "" };
  dialog.value = "password";
}
async function savePassword() {
  await run(async () => {
    await api("/auth/password", "POST", form.value);
    dialog.value = null;
    user.value = null;
    resetCsrf();
    notice.value = L(
      "密码已修改，请重新登录",
      "Password changed. Sign in again.",
    );
  });
}
const createAllowed = computed(() =>
  isMaster.value
    ? can("master.write")
    : isAdmin.value
      ? can("admin") &&
        !["permissions", "menus", "settings"].includes(page.value)
      : ["sales", "purchase"].includes(page.value) &&
        can(page.value + ".write"),
);
const orderWrite = computed(
  () =>
    detail.value &&
    can(detail.value.order.kind === "SALES" ? "sales.write" : "purchase.write"),
);
const activeOrder = computed(
  () =>
    detail.value && !["DRAFT", "CANCELLED"].includes(detail.value.order.status),
);
const financeKeys = [
  ["receivable", "客户未收", "Receivables"],
  ["payable", "供应商未付", "Payables"],
  ["customerCredit", "客户待退", "Customer credits"],
  ["supplierCredit", "供应商待退", "Supplier credits"],
  ["netSales", "净销售额", "Net sales"],
  ["salesCost", "销售成本", "Sales cost"],
  ["grossMargin", "销售毛利", "Gross margin"],
];
watch(lang, (v) => localStorage.setItem("psi-language", v));
onMounted(async () => {
  page.value = location.hash.slice(1) || "dashboard";
  try {
    user.value = await api("/auth/me");
    await init();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") error.value = message(e);
  }
});
</script>

<template>
  <div v-if="!user" class="login-page">
    <div class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><strong>{{
        L("知华科技", "ZhuaTech")
      }}</strong
      ><button class="text-button" @click="lang = lang === 'zh' ? 'en' : 'zh'">
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
    </div>
    <main class="login-card">
      <div class="eyebrow">ZHUA TECH · PSI</div>
      <h1>{{ L("商贸进销存", "Trade & inventory") }}</h1>
      <p>{{ L("登录你的工作账号", "Sign in to your work account") }}</p>
      <form @submit.prevent="signIn">
        <label
          >{{ L("账号", "Username")
          }}<input
            v-model="login.username"
            required
            autocomplete="username"
            maxlength="60" /></label
        ><label
          >{{ L("密码", "Password")
          }}<input
            v-model="login.password"
            required
            type="password"
            autocomplete="current-password"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" role="status">{{ notice }}</p>
        <button class="button primary full" :disabled="busy">
          {{ busy ? L("正在登录…", "Signing in…") : L("登录", "Sign in") }}
        </button>
      </form>
      <div class="license">
        {{
          L(
            "公开源码学习版 · 未经书面授权不得商用",
            "Non-commercial source edition · Written permission required for commercial use",
          )
        }}
      </div>
    </main>
    <footer>
      上海如静知华信息科技有限公司 ·
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
        L("官网 / 商业咨询", "Website / Commercial enquiries")
      }}</a
      ><span>{{ L("微信", "WeChat") }} zhuatech / zhuatech2</span>
    </footer>
  </div>
  <div v-else class="app">
    <aside :class="{ open: mobileNav }">
      <a
        class="brand"
        href="#dashboard"
        @click.prevent="navigate(menus[0]?.code || 'about')"
        ><img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>{{ L("知华商贸", "ZhuaTech Trade") }}</strong
          ><small>PSI · 1.0</small>
        </div></a
      >
      <nav>
        <button
          v-for="m in menus"
          :key="m.code"
          :disabled="busy || loading"
          :class="{ active: page === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || Settings" :size="17" /><span>{{
            lang === "en" ? m.nameEn : m.name
          }}</span>
        </button>
      </nav>
      <div class="sidebar-foot">
        <button class="text-button" @click="navigate('about')">
          {{ L("关于与商业授权", "About & licensing") }}</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >zhuatech.cn <ChevronRight :size="13"
        /></a>
      </div>
    </aside>
    <div v-if="mobileNav" class="nav-shade" @click="mobileNav = false"></div>
    <div class="workspace">
      <header class="topbar">
        <div class="crumb">
          <button
            class="icon-button mobile-only"
            :aria-label="L('打开菜单', 'Open navigation')"
            @click="mobileNav = !mobileNav"
          >
            <Menu :size="20" /></button
          ><span>{{ L("商贸进销存", "Trade & inventory") }}</span
          ><ChevronRight :size="14" /><strong>{{ title }}</strong>
        </div>
        <div class="top-actions">
          <button
            class="text-button"
            @click="lang = lang === 'zh' ? 'en' : 'zh'"
          >
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button class="text-button account" @click="changePassword">
            {{ user.displayName }}</button
          ><button
            class="icon-button"
            :aria-label="L('退出', 'Sign out')"
            @click="logout"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <h1>{{ title }}</h1>
            <p>
              {{
                page === "stock"
                  ? L(
                      "按基本单位记账 · 库存流水保留原始凭证",
                      "Base-unit ledger · Original references retained",
                    )
                  : page === "finance"
                    ? L(
                        "按实际收发货形成往来款 · 负余额为待退款",
                        "Balances arise from goods posted · Negative balances are refundable",
                      )
                    : page === "reports"
                      ? L(
                          "按已过账货物统计 · 不含税费和经营费用",
                          "Posted goods only · Taxes and operating expenses excluded",
                        )
                      : L("业务工作台", "Operations workspace")
              }}
            </p>
          </div>
          <div class="heading-actions">
            <button
              class="button"
              :disabled="busy || loading"
              @click="
                run(async () => {
                  if (detail) detail = await api('/orders/' + detail.order.id);
                  await load();
                })
              "
            >
              <RefreshCw :size="15" />{{ L("刷新", "Refresh") }}</button
            ><button
              v-if="createAllowed"
              class="button primary"
              :disabled="busy"
              @click="isOrders ? newOrder() : edit(null)"
            >
              <Plus :size="16" />{{ L("新建", "New") }}</button
            ><button
              v-if="page === 'reports' && can('report')"
              class="button"
              @click="run(downloadReport)"
            >
              <Download :size="15" />{{ L("导出 CSV", "Export CSV") }}
            </button>
          </div>
        </div>
        <div v-if="error" class="banner error" role="alert">
          {{ error
          }}<button
            class="icon-button"
            :aria-label="L('关闭提示', 'Dismiss')"
            @click="error = ''"
          >
            <X :size="16" />
          </button>
        </div>
        <div v-if="notice" class="banner success" role="status">
          <Check :size="16" />{{ notice }}
        </div>
        <template v-if="page === 'dashboard'"
          ><div class="metric-row">
            <div>
              <small>{{ L("待履约单据", "Orders awaiting fulfilment") }}</small
              ><strong>{{ dash.pending || 0 }}</strong>
            </div>
            <div v-if="dash.inventoryValue !== undefined">
              <small>{{ L("库存价值", "Stock value") }}</small
              ><strong>{{ cash(dash.inventoryValue) }}</strong>
            </div>
            <div v-if="dash.finance">
              <small>{{ L("客户未收", "Receivables") }}</small
              ><strong>{{ cash(dash.finance.receivable) }}</strong>
            </div>
            <div v-if="dash.finance">
              <small>{{ L("供应商未付", "Payables") }}</small
              ><strong>{{ cash(dash.finance.payable) }}</strong>
            </div>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ L("近期单据", "Recent orders") }}</h2>
              <span>{{
                L(
                  "点击单据查看收发货和款项",
                  "Open an order to view goods and payments",
                )
              }}</span>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ L("单号", "Order") }}</th>
                    <th>{{ L("类型", "Type") }}</th>
                    <th>{{ L("客户 / 供应商", "Customer / supplier") }}</th>
                    <th>{{ L("状态", "Status") }}</th>
                    <th class="numeric">
                      {{ L("已过账金额", "Posted amount") }}
                    </th>
                    <th>{{ L("创建时间", "Created") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="d in dash.recent || []"
                    :key="d.id"
                    class="clickable"
                    @click="openOrder(d)"
                  >
                    <td>
                      <button class="link-button">{{ d.number }}</button>
                    </td>
                    <td>{{ kindLabel(d.kind) }}</td>
                    <td>{{ d.partyName }}</td>
                    <td>
                      <span :class="['badge', d.status]">{{
                        stateLabel(d.status)
                      }}</span>
                    </td>
                    <td class="numeric">{{ cash(d.netAmount, d.currency) }}</td>
                    <td>{{ date(d.createdAt) }}</td>
                  </tr>
                  <tr v-if="!dash.recent?.length">
                    <td colspan="6" class="empty">
                      {{
                        L(
                          "还没有单据，从采购或销售开始开单",
                          "No orders yet. Create a purchase or sales order.",
                        )
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section v-if="dash.lowStock?.length" class="panel">
            <div class="section-heading">
              <h2>{{ L("库存预警", "Low stock") }}</h2>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ L("商品", "Product") }}</th>
                    <th>{{ L("仓库", "Warehouse") }}</th>
                    <th class="numeric">{{ L("结存", "On hand") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="s in dash.lowStock" :key="s.id">
                    <td>{{ name("products", s.productId) }}</td>
                    <td>{{ name("warehouses", s.warehouseId) }}</td>
                    <td class="numeric">{{ qty(s.quantity) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else-if="page === 'reports'"
          ><div class="metric-row report-metrics">
            <div v-for="k in financeKeys" :key="k[0]">
              <small>{{ L(k[1], k[2]) }}</small
              ><strong>{{ cash(reports.summary?.[k[0]]) }}</strong>
            </div>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ L("订单汇总", "Order reconciliation") }}</h2>
              <span>{{ reports.currency }}</span>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ L("单号", "Order") }}</th>
                    <th>{{ L("往来单位", "Party") }}</th>
                    <th>{{ L("类型", "Type") }}</th>
                    <th class="numeric">{{ L("净过账金额", "Net posted") }}</th>
                    <th class="numeric">{{ L("净收付款", "Net payments") }}</th>
                    <th class="numeric">{{ L("未结余额", "Balance") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="d in reports.orders || []"
                    :key="d.id"
                    class="clickable"
                    @click="openOrder(d)"
                  >
                    <td class="mono">{{ d.number }}</td>
                    <td>{{ d.partyName }}</td>
                    <td>{{ kindLabel(d.kind) }}</td>
                    <td class="numeric">{{ cash(d.netAmount) }}</td>
                    <td class="numeric">{{ cash(d.netPaid) }}</td>
                    <td class="numeric">
                      {{ cash(Number(d.netAmount) - Number(d.netPaid)) }}
                    </td>
                  </tr>
                  <tr v-if="!reports.orders?.length">
                    <td colspan="6" class="empty">
                      {{ L("暂无已保存单据", "No saved orders") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <section v-else-if="page === 'about'" class="panel about">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <h2>
            {{
              L(
                "知华科技商贸进销存公开源码学习版",
                "ZhuaTech Trade & Inventory · Non-commercial source edition",
              )
            }}
          </h2>
          <p>1.0.0 · 上海如静知华信息科技有限公司</p>
          <p>
            {{
              L(
                "仅限个人学习、技术研究与非商业交流。商用、企业交付、收费部署和商业二次开发须取得书面授权。",
                "For personal learning, research and non-commercial exchange. Commercial deployment, delivery and development require written permission.",
              )
            }}
          </p>
          <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >https://www.zhuatech.cn/</a
          >
          <p>
            {{
              L(
                "商业授权、定制、部署及集成咨询微信",
                "Commercial licensing, development, deployment and integration enquiries via WeChat",
              )
            }}：zhuatech / zhuatech2
          </p>
        </section>
        <section v-else class="panel">
          <div v-if="page === 'finance'" class="toolbar">
            <select
              v-model="statementParty"
              :aria-label="L('对账单位', 'Statement party')"
            >
              <option value="" disabled>
                {{ L("选择客户或供应商", "Select customer or supplier") }}
              </option>
              <option v-for="p in parties" :key="p.id" :value="p.id">
                {{ p.name }}
              </option></select
            ><button
              class="button"
              :disabled="!statementParty || busy"
              @click="openStatement"
            >
              {{ L("查看对账单", "View statement") }}
            </button>
          </div>
          <div v-if="page === 'stock'" class="tabs">
            <button
              :class="{ selected: stockTab === 'stock' }"
              @click="
                stockTab = 'stock';
                index = 0;
                run(load);
              "
            >
              {{ L("库存结存", "On hand") }}</button
            ><button
              :class="{ selected: stockTab === 'movements' }"
              @click="
                stockTab = 'movements';
                index = 0;
                run(load);
              "
            >
              {{ L("库存流水", "Stock ledger") }}
            </button>
            <div class="spacer"></div>
            <button
              v-if="can('stock.write')"
              class="button"
              @click="stockAction('opening')"
            >
              {{ L("期初库存", "Opening stock") }}</button
            ><button
              v-if="can('stock.write')"
              class="button"
              @click="stockAction('transfer')"
            >
              {{ L("仓库调拨", "Transfer") }}
            </button>
          </div>
          <form
            class="toolbar"
            @submit.prevent="
              index = 0;
              run(load);
            "
          >
            <div class="search">
              <Search :size="16" /><input
                v-model="search"
                :placeholder="
                  L('搜索单号、名称或编码', 'Search order, name or code')
                "
                maxlength="200"
              />
            </div>
            <select
              v-if="isOrders"
              v-model="status"
              :aria-label="L('状态筛选', 'Status filter')"
              @change="
                index = 0;
                run(load);
              "
            >
              <option value="">{{ L("全部状态", "All statuses") }}</option>
              <option
                v-for="s in [
                  'DRAFT',
                  'CONFIRMED',
                  'PARTIAL',
                  'FULFILLED',
                  'CANCELLED',
                ]"
                :key="s"
                :value="s"
              >
                {{ stateLabel(s) }}
              </option></select
            ><select
              v-if="page === 'parties'"
              v-model="status"
              :aria-label="L('类型筛选', 'Type filter')"
              @change="
                index = 0;
                run(load);
              "
            >
              <option value="">{{ L("全部类型", "All types") }}</option>
              <option value="CUSTOMER">{{ L("客户", "Customer") }}</option>
              <option value="SUPPLIER">
                {{ L("供应商", "Supplier") }}
              </option></select
            ><button class="button">{{ L("查询", "Search") }}</button
            ><select
              v-model="sort"
              :aria-label="L('排序', 'Sort')"
              @change="
                index = 0;
                run(load);
              "
            >
              <option value="id">{{ L("记录顺序", "Record order") }}</option>
              <option v-if="isMaster || isAdmin" value="name">
                {{ L("名称", "Name") }}
              </option>
              <option v-if="isOrders" value="number">
                {{ L("单号", "Order number") }}
              </option></select
            ><button
              type="button"
              class="text-button"
              @click="
                desc = !desc;
                run(load);
              "
            >
              {{ desc ? L("降序", "Descending") : L("升序", "Ascending") }}
            </button>
            <div class="spacer"></div>
            <template v-if="page === 'products' && can('master.write')"
              ><button type="button" class="text-button" @click="template">
                {{ L("导入模板", "Import template") }}</button
              ><label class="button upload"
                >{{ L("导入 JSON", "Import JSON")
                }}<input
                  type="file"
                  accept="application/json,.json"
                  @change="importFile" /></label
            ></template>
          </form>
          <div class="table-scroll">
            <table v-if="isOrders">
              <thead>
                <tr>
                  <th>{{ L("单号 / 时间", "Order / date") }}</th>
                  <th>{{ L("客户 / 供应商", "Customer / supplier") }}</th>
                  <th>{{ L("仓库", "Warehouse") }}</th>
                  <th>{{ L("状态", "Status") }}</th>
                  <th class="numeric">{{ L("净过账金额", "Net posted") }}</th>
                  <th class="numeric">{{ L("净收付款", "Net payments") }}</th>
                  <th class="numeric">{{ L("未结余额", "Balance") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="r in rows"
                  :key="r.id"
                  class="clickable"
                  @click="openOrder(r)"
                >
                  <td>
                    <button class="link-button mono">{{ r.number }}</button
                    ><small class="cell-sub">{{ date(r.createdAt) }}</small>
                  </td>
                  <td>
                    {{ r.partyName
                    }}<small class="cell-sub">{{ kindLabel(r.kind) }}</small>
                  </td>
                  <td>{{ name("warehouses", r.warehouseId) }}</td>
                  <td>
                    <span :class="['badge', r.status]">{{
                      stateLabel(r.status)
                    }}</span>
                  </td>
                  <td class="numeric">{{ cash(r.netAmount, r.currency) }}</td>
                  <td class="numeric">{{ cash(r.netPaid, r.currency) }}</td>
                  <td class="numeric">
                    {{
                      cash(Number(r.netAmount) - Number(r.netPaid), r.currency)
                    }}
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td colspan="7" class="empty">
                    {{
                      loading
                        ? L("正在加载…", "Loading…")
                        : L("暂无符合条件的单据", "No matching orders")
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
            <table v-else>
              <thead>
                <tr>
                  <th
                    v-for="c in columns"
                    :key="c.key"
                    :class="{
                      numeric: [
                        'quantity',
                        'value',
                        'salePrice',
                        'purchasePrice',
                      ].includes(c.key),
                    }"
                  >
                    {{ c.label }}
                  </th>
                  <th
                    v-if="
                      (isMaster && can('master.write')) ||
                      (isAdmin && can('admin')) ||
                      (page === 'stock' &&
                        stockTab === 'stock' &&
                        can('stock.write'))
                    "
                  >
                    {{ L("操作", "Actions") }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td
                    v-for="c in columns"
                    :key="c.key"
                    :class="{
                      numeric: [
                        'quantity',
                        'value',
                        'salePrice',
                        'purchasePrice',
                      ].includes(c.key),
                    }"
                  >
                    {{ displayValue(r, c.key)
                    }}<small
                      v-if="c.key === 'name' && r.specification"
                      class="cell-sub"
                      >{{ r.specification }}</small
                    >
                  </td>
                  <td
                    v-if="
                      (isMaster && can('master.write')) ||
                      (isAdmin && can('admin'))
                    "
                    class="row-actions"
                  >
                    <button class="link-button" @click="edit(r)">
                      {{ L("编辑", "Edit") }}</button
                    ><button
                      v-if="
                        !['menus', 'permissions', 'settings'].includes(page)
                      "
                      class="link-button danger"
                      @click="remove(r)"
                    >
                      {{ L("删除", "Delete") }}
                    </button>
                  </td>
                  <td
                    v-else-if="
                      page === 'stock' &&
                      stockTab === 'stock' &&
                      can('stock.write')
                    "
                  >
                    <button
                      class="link-button"
                      @click="stockAction('count', r)"
                    >
                      {{ L("盘点", "Count") }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td :colspan="columns.length + 1" class="empty">
                    {{
                      loading
                        ? L("正在加载…", "Loading…")
                        : L("暂无符合条件的记录", "No matching records")
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <span
              >{{ L("共", "Total") }} {{ total }} {{ L("条", "records") }}</span
            >
            <div>
              <button
                class="button"
                :disabled="index === 0 || busy"
                @click="
                  index--;
                  run(load);
                "
              >
                {{ L("上一页", "Previous") }}</button
              ><span
                >{{ index + 1 }} /
                {{ Math.max(1, Math.ceil(total / 20)) }}</span
              ><button
                class="button"
                :disabled="(index + 1) * 20 >= total || busy"
                @click="
                  index++;
                  run(load);
                "
              >
                {{ L("下一页", "Next") }}
              </button>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>

  <div v-if="detail" class="drawer-shade" @click.self="detail = null">
    <section class="drawer">
      <div class="drawer-head">
        <button
          class="icon-button"
          :aria-label="L('关闭单据', 'Close order')"
          @click="detail = null"
        >
          <ArrowLeft :size="20" />
        </button>
        <div>
          <small>{{ kindLabel(detail.order.kind) }}</small>
          <h2 class="mono">{{ detail.order.number }}</h2>
        </div>
        <span :class="['badge', detail.order.status]">{{
          stateLabel(detail.order.status)
        }}</span>
        <div class="spacer"></div>
        <button
          class="icon-button"
          :aria-label="L('打印单据', 'Print order')"
          @click="
            run(async () => {
              window.print();
            })
          "
        >
          <Printer :size="19" /></button
        ><button
          class="icon-button"
          :aria-label="L('关闭', 'Close')"
          @click="detail = null"
        >
          <X :size="20" />
        </button>
      </div>
      <div v-if="error" class="banner error" role="alert">{{ error }}</div>
      <div class="drawer-body print-sheet">
        <div class="document-meta">
          <div>
            <small>{{ L("往来单位", "Party") }}</small
            ><strong>{{ detail.order.partyName }}</strong>
          </div>
          <div>
            <small>{{ L("仓库", "Warehouse") }}</small
            ><strong>{{ name("warehouses", detail.order.warehouseId) }}</strong>
          </div>
          <div>
            <small>{{ L("创建时间", "Created") }}</small
            ><span>{{ date(detail.order.createdAt) }}</span>
          </div>
        </div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{{ L("商品", "Product") }}</th>
                <th class="numeric">{{ L("订单量", "Ordered") }}</th>
                <th class="numeric">{{ L("已履约", "Fulfilled") }}</th>
                <th class="numeric">{{ L("已退", "Returned") }}</th>
                <th class="numeric">{{ L("单价", "Unit price") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="l in detail.lines" :key="l.id">
                <td>
                  {{ l.productName
                  }}<small class="cell-sub"
                    >{{ l.productCode }} · {{ l.unit }}</small
                  >
                </td>
                <td class="numeric">{{ qty(l.quantity) }}</td>
                <td class="numeric">{{ qty(l.fulfilled) }}</td>
                <td class="numeric">{{ qty(l.returned) }}</td>
                <td class="numeric">
                  {{ cash(l.price, detail.order.currency) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="document-totals">
          <div>
            <small>{{ L("净过账金额", "Net posted amount") }}</small
            ><strong>{{
              cash(detail.order.netAmount, detail.order.currency)
            }}</strong>
          </div>
          <div>
            <small>{{ L("净收付款", "Net payments") }}</small
            ><strong>{{
              cash(detail.order.netPaid, detail.order.currency)
            }}</strong>
          </div>
          <div>
            <small>{{
              Number(detail.balance) < 0
                ? L("待退款", "Refundable")
                : L("未结余额", "Outstanding balance")
            }}</small
            ><strong :class="{ accent: Number(detail.balance) !== 0 }">{{
              cash(Math.abs(Number(detail.balance)), detail.order.currency)
            }}</strong>
          </div>
        </div>
        <p v-if="detail.order.notes" class="document-note">
          {{ detail.order.notes }}
        </p>
        <div class="document-actions">
          <template v-if="detail.order.status === 'DRAFT' && orderWrite"
            ><button class="button" @click="newOrder('', detail)">
              {{ L("编辑草稿", "Edit draft") }}</button
            ><button class="button primary" @click="action('confirm')">
              {{ L("确认订单", "Confirm order") }}
            </button></template
          ><button
            v-if="
              ['DRAFT', 'CONFIRMED'].includes(detail.order.status) && orderWrite
            "
            class="button danger"
            @click="action('cancel')"
          >
            {{ L("作废", "Cancel") }}</button
          ><button
            v-if="
              ['CONFIRMED', 'PARTIAL'].includes(detail.order.status) &&
              can('stock.write')
            "
            class="button primary"
            @click="action('post')"
          >
            {{
              L(
                detail.order.kind === "SALES" ? "登记出库" : "登记入库",
                detail.order.kind === "SALES"
                  ? "Record shipment"
                  : "Record receipt",
              )
            }}</button
          ><button
            v-if="activeOrder && can('finance') && Number(detail.balance) > 0"
            class="button"
            @click="action('pay')"
          >
            {{
              L(
                detail.order.kind === "SALES" ? "登记收款" : "登记付款",
                detail.order.kind === "SALES"
                  ? "Receive payment"
                  : "Make payment",
              )
            }}</button
          ><button
            v-if="activeOrder && can('finance') && Number(detail.balance) < 0"
            class="button"
            @click="action('refund')"
          >
            {{ L("登记退款", "Record refund") }}
          </button>
        </div>
        <h3>{{ L("收发货与退货凭证", "Goods and return entries") }}</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{{ L("业务 / 时间", "Type / time") }}</th>
                <th>{{ L("商品", "Product") }}</th>
                <th class="numeric">{{ L("数量", "Quantity") }}</th>
                <th>{{ L("凭证", "Reference") }}</th>
                <th class="numeric">{{ L("金额变动", "Amount change") }}</th>
                <th class="no-print"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in detail.movements" :key="m.id">
                <td>
                  {{ movementLabel(m.kind)
                  }}<small class="cell-sub">{{ date(m.createdAt) }}</small>
                </td>
                <td>{{ name("products", m.productId) }}</td>
                <td class="numeric">{{ qty(m.quantity) }}</td>
                <td>
                  {{ m.reference }}<small class="cell-sub">{{ m.note }}</small>
                </td>
                <td class="numeric">
                  {{ cash(m.amount, detail.order.currency) }}
                </td>
                <td class="no-print">
                  <button
                    v-if="
                      ['RECEIPT', 'SHIPMENT'].includes(m.kind) &&
                      can('stock.write')
                    "
                    class="link-button"
                    @click="action('return', m)"
                  >
                    {{ L("退货", "Return") }}
                  </button>
                  <button
                    v-if="
                      ['RECEIPT', 'SHIPMENT'].includes(m.kind) &&
                      can('finance') &&
                      !detail.movements.some(
                        (e) => e.sourceId === m.id && Number(e.quantity) !== 0,
                      )
                    "
                    class="link-button"
                    @click="action('credit', m)"
                  >
                    {{ L("调价", "Price credit") }}
                  </button>
                </td>
              </tr>
              <tr v-if="!detail.movements.length">
                <td colspan="6" class="empty">
                  {{ L("尚未收发货", "No goods posted") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <h3>{{ L("收付款凭证", "Payment entries") }}</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{{ L("时间", "Time") }}</th>
                <th>{{ L("业务", "Type") }}</th>
                <th>{{ L("凭证 / 备注", "Reference / note") }}</th>
                <th class="numeric">{{ L("金额", "Amount") }}</th>
                <th>{{ L("登记人", "Recorded by") }}</th>
                <th class="no-print"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in detail.payments" :key="p.id">
                <td>{{ date(p.createdAt) }}</td>
                <td>
                  {{
                    p.kind === "PAY"
                      ? L(
                          detail.order.kind === "SALES" ? "收款" : "付款",
                          detail.order.kind === "SALES" ? "Receipt" : "Payment",
                        )
                      : kindLabel(p.kind)
                  }}
                </td>
                <td>
                  {{ p.reference }}<small class="cell-sub">{{ p.note }}</small>
                </td>
                <td class="numeric">
                  {{ cash(p.amount, detail.order.currency) }}
                </td>
                <td>{{ p.createdBy }}</td>
                <td class="no-print">
                  <button
                    v-if="
                      can('finance') &&
                      ['PAY', 'REFUND'].includes(p.kind) &&
                      !detail.payments.some((e) => e.reversalOf === p.id)
                    "
                    class="link-button"
                    @click="action('reversePayment', p)"
                  >
                    {{ L("冲销", "Reverse") }}
                  </button>
                </td>
              </tr>
              <tr v-if="!detail.payments.length">
                <td colspan="5" class="empty">
                  {{ L("尚未登记款项", "No payments recorded") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>

  <div v-if="dialog" class="modal-shade" @click.self="!busy && (dialog = null)">
    <section
      :class="['modal', { wide: ['order', 'statement'].includes(dialog) }]"
    >
      <div class="modal-head">
        <h2>
          {{
            dialog === "master"
              ? (editing ? L("编辑", "Edit") : L("新建", "New")) + " " + title
              : dialog === "order"
                ? L(
                    form.kind === "SALES" ? "销售开单" : "采购开单",
                    form.kind === "SALES" ? "Sales order" : "Purchase order",
                  )
                : dialog === "action"
                  ? actionTitle
                  : dialog === "stock"
                    ? {
                        opening: L("期初入库", "Opening stock"),
                        transfer: L("仓库调拨", "Warehouse transfer"),
                        count: L("库存盘点", "Stock count"),
                      }[actionData.type]
                    : dialog === "statement"
                      ? L("往来对账单", "Party statement")
                      : dialog === "password"
                        ? L("修改密码", "Change password")
                        : L("删除记录", "Delete record")
          }}
        </h2>
        <button
          class="icon-button"
          :disabled="busy"
          :aria-label="L('关闭', 'Close')"
          @click="dialog = null"
        >
          <X :size="20" />
        </button>
      </div>
      <form
        @submit.prevent="
          dialog === 'master'
            ? saveMaster()
            : dialog === 'order'
              ? saveOrder()
              : dialog === 'action'
                ? submitAction()
                : dialog === 'stock'
                  ? submitStock()
                  : dialog === 'password'
                    ? savePassword()
                    : confirmDelete()
        "
      >
        <div class="modal-body">
          <template v-if="dialog === 'statement'"
            ><div class="document-meta">
              <strong>{{ statementData.party.name }}</strong
              ><span>{{
                L(
                  "全期间 · 初始往来余额为零",
                  "All entries · Opening balance is zero",
                )
              }}</span>
            </div>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ L("时间", "Time") }}</th>
                    <th>{{ L("单号", "Order") }}</th>
                    <th>{{ L("业务", "Type") }}</th>
                    <th>{{ L("凭证", "Reference") }}</th>
                    <th class="numeric">{{ L("增减额", "Change") }}</th>
                    <th class="numeric">
                      {{ L("累计余额", "Running balance") }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(e, i) in statementData.entries" :key="i">
                    <td>{{ date(e.at) }}</td>
                    <td>{{ e.order }}</td>
                    <td>{{ movementLabel(kindLabel(e.kind)) }}</td>
                    <td>{{ e.reference }}</td>
                    <td class="numeric">{{ cash(e.change) }}</td>
                    <td class="numeric">{{ cash(e.balance) }}</td>
                  </tr>
                  <tr v-if="!statementData.entries.length">
                    <td colspan="6" class="empty">
                      {{ L("暂无往来流水", "No entries") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="draft-total">
              {{
                L(
                  "未结余额（负数为待退）",
                  "Balance (negative means refundable)",
                )
              }}
              <strong>{{ cash(statementData.balance) }}</strong>
            </div></template
          >
          <div v-if="error" class="banner error" role="alert">{{ error }}</div>
          <div v-if="dialog === 'master'" class="form-grid">
            <label
              v-for="f in fields"
              :key="f.key"
              :class="{ span2: ['permissions', 'notes'].includes(f.key) }"
              ><span>{{ f.label }}</span
              ><select
                v-if="f.type === 'select'"
                v-model="form[f.key]"
                :aria-label="f.label"
                required
              >
                <option value="" disabled>{{ L("请选择", "Select") }}</option>
                <option v-for="o in f.options" :key="o.id" :value="o.id">
                  {{ o.name }}
                </option></select
              ><textarea
                v-else-if="f.type === 'textarea'"
                v-model="form[f.key]"
                :aria-label="f.label"
                :required="!f.optional"
                maxlength="1000"
              ></textarea>
              <div v-else-if="f.type === 'permissions'" class="permission-grid">
                <label v-for="p in f.options" :key="p.code"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ p.name }}</label
                >
              </div>
              <input
                v-else-if="f.type === 'checkbox'"
                v-model="form[f.key]"
                :aria-label="f.label"
                type="checkbox" /><input
                v-else
                v-model="form[f.key]"
                :aria-label="f.label"
                :type="f.type"
                :step="f.step"
                :min="f.type === 'number' ? '0' : undefined"
                :required="!f.optional"
                :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
            /></label>
          </div>
          <template v-if="dialog === 'order'"
            ><div class="form-grid">
              <label
                >{{ L("仓库", "Warehouse")
                }}<select
                  v-model="form.warehouseId"
                  :aria-label="L('仓库', 'Warehouse')"
                  required
                  @change="
                    form.lines = [];
                    form.partyId = '';
                  "
                >
                  <option
                    v-for="w in warehouses.filter((w) => w.enabled)"
                    :key="w.id"
                    :value="w.id"
                  >
                    {{ w.name }}
                  </option>
                </select></label
              ><label
                >{{
                  L(
                    form.kind === "SALES" ? "客户" : "供应商",
                    form.kind === "SALES" ? "Customer" : "Supplier",
                  )
                }}<select
                  v-model="form.partyId"
                  :aria-label="
                    L(
                      form.kind === 'SALES' ? '客户' : '供应商',
                      form.kind === 'SALES' ? 'Customer' : 'Supplier',
                    )
                  "
                  required
                >
                  <option value="" disabled>{{ L("请选择", "Select") }}</option>
                  <option v-for="p in orderParties" :key="p.id" :value="p.id">
                    {{ p.name }}
                  </option>
                </select></label
              >
            </div>
            <div class="order-line-toolbar">
              <div class="search">
                <input
                  v-model="barcode"
                  :placeholder="
                    L(
                      '输入或扫描条码，回车添加',
                      'Type or scan barcode, press Enter',
                    )
                  "
                  @keydown.enter.prevent="scan"
                />
              </div>
              <button type="button" class="button" @click="scan">
                {{ L("扫码添加", "Add by barcode") }}</button
              ><button type="button" class="button" @click="addLine()">
                <Plus :size="15" />{{ L("添加商品", "Add product") }}
              </button>
            </div>
            <div class="table-scroll">
              <table class="line-editor">
                <thead>
                  <tr>
                    <th>{{ L("商品", "Product") }}</th>
                    <th>{{ L("数量", "Quantity") }}</th>
                    <th>{{ L("单价", "Unit price") }}</th>
                    <th class="numeric">{{ L("金额", "Amount") }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(l, i) in form.lines" :key="i">
                    <td>
                      <select
                        v-model="l.productId"
                        required
                        @change="selectProduct(l)"
                      >
                        <option
                          v-for="p in orderProducts"
                          :key="p.id"
                          :value="p.id"
                        >
                          {{ p.code }} · {{ p.name }}
                        </option></select
                      ><small>{{ by("products", l.productId)?.unit }}</small>
                    </td>
                    <td>
                      <input
                        v-model="l.quantity"
                        type="number"
                        min="0.001"
                        step="0.001"
                        required
                      />
                    </td>
                    <td>
                      <input
                        v-model="l.price"
                        type="number"
                        min="0"
                        step="0.01"
                        required
                      />
                    </td>
                    <td class="numeric">
                      {{ cash(Number(l.quantity) * Number(l.price)) }}
                    </td>
                    <td>
                      <button
                        type="button"
                        class="icon-button"
                        :aria-label="L('移除商品', 'Remove product')"
                        @click="form.lines.splice(i, 1)"
                      >
                        <X :size="16" />
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!form.lines.length">
                    <td colspan="5" class="empty">
                      {{ L("添加至少一种商品", "Add at least one product") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="draft-total">
              {{ L("订单参考总额", "Order total") }}
              <strong>{{ cash(draftTotal) }}</strong>
            </div>
            <label
              >{{ L("备注", "Notes")
              }}<textarea
                v-model="form.notes"
                maxlength="1000"
              ></textarea></label
          ></template>
          <template v-if="dialog === 'action'"
            ><p v-if="actionData.type === 'confirm'">
              {{
                L(
                  "确认后单据明细冻结；库存和往来款在实际收发货时登记。",
                  "Confirmation freezes the lines. Stock and balances are posted when goods are received or shipped.",
                )
              }}
            </p>
            <p v-if="actionData.type === 'cancel'">
              {{
                L(
                  "作废后不能收发货。已有履约或资金凭证的单据不能作废。",
                  "Cancelled orders cannot be fulfilled. Orders with goods or payment entries cannot be cancelled.",
                )
              }}
            </p>
            <div v-if="actionData.type === 'post'" class="post-lines">
              <label v-for="l in form.lines" :key="l.lineId"
                ><span
                  >{{ l.name }}<small>{{ l.unit }}</small></span
                ><input
                  v-model="l.quantity"
                  type="number"
                  step="0.001"
                  min="0"
                  required
              /></label>
            </div>
            <template v-if="actionData.type === 'return'"
              ><p>
                {{ name("products", actionData.source.productId) }} ·
                {{ L("原凭证", "Original reference") }}
                {{ actionData.source.reference }}
              </p>
              <label
                >{{ L("退回可销售商品数量", "Saleable quantity returned")
                }}<input
                  v-model="form.quantity"
                  type="number"
                  step="0.001"
                  min="0.001"
                  required
              /></label>
              <p class="hint">
                {{
                  L(
                    "退款另行登记；损坏商品不能作为可销售退货入库。",
                    "Refunds are recorded separately. Damaged goods must not be returned as saleable stock.",
                  )
                }}
              </p></template
            ><label v-if="['pay', 'refund', 'credit'].includes(actionData.type)"
              >{{ L("金额", "Amount") }} ({{ detail.order.currency }})<input
                v-model="form.amount"
                type="number"
                min="0.01"
                step="0.01"
                required /></label
            ><template v-if="!['confirm', 'cancel'].includes(actionData.type)"
              ><label
                >{{ L("外部凭证号", "External reference")
                }}<input
                  v-model="form.reference"
                  maxlength="120"
                  required /></label
              ><label
                >{{ L("备注 / 核对说明", "Note / verification")
                }}<textarea
                  v-model="form.note"
                  maxlength="500"
                  :required="
                    ['return', 'credit', 'reversePayment'].includes(
                      actionData.type,
                    )
                  "
                ></textarea></label></template
          ></template>
          <div v-if="dialog === 'stock'" class="form-grid">
            <label
              >{{ L("商品", "Product")
              }}<select
                v-model="form.productId"
                :aria-label="L('商品', 'Product')"
                required
                :disabled="actionData.type === 'count'"
              >
                <option
                  v-for="p in products.filter((p) => p.enabled)"
                  :key="p.id"
                  :value="p.id"
                >
                  {{ p.code }} · {{ p.name }}
                </option>
              </select></label
            ><label
              >{{ L("仓库", "Warehouse")
              }}<select
                v-model="form.warehouseId"
                :aria-label="L('仓库', 'Warehouse')"
                required
                :disabled="actionData.type === 'count'"
              >
                <option
                  v-for="w in warehouses.filter((w) => w.enabled)"
                  :key="w.id"
                  :value="w.id"
                >
                  {{ w.name }}
                </option>
              </select></label
            ><label v-if="actionData.type === 'transfer'"
              >{{ L("调入仓库", "Destination warehouse")
              }}<select
                v-model="form.destinationId"
                :aria-label="L('调入仓库', 'Destination warehouse')"
                required
              >
                <option value="" disabled>{{ L("请选择", "Select") }}</option>
                <option
                  v-for="w in warehouses.filter(
                    (w) => w.enabled && w.id !== form.warehouseId,
                  )"
                  :key="w.id"
                  :value="w.id"
                >
                  {{ w.name }}
                </option>
              </select></label
            ><label
              >{{
                actionData.type === "count"
                  ? L("实盘数量", "Counted quantity")
                  : L("数量", "Quantity")
              }}<input
                v-model="form.quantity"
                required
                type="number"
                :min="actionData.type === 'count' ? 0 : 0.001"
                step="0.001" /></label
            ><label v-if="actionData.type !== 'transfer'"
              >{{ L("期初 / 盘盈单价", "Opening / surplus unit cost")
              }}<input
                v-model="form.unitCost"
                required
                type="number"
                min="0"
                step="0.01" /></label
            ><label
              >{{ L("外部凭证号", "External reference")
              }}<input
                v-model="form.reference"
                maxlength="120"
                required /></label
            ><label class="span2"
              >{{ L("调整原因", "Reason")
              }}<textarea
                v-model="form.note"
                maxlength="500"
                required
              ></textarea>
            </label>
            <p v-if="actionData.type === 'count'" class="hint span2">
              {{ L("盘点时账面数量", "Observed book quantity") }}：{{
                qty(form.expectedQuantity)
              }}
            </p>
          </div>
          <template v-if="dialog === 'password'"
            ><label
              >{{ L("原密码", "Current password")
              }}<input
                v-model="form.oldPassword"
                type="password"
                required
                autocomplete="current-password" /></label
            ><label
              >{{ L("新密码", "New password")
              }}<input
                v-model="form.newPassword"
                type="password"
                required
                minlength="12"
                maxlength="72"
                autocomplete="new-password" /></label
          ></template>
          <p v-if="dialog === 'delete'">
            {{ L("确认删除", "Delete") }} <strong>{{ form.name }}</strong
            >？{{
              L(
                "已被业务引用的记录不能删除。",
                "Referenced records cannot be deleted.",
              )
            }}
          </p>
        </div>
        <div class="modal-foot">
          <button
            v-if="dialog === 'statement'"
            type="button"
            class="button primary"
            :disabled="busy"
            @click="exportStatement"
          >
            {{ L("导出 CSV", "Export CSV") }}
          </button>
          <button
            type="button"
            class="button"
            :disabled="busy"
            @click="dialog = null"
          >
            {{ L("取消", "Cancel") }}</button
          ><button
            v-if="dialog !== 'statement'"
            :class="['button', dialog === 'delete' ? 'danger' : 'primary']"
            :disabled="busy || (dialog === 'order' && !form.lines.length)"
          >
            {{
              busy
                ? L("正在保存…", "Saving…")
                : dialog === "delete"
                  ? L("确认删除", "Delete")
                  : L("确认保存", "Save")
            }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

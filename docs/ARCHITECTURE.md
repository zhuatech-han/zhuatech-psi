# 业务规则、数据库与接口

知华科技 · 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

## 状态与持久化

`DRAFT → CONFIRMED → PARTIAL → FULFILLED`；未履约且无款项的草稿/确认单可进入 `CANCELLED`。只有草稿能编辑或删除。状态判断在事务内，不能由浏览器直接指定。`FULFILLED` 指全部原订单数量已收发，原退货不重新开放履约。

订单行保存商品编码、名称、单位和价格快照。确认不记货款；实际履约才累计 `netAmount`，销售同步累计 `netCost`。款项 `netPaid` 按付款/退款/冲销记录累计，余额 `netAmount - netPaid`，负数为待退。单据及流水不混合币种；首次订单或库存流水后锁定记账币种。

19张自有表：`department`、`access_role`、`permission`、`role_permission`、`nav_menu`、`account`、`audit_event`、`system_setting`、`dictionary_entry`、`category`、`party`、`product`、`warehouse`、`trade_order`、`order_line`、`stock_balance`、`stock_movement`、`payment_entry`、`mutation_stamp`（含角色权限关联表；另有Flyway历史表）。完整定义见 `backend/src/main/resources/db/migration/V1__psi_schema.sql`。

库存数量三位小数、库存价值两位小数。移动平均出库成本为库存价值乘出库量/结存量；全部出库带走全部剩余价值。分批单据金额使用累计金额增量，避免每批独立舍入使总额不一致。销售退货使用原出库价值，采购退货使用当时平均价值；原金额/成本按累计退回比例分配，最后一批带走尾差。只有可售商品退回库存。

## 一致性和权限

事务先锁总部基础部门行，串行化本实例/多实例共享数据库的库存、订单及管理写操作；随后重新检查数据和状态。该实现便于小团队学习，需做更细的行锁、数据库聚合、压测和监控后才适合大型负载。

幂等记录使用资源与请求标识唯一键，并保存动作与载荷 SHA-256。失败事务不留标识；重复相同载荷返回当前单据状态，不重复过账；同标识不同载荷返回409。权限与部门检查在幂等返回之前执行。请求标识并不是支付渠道交易号。

每次读取和写入都从数据库检查账号启停、当前密码散列、角色权限和 `ALL/DEPARTMENT` 数据范围。Cookie 会话、CSRF、BCrypt12轮、同源Nginx。密码/散列不进入响应。业务导出重新检查权限，逐笔对账不混入其他部门的单位。后台保护至少一名启用、全范围且具有admin权限的管理员。

权限：`dashboard`、`master.read/write`、`purchase.read/write`、`sales.read/write`、`stock.read/write`、`finance`、`report`、`audit`、`admin`。内建菜单名称、顺序、启停、所需权限可配置，代码和路由不可凭空新增。

## HTTP 接口

| 接口 | 行为 |
| --- | --- |
| `GET /api/auth/csrf` | 获取会话CSRF头和令牌 |
| `POST /api/auth/login/logout/password` | 登录、退出、修改本人密码 |
| `GET /api/auth/me` | 账号、实时角色与可见菜单 |
| `GET /api/catalog` | 受限参考资料，管理员另有角色和权限目录 |
| `GET /api/lists/{resource}` | 搜索、状态、排序、页码和20条默认分页；size1–100 |
| `POST/PUT/DELETE /api/master/{type}[/{id}]` | 商品、分类、往来单位和仓库维护 |
| `POST /api/master/products/import` | 原子批量导入商品JSON |
| `GET /api/barcode?value=...` | 部门范围内条码/编码精确匹配 |
| `POST /api/orders`，`PUT/DELETE /api/orders/{id}` | 创建、修改、删除草稿 |
| `GET /api/orders/{id}` | 单据、行、货物和资金凭证、余额 |
| `POST /api/orders/{id}/{action}` | confirm/cancel/post/return/pay/refund/credit/reversePayment |
| `POST /api/stock/{action}` | opening/count/transfer |
| `GET /api/dashboard` | 工作台，财务汇总有额外权限 |
| `GET /api/reports[.csv]` | 净过账销售额、成本、货款与导出 |
| `GET /api/statement[.csv]?partyId=...` | 一单位全期间逐笔对账、累计余额和导出 |
| `POST/PUT/DELETE /api/admin/{type}[/{id}]` | 用户、角色、部门、菜单、权限目录、字典与参数 |
| `GET /actuator/health` | 启动健康检查 |

写动作需 `requestKey`（12–80字符，字母数字下划线或连字符）；收发货指定原单行ID和数量；退货指定原履约流水ID；贷记指定未实物退货的原履约ID；款项冲销指定原付款或退款ID。业务状态冲突409、字段错误400、越权403、失效登录401。每类列表最大一万条，超出413，不静默截断报表。

错误响应只返回业务代码，不返回密码、SQL或内部路径。CSV公式前缀被转义，导出不插入品牌广告；源码、系统关于页和手册保留合法署名和联系方式。

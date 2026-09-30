-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  UNIQUE (type, code)
);


CREATE TABLE category (
id bigint AUTO_INCREMENT PRIMARY KEY,
name varchar(120) NOT NULL UNIQUE,
name_en varchar(120) NOT NULL
);

CREATE TABLE party (
id bigint AUTO_INCREMENT PRIMARY KEY,
kind varchar(20) NOT NULL,
code varchar(60) NOT NULL UNIQUE,
name varchar(120) NOT NULL,
contact varchar(200) NOT NULL,
notes varchar(1000) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE product (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL UNIQUE,
barcode varchar(100) UNIQUE,
name varchar(120) NOT NULL,
specification varchar(200) NOT NULL,
unit varchar(20) NOT NULL,
category_id bigint NOT NULL,
department_id bigint NOT NULL,
sale_price decimal(18,2) NOT NULL,
purchase_price decimal(18,2) NOT NULL,
reorder_level decimal(18,3) NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(category_id) REFERENCES category(id),
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(sale_price>=0 AND purchase_price>=0 AND reorder_level>=0)
);

CREATE TABLE warehouse (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL UNIQUE,
name varchar(120) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE trade_order (
id bigint AUTO_INCREMENT PRIMARY KEY,
number varchar(60) NOT NULL UNIQUE,
kind varchar(20) NOT NULL,
party_id bigint NOT NULL,
party_name varchar(120) NOT NULL,
warehouse_id bigint NOT NULL,
department_id bigint NOT NULL,
status varchar(20) NOT NULL,
currency varchar(3) NOT NULL,
notes varchar(1000) NOT NULL,
created_at timestamp(6) NOT NULL,
created_by varchar(60) NOT NULL,
net_amount decimal(18,2) NOT NULL,
net_cost decimal(18,2) NOT NULL,
net_paid decimal(18,2) NOT NULL,
FOREIGN KEY(party_id) REFERENCES party(id),
FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE order_line (
id bigint AUTO_INCREMENT PRIMARY KEY,
order_id bigint NOT NULL,
product_id bigint NOT NULL,
product_code varchar(60) NOT NULL,
product_name varchar(120) NOT NULL,
unit varchar(20) NOT NULL,
quantity decimal(18,3) NOT NULL,
price decimal(18,2) NOT NULL,
fulfilled decimal(18,3) NOT NULL,
returned decimal(18,3) NOT NULL,
FOREIGN KEY(order_id) REFERENCES trade_order(id),
FOREIGN KEY(product_id) REFERENCES product(id),
UNIQUE(order_id,product_id),
CHECK(quantity>0 AND price>=0 AND fulfilled>=0 AND fulfilled<=quantity AND returned>=0 AND returned<=fulfilled)
);

CREATE TABLE stock_balance (
id bigint AUTO_INCREMENT PRIMARY KEY,
product_id bigint NOT NULL,
warehouse_id bigint NOT NULL,
quantity decimal(18,3) NOT NULL,
inventory_value decimal(18,2) NOT NULL,
FOREIGN KEY(product_id) REFERENCES product(id),
FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
UNIQUE(product_id,warehouse_id),
CHECK(quantity>=0 AND inventory_value>=0)
);

CREATE TABLE stock_movement (
id bigint AUTO_INCREMENT PRIMARY KEY,
product_id bigint NOT NULL,
warehouse_id bigint NOT NULL,
department_id bigint NOT NULL,
order_id bigint,
line_id bigint,
source_id bigint,
kind varchar(30) NOT NULL,
quantity decimal(18,3) NOT NULL,
inventory_value decimal(18,2) NOT NULL,
amount decimal(18,2) NOT NULL,
reference varchar(120) NOT NULL,
note varchar(500) NOT NULL,
created_at timestamp(6) NOT NULL,
created_by varchar(60) NOT NULL,
FOREIGN KEY(product_id) REFERENCES product(id),
FOREIGN KEY(warehouse_id) REFERENCES warehouse(id),
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(order_id) REFERENCES trade_order(id),
FOREIGN KEY(line_id) REFERENCES order_line(id),
FOREIGN KEY(source_id) REFERENCES stock_movement(id)
);

CREATE TABLE payment_entry (
id bigint AUTO_INCREMENT PRIMARY KEY,
reversal_of bigint NULL UNIQUE,
order_id bigint NOT NULL,
kind varchar(20) NOT NULL,
amount decimal(18,2) NOT NULL,
reference varchar(120) NOT NULL,
note varchar(500) NOT NULL,
created_at timestamp(6) NOT NULL,
created_by varchar(60) NOT NULL,
FOREIGN KEY(order_id) REFERENCES trade_order(id),
FOREIGN KEY(reversal_of) REFERENCES payment_entry(id),
CHECK(amount>0)
);

CREATE TABLE mutation_stamp (
id bigint AUTO_INCREMENT PRIMARY KEY,
resource varchar(80) NOT NULL,
request_key varchar(80) NOT NULL,
fingerprint varchar(64) NOT NULL,
UNIQUE(resource,request_key)
);
CREATE INDEX ix_order_department_time ON trade_order(department_id,created_at);
CREATE INDEX ix_order_kind_status ON trade_order(kind,status);
CREATE INDEX ix_movement_stock ON stock_movement(warehouse_id,product_id,created_at);
CREATE INDEX ix_movement_order ON stock_movement(order_id);
CREATE INDEX ix_payment_order ON payment_entry(order_id);
CREATE INDEX ix_party_department ON party(department_id);
CREATE INDEX ix_product_department ON product(department_id);
CREATE INDEX ix_audit_department_time ON audit_event(department_id,created_at);

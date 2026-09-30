# 部署、升级与数据库恢复

知华科技 · 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

## 隔离安装

使用 Docker Compose v2，复制 `.env.example` 为 `.env`，填三个独立强密码。首次管理员密码12–72位并含大小写字母及数字。`.env`保存在服务器受限目录，不上传仓库。

```sh
docker compose up -d --build --wait
docker compose ps
```

运行容器使用官方 Maven/Temurin Java 21 镜像中的 JDK，以与构建阶段复用同一基础镜像；应用以非 root 账号运行。镜像包含构建工具，体积比专用 JRE 镜像大。

默认访问 `http://127.0.0.1:8096/`，健康 `/actuator/health`。端口冲突设置 `WEB_PORT`。独立测试用 `docker compose -p psi-check --env-file /path/to/test.env ...`，只有明确属于测试项目的容器/卷才能删除。MySQL数据使用持久化卷；生产不能执行`down -v`。

首次`SEED_DEMO=true`仅加入虚构主数据；没有付款、库存或默认员工密码。已存在管理员后初始化器不再覆盖数据。

## 公网和外部数据库

保持默认仅127.0.0.1绑定，通过自己的可信HTTPS反向代理对外开放，设置`COOKIE_SECURE=true`，代理正确传递受信任的Host及协议。不要开放MySQL端口或共享管理员。限制网关请求频率、备份访问、会话范围，监控健康与异常；本版登录限流和会话仅为单进程，扩展多实例需配置共享会话和统一限流。

驱动为MariaDB Connector/J 3.5.10，连接MySQL8.4。Compose使用隔离内网及`sslMode=trust`；外部数据库须使用`sslMode=verify-full`和可信CA证书，启用数据库TLS并验证主机名。不要把真实连接凭证写在源码、远程地址或公开日志里。

## 备份与恢复

备份包含业务库、Flyway历史和管理员散列，应按敏感业务数据存储，权限仅授权运维人员。以下命令只在自己的授权服务器执行；密码从容器环境读取，不写入命令参数或公开输出。

```sh
umask 077
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers zhuatech_psi' > psi-backup.sql
```

恢复先准备独立项目、空库及匹配应用版本，不直接覆盖现有生产数据。仅启动独立MySQL，等健康后将备份导入；再启动对应版本的后端和前端，核对登录、表数、订单、库存、往来款、Flyway版本。

```sh
# 在已确认的恢复测试项目目录执行
# 使用与备份匹配的数据库名和版本
docker compose -p psi-restore up -d mysql --wait
docker compose -p psi-restore exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot zhuatech_psi' < psi-backup.sql
docker compose -p psi-restore up -d --wait
```

备份恢复密码来自备份中的账号散列；恢复环境的ADMIN_PASSWORD不会覆盖。验证完只清理恢复测试项目的容器/卷及敏感备份。

## 升级与故障

升级前记录版本和校验值，备份并验证恢复，查看新增Flyway迁移。新迁移只追加，不修改已执行脚本。JPA只做结构校验，不能用自动建表绕过迁移失败。回退应用前确认是否能兼容新库，必要时恢复已验证备份。

健康失败先查看`docker compose logs mysql backend frontend`；请先脱敏，再提交故障信息。登录失败检查空库初始化与原账号，不随意清空卷。403核对角色、部门和CSRF；库存不足或盘点过期应刷新业务数据。持久化成功但浏览器超时，先查询原凭证再用同标识重试，不重新登记付款。

公开试用实例必须另行设计数据隔离和清理周期，不直接开放这套业务数据库。任何真实支付、税务或第三方连接均不属于本版已验收范围。

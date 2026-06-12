# puregxl.site 个人博客部署设计

日期：2026-06-12
状态：已获用户批准（聊天确认）

## 1. 背景与目标

用户（gaoxiaolei-s59）希望在自有腾讯云服务器上部署个人网站：

- 自己能方便地添加博客文章
- 统计访问量
- 前端精美
- 与 GitHub 关联

### 服务器现状（2026-06-12 实测）

- 101.35.5.210，Ubuntu 22.04，4C / 3.3G 内存 / 40G 磁盘（剩 25G）
- 内存已用 2.9G（约 88%），**无 swap**；跑着 MySQL 5.7、Redis、Nacos 2.1.2（Docker）及多个 Java 应用（端口 8000/8003）
- nginx 1.18 占用 80 端口：`puregxl.site` → 短链接前端（/home/shortlink/dist + /api→8000），`s.puregxl.site` → 8003 短链跳转
- Docker 26.1.3 可用；安全组 22/80/443 均放行
- 登录：ubuntu 用户，密码认证，sudo 免密
- 域名 puregxl.site 已 ICP 备案，未配 HTTPS；DNS：根域名/www/s 已指向本机

### 关键约束

- 可用内存仅约 180MB → **构建和重型服务不得放在服务器上**
- 现有短链接服务（已对外发出的短链）不可中断
- 境内服务器 + 已备案域名：站点页脚需展示 ICP 备案号（合规要求）

## 2. 已确认的决策

| 决策点 | 结论 |
|---|---|
| 网站形态 | 静态站（Astro + Fuwari 主题），Markdown 写作，Git 推送发布 |
| 域名分配 | 个人站占 `puregxl.site` + `www`（default_server）；短链接管理页平移到 `link.puregxl.site`；`s.puregxl.site` 不动 |
| HTTPS | certbot（Let's Encrypt）签发 puregxl.site/www/link/s/stats，全站 80→443 |
| 访问统计 | 自托管 GoatCounter（Docker，127.0.0.1:8081，SQLite），`stats.puregxl.site` 反代 |
| GitHub | 新建 public 仓库 `gaoxiaolei-s59/puregxl.site`，GitHub Actions 构建 + rsync 部署 |
| 加固 | 加 2G swap 文件；为本机加 SSH 公钥；建议用户事后改密码 |

## 3. 架构

```
写 .md ─push→ GitHub repo ─Actions(pnpm build)→ rsync over SSH
                                                    ▼
                nginx @ 101.35.5.210
                  ├─ puregxl.site / www / 裸IP → /var/www/blog（静态，default_server）
                  ├─ link.puregxl.site → /home/shortlink/dist + /api→127.0.0.1:8000
                  ├─ s.puregxl.site    → 127.0.0.1:8003（原样）
                  └─ stats.puregxl.site→ 127.0.0.1:8081（GoatCounter 容器）
```

## 4. 组件设计

### 4.1 内容仓库（gaoxiaolei-s59/puregxl.site）

- 基于 Fuwari 模板（https://github.com/saicaca/fuwari）初始化，pnpm 管理
- 定制项：站点标题/副标题、作者资料卡（头像、GitHub 链接 → 满足"链接到 GitHub"）、中文站点语言、About 页骨架、页脚 ICP 备案号（从现有短链前端页脚抓取，找不到则向用户索要）
- 每页注入 GoatCounter 统计脚本（指向 https://stats.puregxl.site/count）
- 写作：`pnpm new-post <名称>` 生成 frontmatter 模板；或直接在 github.com 网页编辑

### 4.2 CI/CD（.github/workflows/deploy.yml）

- 触发：push 到 main
- 步骤：checkout → pnpm install → astro build → rsync `dist/` 到服务器 `/var/www/blog-incoming/` → 服务器端 `rsync --delete` 切换到 `/var/www/blog/`（两段式，避免访客看到半成品）
- 凭证：专用 ed25519 密钥对——公钥进服务器 `~ubuntu/.ssh/authorized_keys`，私钥进仓库 Secret `DEPLOY_SSH_KEY`；`SSH_HOST`/`SSH_USER` 同为 Secrets
- 失败行为：rsync 失败则线上保持旧版本，Actions 页面可见红叉

### 4.3 nginx 重排（先备份 /etc/nginx 整目录）

- 短链 server block：仅改 `server_name puregxl.site` → `link.puregxl.site`，root 与 /api 代理保持原样
- 新增博客 block：`server_name puregxl.site www.puregxl.site` + `default_server`，root `/var/www/blog`，gzip、静态缓存头
- 新增 stats block：反代 127.0.0.1:8081
- 每步 `nginx -t` 通过才 reload；reload 不中断现有连接

### 4.4 GoatCounter

- 镜像 `zgoat/goatcounter`，容器 `--restart always`，数据卷挂 SQLite 文件
- 站点账号：用户邮箱 + 生成的初始密码（交付时告知，提示修改）
- Docker Hub 拉取失败的备选：GitHub Releases 静态二进制 + systemd unit

### 4.5 HTTPS

- snap 安装 certbot + nginx 插件，一次申请 5 个域名证书，自动续期（systemd timer）
- 80→443 301 重定向；`http://s.puregxl.site/xxx` 老短链经 301 保留路径，不失效

### 4.6 服务器加固

- `/swapfile` 2G，`swappiness=10`，写入 /etc/fstab——保护现有 Java 服务免于 OOM
- 把本机（开发机）SSH 公钥加入 authorized_keys，后续维护免密码
- 交付后建议用户更换 ubuntu 密码（已在聊天中明文暴露）

## 5. 错误处理与回滚

| 风险 | 处置 |
|---|---|
| nginx 配置错误 | 改前备份；`nginx -t` 不过不 reload；恢复备份目录 + reload 秒级回滚 |
| certbot 签发失败 | 80 端口服务不受影响；可重试或暂缓 443 |
| Docker Hub 不可达 | 换二进制 + systemd（见 4.4） |
| Actions 构建失败 | 线上不变；按日志修复后重新 push |
| DNS 未生效 | link/stats 解析未就绪前不切短链 server_name，避免管理页空窗 |

## 6. 验收标准

1. `https://puregxl.site` 打开 Fuwari 博客，样式/深浅色/归档/搜索正常，页脚有备案号
2. `https://link.puregxl.site` 短链管理页可登录、API 正常
3. 既有短链 `s.puregxl.site/xxx` 跳转正常（http 与 https 均可）
4. push 一篇测试文章，≤2 分钟自动上线
5. stats.puregxl.site 仪表盘记录到上述访问
6. MySQL/Redis/Nacos/Java 应用全程无重启；`free` 显示 swap 已启用

## 7. 用户待办

- 在腾讯云 DNSPod 给 puregxl.site 添加 A 记录：`link` → 101.35.5.210、`stats` → 101.35.5.210
- 交付后：修改服务器密码；登录 GoatCounter 改初始密码

## 8. 明确不做（YAGNI）

- 评论系统、RSS 之外的订阅、每篇文章页内阅读量展示（GoatCounter 支持，列为未来可选）
- 服务器上安装 Node/构建工具链
- 迁移或改造短链接项目本身

## 9. 未来可选

- 文章页内显示阅读量（GoatCounter visitor-counter 嵌入）
- link/stats 子域加 HTTP Basic Auth 二次保护
- 博客仓库加 Decap CMS 实现网页端写作

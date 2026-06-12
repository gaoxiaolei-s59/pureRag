# puregxl.site 个人博客部署实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在腾讯云服务器 101.35.5.210 上部署 Astro+Fuwari 静态博客（puregxl.site），GitHub Actions 自动构建部署，GoatCounter 自托管统计，certbot 全站 HTTPS，且不中断既有短链接服务。

**Architecture:** 构建全部发生在 GitHub Actions（服务器内存仅剩 ~180MB），服务器只用 nginx 伺服静态文件。短链接前端从根域名平移到 link.puregxl.site；GoatCounter 以 systemd 二进制方式跑在 127.0.0.1:8081（Docker Hub 境内不稳，二进制路径更确定，符合 spec 4.4 备选条款）。

**Tech Stack:** Astro + Fuwari 主题、pnpm、GitHub Actions、rsync over SSH、nginx 1.18、certbot(snap)、GoatCounter(SQLite)、systemd。

**Spec:** `docs/superpowers/specs/2026-06-12-personal-site-design.md`

**关键事实（实施时直接用）：**
- 服务器：`ubuntu@101.35.5.210`，密码认证（sshpass），sudo 免密
- 本机 gh 已登录 `gaoxiaolei-s59`（repo+workflow 权限）
- 现有 nginx 配置只有 `/etc/nginx/sites-enabled/default` 一个文件（两个 server block：puregxl.site 短链前端 / s.puregxl.site 跳转）
- 用户邮箱：chaungoc0912@gmail.com
- 本地博客工作目录（新建）：`/Users/gaoxaiolei/IdeaProjects/puregxl.site`
- 所有 ssh 命令模板：`SSHPASS='<服务器密码>' sshpass -e ssh ubuntu@101.35.5.210 '<cmd>'`（Task 1 装上密钥后改用普通 ssh）

---

### Task 0: 前置检查（本地工具 + DNS）

**Files:** 无

- [ ] **Step 1: 本地工具检查**

```bash
node -v; pnpm -v || corepack enable && pnpm -v; rsync --version | head -1; gh auth status
```

预期：node ≥ 20（不足则 `brew install node`）；pnpm 可用（corepack 启用即可）；gh 已登录 gaoxiaolei-s59。

- [ ] **Step 2: 检查 link/stats DNS 是否已加（用户待办）**

```bash
sshpass -e ssh ubuntu@101.35.5.210 'for d in link.puregxl.site stats.puregxl.site; do echo -n "$d -> "; dig +short $d A @119.29.29.29; done'
```

预期：两条都返回 `101.35.5.210`。
**若为空：** 不阻塞 Task 1–5（构建链路与域名无关），但 Task 6 的短链平移和 Task 7/8 必须等记录生效；届时提醒用户去 DNSPod 添加并等 1–5 分钟。

### Task 1: 服务器加固（备份、swap、SSH 密钥）

**Files:**
- 服务器 Create: `/root/nginx-backup-20260612.tar.gz`、`/swapfile`、`/etc/sysctl.d/99-swap.conf`
- 服务器 Modify: `~ubuntu/.ssh/authorized_keys`、`/etc/fstab`

- [ ] **Step 1: 备份 nginx 配置全目录**

```bash
sshpass -e ssh ubuntu@101.35.5.210 'sudo tar czf /root/nginx-backup-20260612.tar.gz /etc/nginx && sudo tar tzf /root/nginx-backup-20260612.tar.gz | head -3'
```

预期：列出 etc/nginx/ 开头的文件名。**这是所有 nginx 改动的回滚点。**

- [ ] **Step 2: 加 2G swap（spec 4.6）**

```bash
sshpass -e ssh ubuntu@101.35.5.210 'sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile && echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab && echo "vm.swappiness=10" | sudo tee /etc/sysctl.d/99-swap.conf && sudo sysctl -p /etc/sysctl.d/99-swap.conf && free -h'
```

预期：`free -h` 显示 `Swap: 2.0Gi`。

- [ ] **Step 3: 安装本机 SSH 公钥（之后免密码）**

```bash
[ -f ~/.ssh/id_ed25519.pub ] || ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519
sshpass -e ssh ubuntu@101.35.5.210 "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo '$(cat ~/.ssh/id_ed25519.pub)' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
ssh -o PasswordAuthentication=no ubuntu@101.35.5.210 'echo KEY-OK'
```

预期：最后一行输出 `KEY-OK`。**此后所有 ssh 命令不再用 sshpass。**

### Task 2: 本地脚手架 Fuwari 并定制

**Files:**
- Create: `/Users/gaoxaiolei/IdeaProjects/puregxl.site/`（整个项目，来自 Fuwari 模板）
- Modify: `src/config.ts`（站点/作者信息）、`src/components/Footer.astro`（ICP 备案号）、`src/layouts/Layout.astro`（GoatCounter 脚本）

- [ ] **Step 1: 克隆模板并重置 git**

```bash
cd /Users/gaoxaiolei/IdeaProjects && git clone --depth 1 https://github.com/saicaca/fuwari.git puregxl.site && cd puregxl.site && rm -rf .git && git init -b main && git add -A && git commit -m "chore: init from fuwari template"
```

预期：commit 成功。

- [ ] **Step 2: 定制 src/config.ts**

按 Fuwari 实际字段结构修改以下值（字段名以仓库内 `src/config.ts` 现状为准，改值不改结构）：

```ts
// siteConfig
title: 'PureGXL'
subtitle: '高小磊的个人博客'
lang: 'zh_CN'
// profileConfig
name: 'PureGXL'
bio: '记录开发与学习'   // 占位文案，用户后续自改
links: [{ name: 'GitHub', icon: 'fa6-brands:github', url: 'https://github.com/gaoxiaolei-s59' }]
```

- [ ] **Step 3: 页脚加 ICP 备案号**

先从短链前端找备案号：

```bash
ssh ubuntu@101.35.5.210 'grep -rhoE "[^<>\"'"'"']*ICP备[0-9]+号(-[0-9]+)?" /home/shortlink/dist 2>/dev/null | sort -u'
```

预期：输出形如 `蜀ICP备2024xxxxxx号`。**若为空：暂停，问用户要备案号。**
然后在 `src/components/Footer.astro` 的版权行下方加：

```html
<a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener">蜀ICP备2024xxxxxx号</a><!-- 用上一步查到的真实备案号替换 -->
```

- [ ] **Step 4: 注入 GoatCounter 统计脚本（spec 4.1）**

在 `src/layouts/Layout.astro` 的 `<head>` 内（任意现有 script 旁）加：

```html
<script is:inline data-goatcounter="https://stats.puregxl.site/count" async src="https://stats.puregxl.site/count.js"></script>
<script is:inline>
  // Fuwari 用 swup 做无刷新切页，count.js 只统计首次加载，这里补报后续页面
  (function hook() {
    if (window.swup && window.swup.hooks) {
      window.swup.hooks.on('page:view', function () {
        if (window.goatcounter && window.goatcounter.count) {
          window.goatcounter.count({ path: location.pathname });
        }
      });
    } else { setTimeout(hook, 500); }
  })();
</script>
```

- [ ] **Step 5: 本地构建验证**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && pnpm install && pnpm build && ls dist/index.html && grep -c "stats.puregxl.site" dist/index.html
```

预期：`dist/index.html` 存在；grep 计数 ≥ 1（统计脚本已进产物）。

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: 站点信息、ICP 页脚、GoatCounter 统计接入"
```

### Task 3: 创建 GitHub 仓库并推送

**Files:** 无新文件（远端仓库）

- [ ] **Step 1: 建 public 仓库并推送**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && gh repo create gaoxiaolei-s59/puregxl.site --public --source . --remote origin --push
```

预期：输出仓库 URL；`git ls-remote origin main` 有提交。

### Task 4: 部署密钥、Secrets 与服务器目录

**Files:**
- 本地 Create: `/Users/gaoxaiolei/.ssh/puregxl_deploy`(+.pub)（专用部署密钥，不入仓库）
- 服务器 Create: `/var/www/blog/`、`/var/www/blog-incoming/`
- 服务器 Modify: `~ubuntu/.ssh/authorized_keys`

- [ ] **Step 1: 生成专用部署密钥并装到服务器**

```bash
ssh-keygen -t ed25519 -N "" -f ~/.ssh/puregxl_deploy -C "github-actions-deploy"
ssh ubuntu@101.35.5.210 "echo '$(cat ~/.ssh/puregxl_deploy.pub)' >> ~/.ssh/authorized_keys"
ssh -i ~/.ssh/puregxl_deploy -o IdentitiesOnly=yes ubuntu@101.35.5.210 'echo DEPLOY-KEY-OK'
```

预期：`DEPLOY-KEY-OK`。

- [ ] **Step 2: 配置仓库 Secrets**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site
gh secret set DEPLOY_SSH_KEY < ~/.ssh/puregxl_deploy
gh secret set SSH_HOST --body "101.35.5.210"
gh secret set SSH_USER --body "ubuntu"
gh secret list
```

预期：列出 3 个 secret。

- [ ] **Step 3: 建服务器目录**

```bash
ssh ubuntu@101.35.5.210 'sudo mkdir -p /var/www/blog /var/www/blog-incoming && sudo chown -R ubuntu:ubuntu /var/www/blog /var/www/blog-incoming && ls -ld /var/www/blog*'
```

预期：两个目录属主 ubuntu。

### Task 5: GitHub Actions 工作流与首次部署

**Files:**
- Create: `/Users/gaoxaiolei/IdeaProjects/puregxl.site/.github/workflows/deploy.yml`

- [ ] **Step 1: 写 workflow**

```yaml
name: Deploy Blog
on:
  push:
    branches: [main]
  workflow_dispatch:
concurrency:
  group: deploy
  cancel-in-progress: true
jobs:
  build-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
        with:
          version: 9
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: pnpm
      - run: pnpm install --frozen-lockfile
      - run: pnpm build
      - name: Setup SSH
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.DEPLOY_SSH_KEY }}" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          ssh-keyscan -H "${{ secrets.SSH_HOST }}" >> ~/.ssh/known_hosts
      - name: Deploy (两段式，避免半成品页面)
        run: |
          rsync -az --delete -e "ssh -i ~/.ssh/id_ed25519" dist/ "${{ secrets.SSH_USER }}@${{ secrets.SSH_HOST }}:/var/www/blog-incoming/"
          ssh -i ~/.ssh/id_ed25519 "${{ secrets.SSH_USER }}@${{ secrets.SSH_HOST }}" "rsync -a --delete /var/www/blog-incoming/ /var/www/blog/"
```

注：若 Fuwari 的 package.json 带 `packageManager` 字段且与 9 冲突，去掉 `version: 9` 让 action 读字段。

- [ ] **Step 2: 推送并跟踪运行**

```bash
git add .github && git commit -m "ci: GitHub Actions 自动构建部署" && git push
gh run watch --exit-status
```

预期：run 绿色通过。失败则 `gh run view --log-failed` 排查（常见：secret 名拼错、ssh 超时）。

- [ ] **Step 3: 验证文件落盘**

```bash
ssh ubuntu@101.35.5.210 'ls /var/www/blog/index.html && du -sh /var/www/blog'
```

预期：index.html 存在，目录有内容（几 MB）。

### Task 6: nginx 重排（短链平移 + 博客上线 + stats 块）

**Files:**
- 服务器 Create: `/etc/nginx/sites-available/blog`、`/etc/nginx/sites-available/shortlink`、`/etc/nginx/sites-available/stats`
- 服务器 Modify: `/etc/nginx/sites-enabled/`（替换 default 软链）

**前置门槛：** Task 0 Step 2 的 link/stats DNS 必须已解析，否则短链管理页会出现空窗——未就绪就停下提醒用户。

- [ ] **Step 1: 读取现有完整配置（平移时保真）**

```bash
ssh ubuntu@101.35.5.210 'cat /etc/nginx/sites-available/default'
```

记录短链 server block 的全部指令（root、/api proxy_pass 等），下一步原样平移。

- [ ] **Step 2: 写三个新 vhost 文件**

`shortlink`（把原 puregxl.site block 整体复制，只改 server_name；s.puregxl.site block 原样保留在此文件）：

```nginx
server {
    listen 80;
    server_name link.puregxl.site;
    # ↓ 以下从原配置原样平移（root /home/shortlink/dist、/api 代理 8000 等）
    location / { root /home/shortlink/dist; index index.html; try_files $uri $uri/ /index.html; }
    location /api { proxy_pass http://127.0.0.1:8000/api; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; }
}
server {
    listen 80;
    server_name s.puregxl.site;
    location / { proxy_pass http://127.0.0.1:8003; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; }
}
```

`blog`：

```nginx
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name puregxl.site www.puregxl.site;
    root /var/www/blog;
    index index.html;
    location / { try_files $uri $uri/index.html $uri.html =404; }
    location ~* \.(js|css|png|jpg|jpeg|gif|webp|svg|woff2?|ico)$ { expires 7d; add_header Cache-Control "public"; }
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml text/xml;
    error_page 404 /404.html;
}
```

`stats`：

```nginx
server {
    listen 80;
    server_name stats.puregxl.site;
    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**注意：** Step 1 读到的真实配置若与上面模板有出入（额外 header、client_max_body_size 等），以真实配置为准平移，不丢任何指令。

- [ ] **Step 3: 切换软链并验证-重载**

```bash
ssh ubuntu@101.35.5.210 'cd /etc/nginx/sites-enabled && sudo rm -f default && sudo ln -sf ../sites-available/shortlink ../sites-available/blog ../sites-available/stats . && sudo nginx -t && sudo systemctl reload nginx'
```

预期：`nginx -t` 输出 `syntax is ok / test is successful`。**若 -t 失败：不 reload，恢复 `sudo rm 新软链 && sudo ln -s ../sites-available/default .`，线上无影响。**

- [ ] **Step 4: HTTP 烟雾测试**

```bash
curl -sI http://puregxl.site/ | head -1
curl -sI http://www.puregxl.site/ | head -1
curl -s http://link.puregxl.site/ | head -3
curl -sI http://101.35.5.210/ | head -1
```

预期：根域名/www/裸IP 返回 200 且是博客 HTML；link 返回短链前端 HTML（`<title>短链接</title>`）。
（若本机 DNS 被代理污染，用 `curl --resolve puregxl.site:80:101.35.5.210 ...` 或在服务器上 curl。）

### Task 7: GoatCounter 部署（二进制 + systemd）

**Files:**
- 服务器 Create: `/usr/local/bin/goatcounter`、`/opt/goatcounter/goatcounter.sqlite3`、`/etc/systemd/system/goatcounter.service`

- [ ] **Step 1: 下载最新 linux-amd64 二进制（本机下载再 scp，绕开服务器到 GitHub 的不稳定链路）**

```bash
URL=$(gh api repos/arp242/goatcounter/releases/latest --jq '.assets[] | select(.name | test("linux-amd64")) | .browser_download_url')
curl -L -o /tmp/goatcounter.gz "$URL" && gunzip -f /tmp/goatcounter.gz && chmod +x /tmp/goatcounter
scp /tmp/goatcounter ubuntu@101.35.5.210:/tmp/ && ssh ubuntu@101.35.5.210 'sudo mv /tmp/goatcounter /usr/local/bin/ && goatcounter version'
```

预期：打印版本号（v2.x）。

- [ ] **Step 2: 建用户、初始化站点（密码本步生成并记录）**

```bash
GC_PASS=$(openssl rand -base64 12)
echo "GoatCounter 初始密码: $GC_PASS"   # 记录到交付清单
ssh ubuntu@101.35.5.210 "sudo useradd -r -s /usr/sbin/nologin -d /opt/goatcounter goatcounter 2>/dev/null; sudo mkdir -p /opt/goatcounter && sudo chown goatcounter:goatcounter /opt/goatcounter && sudo -u goatcounter goatcounter db create site -vhost stats.puregxl.site -user.email chaungoc0912@gmail.com -password '$GC_PASS' -db sqlite+/opt/goatcounter/goatcounter.sqlite3 -createdb"
```

预期：无报错（命令行参数如因版本差异报错，按 `goatcounter help db` 调整子命令拼写）。

- [ ] **Step 3: systemd 服务**

写 `/etc/systemd/system/goatcounter.service`：

```ini
[Unit]
Description=GoatCounter web analytics
After=network.target

[Service]
User=goatcounter
ExecStart=/usr/local/bin/goatcounter serve -listen 127.0.0.1:8081 -tls http -db sqlite+/opt/goatcounter/goatcounter.sqlite3
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
ssh ubuntu@101.35.5.210 'sudo systemctl daemon-reload && sudo systemctl enable --now goatcounter && sleep 2 && systemctl is-active goatcounter && curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8081/'
```

预期：`active` + HTTP 200/302。（`-tls http` 含义为"纯 HTTP、TLS 交给反代"；若该版本不接受此值，看 `goatcounter help serve` 用对应的 proxy 选项。）

- [ ] **Step 4: 经 nginx 访问验证**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://stats.puregxl.site/
```

预期：200（登录页）。

### Task 8: certbot 全域名 HTTPS

**Files:**
- 服务器 Modify: nginx 三个 vhost（certbot 自动加 443 块和 301）

- [ ] **Step 1: 安装并签发**

```bash
ssh ubuntu@101.35.5.210 'sudo snap install --classic certbot 2>/dev/null || sudo apt-get install -y certbot python3-certbot-nginx; sudo ln -sf /snap/bin/certbot /usr/bin/certbot 2>/dev/null; sudo certbot --nginx --redirect -m chaungoc0912@gmail.com --agree-tos --no-eff-email -d puregxl.site -d www.puregxl.site -d link.puregxl.site -d s.puregxl.site -d stats.puregxl.site'
```

预期：`Successfully deployed certificate`。失败常见原因：DNS 未生效（等几分钟重试）、80 被占线（不会，nginx 插件用现有 nginx）。

- [ ] **Step 2: 验证 HTTPS 与 301**

```bash
for d in puregxl.site www.puregxl.site link.puregxl.site stats.puregxl.site; do echo -n "$d: "; curl -s -o /dev/null -w "%{http_code}\n" https://$d/; done
curl -sI http://puregxl.site/ | grep -i "^location"
ssh ubuntu@101.35.5.210 'sudo certbot renew --dry-run 2>&1 | tail -2'
```

预期：四个域名 200；http 返回 `Location: https://...`；dry-run 成功（自动续期就绪）。

### Task 9: 端到端验收（spec 第 6 节全项）

**Files:**
- Create: `/Users/gaoxaiolei/IdeaProjects/puregxl.site/src/content/posts/hello-world.md`

- [ ] **Step 1: 发测试文章**

```bash
cd /Users/gaoxaiolei/IdeaProjects/puregxl.site && pnpm new-post hello-world
```

编辑生成的 md，正文写一句"博客已上线"。（`new-post` 脚本若生成路径不同，以 Fuwari 仓库 README 为准；frontmatter 含 title/published/tags 字段。）

```bash
git add -A && git commit -m "post: hello world" && git push && gh run watch --exit-status
```

预期：≤2 分钟 run 通过。

- [ ] **Step 2: 验证文章上线 + 统计记录**

```bash
curl -s https://puregxl.site/ | grep -o "hello-world" | head -1
curl -s "https://puregxl.site/posts/hello-world/" -o /dev/null -w "%{http_code}\n"
```

预期：首页出现文章链接、文章页 200。然后浏览器开几个页面，登录 https://stats.puregxl.site 确认仪表盘有今日 PV。

- [ ] **Step 3: 既有服务健康检查（spec 验收 ②③⑥）**

```bash
curl -s https://link.puregxl.site/ | grep -o "<title>[^<]*" 
curl -sI https://s.puregxl.site/ | head -1
ssh ubuntu@101.35.5.210 'sudo docker ps --format "{{.Names}} {{.Status}}"; systemctl is-active nginx goatcounter; free -h | grep -E "Mem|Swap"'
```

预期：短链管理页 title 正常；s 域名响应（任意 2xx/3xx/404 都算服务活着）；mysql/redis/nacos2 状态 Up 且无 restart；swap 2G 在用。

### Task 10: 收尾交付

**Files:**
- Create: `/Users/gaoxaiolei/IdeaProjects/puregxl.site/README.md`（写作指南）

- [ ] **Step 1: 写 README 写作指南并推送**

内容必须包含：`pnpm new-post xxx` → 编辑 md → `git push` 即发布；github.com 网页直接编辑也可；frontmatter 字段说明；本地预览 `pnpm dev`；图片放 `src/assets/` 或文章同目录的用法。

```bash
git add README.md && git commit -m "docs: 写作与发布指南" && git push
```

- [ ] **Step 2: 用户交付清单（聊天里发给用户）**

- 网站：https://puregxl.site （根域名+www+裸IP 都到博客）
- 短链管理页新地址：https://link.puregxl.site （旧短链 s.puregxl.site 全部照常）
- 统计：https://stats.puregxl.site，账号 chaungoc0912@gmail.com，初始密码 = Task 7 Step 2 生成值，**提醒登录后修改**
- 仓库：https://github.com/gaoxiaolei-s59/puregxl.site
- **安全提醒：** 服务器密码已在聊天明文出现，建议尽快修改（本机和 Actions 都已用密钥，不受影响）
- 回滚说明：nginx 备份在 /root/nginx-backup-20260612.tar.gz

---

## Self-Review 记录

1. **Spec 覆盖：** spec §2 决策表逐项 → 静态站+Fuwari(T2)、域名分配(T6)、HTTPS(T8)、GoatCounter(T7)、GitHub+Actions(T3/T4/T5)、swap+密钥+改密提醒(T1/T10)；§4.1 ICP 页脚+GitHub 链接(T2)；§5 回滚（T1 备份、T6 Step3 回滚路径）；§6 验收(T9)；§7 用户待办（T0 DNS 检查、T10 交付清单）。无缺口。
2. **占位符扫描：** ICP 备案号为运行时查询值（T2 Step3 给出查询命令与问用户的回退）；GoatCounter 密码为运行时生成（命令已给）。无 TBD/"适当处理"类表述。
3. **一致性：** 端口 8081、路径 /var/www/blog、/var/www/blog-incoming、密钥文件名 puregxl_deploy、secret 名 DEPLOY_SSH_KEY/SSH_HOST/SSH_USER 在 T4/T5/T6/T7 间一致。

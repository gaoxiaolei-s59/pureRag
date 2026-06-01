import { Bot, KeyRound, Loader2, LogIn } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useAuthGuard } from "../../../hooks/useAuthGuard";
import { login, logout } from "../services/auth";
import {
  getStoredToken,
  getStoredUserName,
  setStoredToken,
  setStoredUserId,
  setStoredUserName
} from "../storage";

export function LoginPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthGuard();
  const [tokenDraft, setTokenDraft] = useState(getStoredToken());
  const [userName, setUserName] = useState(getStoredUserName());
  const [password, setPassword] = useState("123456");
  const [notice, setNotice] = useState("登录后即可进入新的模块化后台");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setTokenDraft(getStoredToken());
  }, []);

  if (isAuthenticated) {
    return <Navigate to="/chat" replace />;
  }

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      const response = await login(userName, password);
      setStoredToken(response.token);
      setStoredUserName(userName);
      setStoredUserId(response.userId ?? "");
      navigate("/chat", { replace: true });
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "登录失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveToken() {
    const nextToken = tokenDraft.trim();
    if (!nextToken) {
      try {
        await logout();
      } catch {
        // token 已空时忽略后端退出错误
      }
    }
    setStoredToken(nextToken);
    if (nextToken) {
      setStoredUserName(userName || "admin");
      navigate("/chat", { replace: true });
      return;
    }
    setNotice("Token 已清空");
  }

  return (
    <main className="login-shell">
      <section className="login-hero-panel">
        <div className="login-hero-copy">
          <span className="eyebrow">RAG Frontend Refactor</span>
          <h1>把问答、知识库和意图树拆成真正可维护的前端模块</h1>
          <p>
            这次重构不只是换皮。现在的控制台已经切成真实路由结构，后续页面可以独立迭代，不需要再回到巨型
            `App.tsx` 里找逻辑。
          </p>
        </div>
        <div className="login-feature-list">
          <div>
            <strong>真实路由</strong>
            <span>`/chat`、`/knowledge`、`/intent-tree` 可直接访问</span>
          </div>
          <div>
            <strong>模块 service</strong>
            <span>认证、聊天、知识库、意图树各自收口</span>
          </div>
          <div>
            <strong>更清楚的后台层级</strong>
            <span>导航、工作区、详情区职责分离</span>
          </div>
        </div>
      </section>

      <section className="login-form-panel">
        <div className="login-brand">
          <div className="brand-mark">
            <Bot size={24} />
          </div>
          <div>
            <strong>PureAgent</strong>
            <span>Knowledge Console</span>
          </div>
        </div>

        <form className="login-form" onSubmit={handleLogin}>
          <label>
            <span>用户名</span>
            <input value={userName} onChange={(event) => setUserName(event.target.value)} placeholder="admin" />
          </label>
          <label>
            <span>密码</span>
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="请输入密码"
              type="password"
            />
          </label>
          <button type="submit" className="gradient-button" disabled={loading}>
            {loading ? <Loader2 className="spin" size={16} /> : <LogIn size={16} />}
            登录并进入
          </button>
        </form>

        <div className="login-divider">或使用已有 Token 进入</div>

        <div className="token-panel">
          <textarea
            value={tokenDraft}
            onChange={(event) => setTokenDraft(event.target.value)}
            rows={4}
            placeholder="粘贴 s-token"
          />
          <button type="button" className="outline-button" onClick={() => void handleSaveToken()}>
            <KeyRound size={16} />
            保存 Token
          </button>
        </div>

        <p className="notice-text">{notice}</p>
      </section>
    </main>
  );
}

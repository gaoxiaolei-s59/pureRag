import {
  Bot,
  Database,
  Layers3,
  LogOut,
  MessageSquare
} from "lucide-react";
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuthGuard } from "../../hooks/useAuthGuard";
import { clearAuthStorage, getStoredUserName } from "../../modules/auth/storage";
import { logout } from "../../modules/auth/services/auth";

export function DashboardLayout() {
  const { isAuthenticated } = useAuthGuard();
  const navigate = useNavigate();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  async function handleLogout() {
    try {
      await logout();
    } catch {
      // 后端登出失败时仍清理本地态，避免用户卡住
    }
    clearAuthStorage();
    navigate("/login", { replace: true });
  }

  return (
    <main className="dashboard-shell">
      <aside className="dashboard-sidebar">
        <div className="brand-block">
          <div className="brand-mark">
            <Bot size={22} />
          </div>
          <div>
            <strong>PureAgent 管理后台</strong>
            <span>Knowledge Console</span>
          </div>
        </div>

        <nav className="dashboard-nav">
          <NavLink to="/chat" className="nav-link">
            <MessageSquare size={18} />
            智能问答
          </NavLink>
          <NavLink to="/knowledge" className="nav-link">
            <Database size={18} />
            知识库管理
          </NavLink>
          <NavLink to="/intent-tree" className="nav-link">
            <Layers3 size={18} />
            意图树配置
          </NavLink>
        </nav>

        <div className="sidebar-card">
          <span className="sidebar-card-label">当前分区</span>
          <strong>{location.pathname.includes("intent") ? "意图配置" : "知识管理"}</strong>
          <p>集中管理知识库、文档处理和意图树配置，聊天入口保持在独立前台页面。</p>
        </div>

        <button type="button" className="outline-button sidebar-action" onClick={() => void handleLogout()}>
          <LogOut size={16} />
          退出登录
        </button>
      </aside>

      <section className="dashboard-main">
        <header className="dashboard-topbar">
          <div>
            <span className="eyebrow">Admin Console</span>
            <h1>知识库与意图管理</h1>
          </div>
          <div className="dashboard-topbar-actions">
            <NavLink to="/chat" className="outline-button">
              <MessageSquare size={16} />
              返回聊天
            </NavLink>
            <div className="user-pill">{getStoredUserName()}</div>
          </div>
        </header>

        <div className="dashboard-content">
          <Outlet />
        </div>
      </section>
    </main>
  );
}

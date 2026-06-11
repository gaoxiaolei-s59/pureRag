import {
  Bot,
  Database,
  Layers3,
  LogOut,
  MessageSquare,
  Moon,
  Sun
} from "lucide-react";
import { NavLink, Navigate, Outlet, useNavigate } from "react-router-dom";
import { useAppTheme } from "../theme";
import { useAuthGuard } from "../../hooks/useAuthGuard";
import { clearAuthStorage, getStoredUserName } from "../../modules/auth/storage";
import { logout } from "../../modules/auth/services/auth";

export function DashboardLayout() {
  const { isAuthenticated } = useAuthGuard();
  const navigate = useNavigate();
  const { theme, isDarkTheme, toggleTheme } = useAppTheme();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  async function handleLogout() {
    try {
      await logout();
    } catch {
      // 后端登出失败时仍清理本地态
    }
    clearAuthStorage();
    navigate("/login", { replace: true });
  }

  const userName = getStoredUserName();
  const userInitial = userName.slice(0, 1).toUpperCase() || "A";

  return (
    <main className="dashboard-shell" data-theme={theme}>
      {/* ===== Sidebar ===== */}
      <aside className="dashboard-sidebar">
        {/* Brand */}
        <div className="brand-block">
          <div className="brand-mark">
            <Bot size={18} />
          </div>
          <div>
            <strong>PureAgent</strong>
            <span>管理后台</span>
          </div>
        </div>

        {/* Navigation */}
        <nav className="dashboard-nav">
          <NavLink to="/knowledge" className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
            <Database size={16} />
            知识库管理
          </NavLink>
          <NavLink to="/intent-tree" className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
            <Layers3 size={16} />
            意图树配置
          </NavLink>
          <NavLink to="/chat" className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
            <MessageSquare size={16} />
            智能问答
          </NavLink>
        </nav>

        {/* Bottom: user + logout */}
        <div className="sidebar-footer">
          <div className="sidebar-user">
            <span className="sidebar-avatar">{userInitial}</span>
            <span className="sidebar-username">{userName || "管理员"}</span>
          </div>
          <button
            type="button"
            className="sidebar-logout"
            title="退出登录"
            onClick={() => void handleLogout()}
          >
            <LogOut size={15} />
          </button>
        </div>
      </aside>

      {/* ===== Main ===== */}
      <section className="dashboard-main">
        <header className="dashboard-topbar">
          <h1 className="dashboard-title">知识库与意图管理</h1>
          <div className="dashboard-topbar-actions">
            <NavLink to="/chat" className="topbar-link-button">
              <MessageSquare size={15} />
              返回聊天
            </NavLink>
            <button
              type="button"
              className="topbar-icon-button"
              aria-label={isDarkTheme ? "切换浅色模式" : "切换深色模式"}
              title={isDarkTheme ? "切换浅色模式" : "切换深色模式"}
              onClick={toggleTheme}
            >
              {isDarkTheme ? <Sun size={15} /> : <Moon size={15} />}
            </button>
            <div className="topbar-user-pill">
              <span className="topbar-avatar">{userInitial}</span>
              {userName}
            </div>
          </div>
        </header>

        <div className="dashboard-content">
          <Outlet />
        </div>
      </section>
    </main>
  );
}

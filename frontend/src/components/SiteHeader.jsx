import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { THEME_COLORS, useAppContext } from "../hooks/useAppContext";
import { apiPost } from "../lib/api";

export default function SiteHeader() {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, theme, setCurrentUser, setTheme } = useAppContext();
  const [accountOpen, setAccountOpen] = useState(false);
  const [themeOpen, setThemeOpen] = useState(false);
  const [logoutPending, setLogoutPending] = useState(false);
  const [logoutError, setLogoutError] = useState("");

  function goHome() {
    setAccountOpen(false);
    setThemeOpen(false);
    navigate("/lobby");
  }

  async function logout() {
    setLogoutPending(true);
    setLogoutError("");
    try {
      await apiPost("/api/auth/logout", {});
      setCurrentUser(null);
      navigate("/login", { replace: true });
    } catch (exception) {
      setLogoutError(exception.message);
      setLogoutPending(false);
    }
  }

  return (
    <header className="lobby-navbar">
      <div className="lobby-navbar-inner">
        <button className="lobby-brand" type="button" onClick={goHome} aria-label="返回首页">
          <span className="lobby-brand-mark">轻</span>
          <span className="lobby-brand-copy">
            <strong>轻量级桌游平台</strong>
            <small>FORTELL BOARDGAME</small>
          </span>
        </button>

        <nav className="lobby-nav" aria-label="主导航">
          <button
            className={`lobby-nav-link${location.pathname === "/lobby" ? " active" : ""}`}
            type="button"
            onClick={goHome}
          >
            首页
          </button>
          <button
            className={`lobby-nav-link lobby-nav-link-with-icon${location.pathname === "/games" ? " active" : ""}`}
            type="button"
            onClick={() => navigate("/games")}
          >
            <span className="lobby-nav-icon" aria-hidden="true">▦</span>
            游戏列表
          </button>
          <button
            className={`lobby-nav-link lobby-nav-link-with-icon${location.pathname === "/rooms" ? " active" : ""}`}
            type="button"
            onClick={() => navigate("/rooms")}
          >
            <span className="lobby-nav-icon" aria-hidden="true">▤</span>
            游戏房间
          </button>
        </nav>

        <div className="lobby-actions">
          <div className="lobby-menu-wrap">
            <button
              className={`lobby-nav-button${location.pathname === "/account" || accountOpen ? " open" : ""}`}
              type="button"
              aria-expanded={accountOpen}
              onClick={() => {
                setAccountOpen((open) => !open);
                setThemeOpen(false);
              }}
            >
              账号
            </button>
            {accountOpen ? (
              <div className="lobby-popover account-popover">
                <span className="popover-label">当前账号</span>
                <strong>{currentUser?.username ?? "未登录"}</strong>
                <div className="account-menu-actions">
                  <button
                    className="account-menu-button"
                    type="button"
                    onClick={() => {
                      setAccountOpen(false);
                      navigate("/account");
                    }}
                  >
                    账号管理
                  </button>
                  <button className="account-menu-button danger" type="button" disabled={logoutPending} onClick={logout}>
                    {logoutPending ? "退出中..." : "退出登录"}
                  </button>
                </div>
                {logoutError ? <span className="account-menu-error">{logoutError}</span> : null}
              </div>
            ) : null}
          </div>

          <div className="lobby-menu-wrap">
            <button
              className={`lobby-nav-button theme-button${themeOpen ? " open" : ""}`}
              type="button"
              aria-expanded={themeOpen}
              onClick={() => {
                setThemeOpen((open) => !open);
              }}
            >
              <span className="theme-dot" style={{ backgroundColor: theme.value }} />
              主题色
            </button>
            {themeOpen ? (
              <div className="lobby-popover theme-popover">
                <div className="popover-heading">
                  <strong>选择主题色</strong>
                  <span>同步应用到 banner 与组件</span>
                </div>
                <div className="theme-options">
                  {THEME_COLORS.map((option) => (
                    <button
                      key={option.value}
                      className={`theme-option${theme.value === option.value ? " selected" : ""}`}
                      type="button"
                      aria-pressed={theme.value === option.value}
                      onClick={() => setTheme(option)}
                    >
                      <span className="theme-swatch" style={{ backgroundColor: option.value }} />
                      <span>{option.name}</span>
                      <small>{option.value}</small>
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </header>
  );
}

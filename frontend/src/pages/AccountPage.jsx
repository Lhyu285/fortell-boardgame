import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ConfirmModal from "../components/ConfirmModal";
import SiteHeader from "../components/SiteHeader";
import { useAppContext } from "../hooks/useAppContext";
import { apiDelete, apiGet, apiPost, apiPut } from "../lib/api";

const PREFERRED_COLORS = ["红", "黄", "蓝", "绿"];

export default function AccountPage() {
  const navigate = useNavigate();
  const { setCurrentUser } = useAppContext();
  const [profile, setProfile] = useState(null);
  const [profileForm, setProfileForm] = useState({ nickname: "", preferredColor: "红" });
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [deletePassword, setDeletePassword] = useState("");
  const [loading, setLoading] = useState(true);
  const [profileSaving, setProfileSaving] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const [profileError, setProfileError] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [deleteError, setDeleteError] = useState("");

  useEffect(() => {
    apiGet("/api/account")
      .then((data) => {
        setProfile(data);
        setProfileForm({ nickname: data.nickname ?? "", preferredColor: data.preferredColor ?? "红" });
      })
      .catch((exception) => setProfileError(exception.message))
      .finally(() => setLoading(false));
  }, []);

  async function saveProfile(event) {
    event.preventDefault();
    setProfileSaving(true);
    setProfileMessage("");
    setProfileError("");
    try {
      const data = await apiPut("/api/account/profile", profileForm);
      setProfile(data);
      setProfileForm({ nickname: data.nickname, preferredColor: data.preferredColor });
      setProfileMessage("设置已保存。");
      setCurrentUser((currentUser) => currentUser ? { ...currentUser, username: data.nickname } : currentUser);
    } catch (exception) {
      setProfileError(exception.message);
    } finally {
      setProfileSaving(false);
    }
  }

  async function changePassword(event) {
    event.preventDefault();
    setPasswordSaving(true);
    setPasswordMessage("");
    setPasswordError("");
    try {
      await apiPost("/api/account/password", passwordForm);
      setCurrentUser(null);
      navigate("/login", { replace: true, state: { passwordChanged: true } });
    } catch (exception) {
      setPasswordError(exception.message);
      setPasswordSaving(false);
    }
  }

  async function deleteAccount() {
    setDeletePending(true);
    setDeleteError("");
    try {
      await apiDelete("/api/account", { password: deletePassword });
      setCurrentUser(null);
      navigate("/login", { replace: true, state: { accountDeleted: true } });
    } catch (exception) {
      setDeleteError(exception.message);
      setDeletePending(false);
    }
  }

  async function logout() {
    try {
      await apiPost("/api/auth/logout", {});
      setCurrentUser(null);
      navigate("/login", { replace: true });
    } catch (exception) {
      setDeleteError(exception.message);
    }
  }

  if (loading) {
    return (
      <div className="account-page">
        <SiteHeader />
        <main className="account-content account-loading">加载中...</main>
      </div>
    );
  }

  return (
    <div className="account-page">
      <SiteHeader />
      <main className="account-content">
        <header className="account-hero account-dashboard-hero">
          <div className="account-hero-copy">
            <span>ACCOUNT CENTER</span>
            <h1>账号中心</h1>
            <p>管理你的公开身份、偏好与账号安全。</p>
          </div>
          {profile ? (
            <div className="account-hero-user">
              <span className="account-hero-avatar">{profile.nickname.slice(0, 1)}</span>
              <div>
                <span>已登录</span>
                <strong>{profile.nickname}</strong>
                <code>{profile.accountId}</code>
              </div>
            </div>
          ) : null}
        </header>

        {profileError && !profile ? <div className="form-error">{profileError}</div> : null}

        {profile ? (
          <>
            <div className="account-overview-grid">
              <section className="panel account-identity-card">
                <div className="account-card-heading">
                  <span>当前账户</span>
                  <span className="account-badge">PUBLIC PROFILE</span>
                </div>
                <div className="account-identity-main">
                  <span className="account-profile-avatar">{profile.nickname.slice(0, 1)}</span>
                  <div>
                    <span className="account-identity-caption">游戏昵称</span>
                    <h2>{profile.nickname}</h2>
                  </div>
                </div>
                <div className="account-meta-list">
                  <div>
                    <span>账户 ID</span>
                    <code>{profile.accountId}</code>
                  </div>
                </div>
                <button className="secondary-button account-logout-button" type="button" onClick={logout}>
                  退出登录
                </button>
              </section>

              <section className="panel account-preferences-card">
                <h2>个人偏好</h2>
                <form className="account-form" onSubmit={saveProfile}>
                  <label>
                    昵称
                    <input
                      value={profileForm.nickname}
                      onChange={(event) => setProfileForm({ ...profileForm, nickname: event.target.value })}
                      maxLength={20}
                      placeholder="输入游戏中展示给其他玩家的昵称"
                    />
                    <small>游戏过程中会向其他玩家展示这个昵称。</small>
                  </label>
                  <label>
                    偏好颜色
                    <select
                      value={profileForm.preferredColor}
                      onChange={(event) => setProfileForm({ ...profileForm, preferredColor: event.target.value })}
                    >
                      {PREFERRED_COLORS.map((color) => <option key={color} value={color}>{color}</option>)}
                    </select>
                    <small>该设置先保存到账号，暂不影响游戏内颜色。</small>
                  </label>
                  {profileError ? <div className="form-error">{profileError}</div> : null}
                  {profileMessage ? <div className="form-success">{profileMessage}</div> : null}
                  <button className="primary-button" type="submit" disabled={profileSaving}>
                    {profileSaving ? "保存中..." : "保存设置"}
                  </button>
                </form>
              </section>
            </div>

            <section className="panel account-security-card">
              <h2>账户安全</h2>
              <div className="account-security-grid">
                <div className="account-security-block">
                  <h3>修改密码</h3>
                  <p>修改后当前登录会立即失效，需要使用新密码重新登录。</p>
                  <form className="account-password-form" onSubmit={changePassword}>
                    <label>
                      当前密码
                      <input
                        type="password"
                        value={passwordForm.currentPassword}
                        onChange={(event) => setPasswordForm({ ...passwordForm, currentPassword: event.target.value })}
                      />
                    </label>
                    <label>
                      新密码
                      <input
                        type="password"
                        value={passwordForm.newPassword}
                        onChange={(event) => setPasswordForm({ ...passwordForm, newPassword: event.target.value })}
                      />
                    </label>
                    <label>
                      确认新密码
                      <input
                        type="password"
                        value={passwordForm.confirmPassword}
                        onChange={(event) => setPasswordForm({ ...passwordForm, confirmPassword: event.target.value })}
                      />
                    </label>
                    {passwordError ? <div className="form-error">{passwordError}</div> : null}
                    {passwordMessage ? <div className="form-success">{passwordMessage}</div> : null}
                    <button className="primary-button" type="submit" disabled={passwordSaving}>
                      {passwordSaving ? "提交中..." : "修改密码并退出登录"}
                    </button>
                  </form>
                </div>

                <div className="account-security-block account-danger-zone">
                  <h3>注销账号</h3>
                  <p>注销后账号将无法再次登录，昵称和账号资料会被移除，已结束对局的必要记录会保留。</p>
                  <label>
                    输入当前密码确认注销
                    <input
                      type="password"
                      value={deletePassword}
                      onChange={(event) => setDeletePassword(event.target.value)}
                    />
                  </label>
                  {deleteError ? <div className="form-error">{deleteError}</div> : null}
                  <button
                    className="danger-outline-button"
                    type="button"
                    disabled={!deletePassword || deletePending}
                    onClick={() => setDeleteConfirmOpen(true)}
                  >
                    我要注销账号…
                  </button>
                </div>
              </div>
            </section>
          </>
        ) : null}
      </main>

      <ConfirmModal
        open={deleteConfirmOpen}
        message="注销账号后将无法恢复，确定继续吗？"
        busy={deletePending}
        error={deleteError}
        onCancel={() => {
          if (!deletePending) {
            setDeleteConfirmOpen(false);
            setDeleteError("");
          }
        }}
        onConfirm={deleteAccount}
      />
    </div>
  );
}

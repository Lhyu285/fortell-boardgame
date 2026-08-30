import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAppContext } from "../hooks/useAppContext";
import { apiGet, apiPost } from "../lib/api";

export default function LoginPage() {
  const navigate = useNavigate();
  const { currentUser, setCurrentUser } = useAppContext();
  const [mode, setMode] = useState("login");
  const [captcha, setCaptcha] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    username: "",
    password: "",
    confirmPassword: "",
    captcha: ""
  });

  useEffect(() => {
    if (currentUser) {
      navigate("/lobby", { replace: true });
    }
  }, [currentUser, navigate]);

  useEffect(() => {
    if (mode === "register") {
      apiGet("/api/auth/captcha")
        .then((data) => setCaptcha(data.captcha))
        .catch(() => setCaptcha("----"));
    }
  }, [mode]);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      const payload = mode === "login"
        ? { username: form.username, password: form.password }
        : form;
      const path = mode === "login" ? "/api/auth/login" : "/api/auth/register";
      const data = await apiPost(path, payload);
      setCurrentUser(data.user);
      navigate("/lobby", { replace: true });
    } catch (exception) {
      setError(exception.message);
      if (mode === "register") {
        apiGet("/api/auth/captcha")
          .then((data) => setCaptcha(data.captcha))
          .catch(() => setCaptcha("----"));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-layout">
      <section className="auth-card">
        <div className="auth-brand">
          <span className="eyebrow">FORTELL BOARDGAME</span>
          <h1>轻量级桌游平台</h1>
          <p>仅供编程技术交流讨论使用，个人开发者能力有限，因此美工部分仅保证能玩</p>
        </div>

        <div className="auth-switch">
          <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} type="button">
            登录
          </button>
          <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")} type="button">
            注册
          </button>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            用户名
            <input
              value={form.username}
              onChange={(event) => setForm({ ...form, username: event.target.value })}
              placeholder="输入用户名"
            />
          </label>
          <label>
            密码
            <input
              type="password"
              value={form.password}
              onChange={(event) => setForm({ ...form, password: event.target.value })}
              placeholder="输入密码"
            />
          </label>

          {mode === "register" ? (
            <>
              <label>
                确认密码
                <input
                  type="password"
                  value={form.confirmPassword}
                  onChange={(event) => setForm({ ...form, confirmPassword: event.target.value })}
                  placeholder="再次输入密码"
                />
              </label>
              <div className="captcha-row">
                <label>
                  验证码
                  <input
                    value={form.captcha}
                    onChange={(event) => setForm({ ...form, captcha: event.target.value })}
                    placeholder="输入验证码"
                  />
                </label>
                <div className="captcha-box">{captcha}</div>
              </div>
            </>
          ) : null}

          {error ? <div className="form-error">{error}</div> : null}

          <button className="primary-button" disabled={submitting} type="submit">
            {submitting ? "提交中..." : mode === "login" ? "登录并进入大厅" : "注册并进入大厅"}
          </button>
        </form>
      </section>
    </div>
  );
}

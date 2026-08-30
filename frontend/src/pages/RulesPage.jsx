import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import PageShell from "../components/PageShell";

export default function RulesPage() {
  const navigate = useNavigate();
  const { gameType } = useParams();
  const [markdown, setMarkdown] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    fetch(`/api/games/${gameType}/rule`, { credentials: "include" })
      .then((response) => {
        if (!response.ok) {
          throw new Error("规则加载失败");
        }
        return response.text();
      })
      .then((text) => {
        if (!active) return;
        setMarkdown(text);
        setError("");
      })
      .catch((exception) => {
        if (!active) return;
        setError(exception.message);
      });
    return () => {
      active = false;
    };
  }, [gameType]);

  return (
    <PageShell
      title={`${labelFor(gameType)} 规则`}
      subtitle="简要规则说明"
      actions={
        <button className="ghost-button" onClick={() => navigate(`/${gameType}`)}>
          返回房间入口
        </button>
      }
    >
      <section className="panel markdown-panel">
        {error ? <div className="form-error">{error}</div> : null}
        <pre>{markdown}</pre>
      </section>
    </PageShell>
  );
}

function labelFor(gameType) {
  if (gameType === "rps") return "猜拳";
  if (gameType === "gobang") return "五子棋";
  if (gameType === "brass") return "伯明翰";
  return gameType;
}

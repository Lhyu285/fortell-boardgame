import { useEffect, useMemo, useState } from "react";

const LABELS = {
  title: "游戏规则",
  loading: "规则加载中...",
  close: "关闭",
  loadFailed: "规则加载失败"
};

export default function RuleModal({ gameType, title, open, onClose }) {
  const [markdown, setMarkdown] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open || !gameType) {
      return undefined;
    }

    let active = true;
    setMarkdown("");
    setError("");

    fetch(`/api/games/${gameType}/rule`, { credentials: "include" })
      .then((response) => {
        if (!response.ok) {
          throw new Error(LABELS.loadFailed);
        }
        return response.text();
      })
      .then((text) => {
        if (!active) return;
        setMarkdown(text);
      })
      .catch((exception) => {
        if (!active) return;
        setError(exception.message);
      });

    return () => {
      active = false;
    };
  }, [gameType, open]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onKeyDown = (event) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  const html = useMemo(() => renderMarkdown(markdown), [markdown]);

  if (!open) {
    return null;
  }

  return (
    <div className="rule-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="rule-modal"
        role="dialog"
        aria-modal="true"
        aria-label={title ?? LABELS.title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="rule-modal-header">
          <h2>{title ?? LABELS.title}</h2>
          <button className="ghost-button small-button" type="button" onClick={onClose}>
            {LABELS.close}
          </button>
        </header>
        <div className="rule-modal-body">
          {error ? <div className="form-error">{error}</div> : null}
          {!error && !markdown ? <p className="muted-note">{LABELS.loading}</p> : null}
          {markdown ? <div className="markdown-content" dangerouslySetInnerHTML={{ __html: html }} /> : null}
        </div>
      </section>
    </div>
  );
}

function renderMarkdown(source) {
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const html = [];
  let listType = "";
  let asideType = "";
  let asideLines = [];

  function closeList() {
    if (listType) {
      html.push(`</${listType}>`);
      listType = "";
    }
  }

  function closeAside() {
    if (asideType) {
      html.push(`<div class="markdown-admonition ${asideType}">${asideLines.join("")}</div>`);
      asideType = "";
      asideLines = [];
    }
  }

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();
    const trimmed = line.trim();

    if (/^:::\s*(note|warning|error)\s*$/i.test(trimmed)) {
      closeList();
      closeAside();
      asideType = trimmed.split(/\s+/)[1].toLowerCase();
      continue;
    }
    if (trimmed === ":::") {
      closeAside();
      continue;
    }

    if (asideType) {
      asideLines.push(trimmed ? `<p>${renderInline(trimmed)}</p>` : "");
      continue;
    }

    if (!trimmed) {
      closeList();
      continue;
    }

    const heading = trimmed.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      closeList();
      const level = heading[1].length;
      html.push(`<h${level}>${renderInline(heading[2])}</h${level}>`);
      continue;
    }

    const unordered = trimmed.match(/^[-*]\s+(.+)$/);
    if (unordered) {
      if (listType !== "ul") {
        closeList();
        listType = "ul";
        html.push("<ul>");
      }
      html.push(`<li>${renderInline(unordered[1])}</li>`);
      continue;
    }

    const ordered = trimmed.match(/^\d+\.\s+(.+)$/);
    if (ordered) {
      if (listType !== "ol") {
        closeList();
        listType = "ol";
        html.push("<ol>");
      }
      html.push(`<li>${renderInline(ordered[1])}</li>`);
      continue;
    }

    closeList();
    html.push(`<p>${renderInline(trimmed)}</p>`);
  }

  closeList();
  closeAside();
  return html.join("");
}

function renderInline(value) {
  return escapeHtml(value)
    .replace(/&lt;(\/?(?:u|mark|span)(?:\s+style=&quot;[^&]*&quot;)?)&gt;/g, "<$1>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>")
    .replace(/==([^=]+)==/g, "<mark>$1</mark>")
    .replace(/__([^_]+)__/g, "<u>$1</u>")
    .replace(/`([^`]+)`/g, "<code>$1</code>");
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

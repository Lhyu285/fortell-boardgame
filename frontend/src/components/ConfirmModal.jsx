import { useEffect } from "react";

export default function ConfirmModal({ open, message, onConfirm, onCancel, busy = false, error = "" }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (event) => {
      if (event.key === "Escape" && !busy) {
        onCancel();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [busy, onCancel, open]);

  if (!open) return null;

  return (
    <div
      className="confirm-modal-backdrop"
      role="presentation"
      onMouseDown={() => {
        if (!busy) onCancel();
      }}
    >
      <section
        className="confirm-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2 id="confirm-modal-title">确认操作</h2>
        <p>{message}</p>
        {error ? <div className="form-error">{error}</div> : null}
        <div className="confirm-modal-actions">
          <button className="ghost-button" type="button" disabled={busy} onClick={onCancel}>
            否
          </button>
          <button className="danger-button" type="button" disabled={busy} onClick={onConfirm}>
            是
          </button>
        </div>
      </section>
    </div>
  );
}

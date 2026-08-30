export default function PageShell({ title, subtitle, actions, children, className = "" }) {
  return (
    <div className={`page-shell ${className}`.trim()}>
      <header className="page-header">
        <div>
          <h1>{title}</h1>
          {subtitle ? <p>{subtitle}</p> : null}
        </div>
        {actions ? <div className="page-actions">{actions}</div> : null}
      </header>
      {children}
    </div>
  );
}

import React from "react";

class GameErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error) {
    console.error("Game render failed", this.props.gameType, error);
  }

  render() {
    if (this.state.error) {
      return (
        <section className="panel form-error">
          游戏界面渲染失败：{this.state.error.message || "未知错误"}。请刷新页面；如果仍然出现，请保留当前房间状态用于排查。
        </section>
      );
    }
    return this.props.children;
  }
}

export { GameErrorBoundary };
export default GameErrorBoundary;

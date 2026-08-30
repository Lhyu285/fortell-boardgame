import { Navigate, useLocation } from "react-router-dom";
import { useAppContext } from "../hooks/useAppContext";

export default function ProtectedRoute({ children }) {
  const { currentUser, loadingUser } = useAppContext();
  const location = useLocation();

  if (loadingUser) {
    return <div className="center-page">加载中...</div>;
  }

  if (!currentUser) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

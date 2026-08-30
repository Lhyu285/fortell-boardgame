import { Navigate, Route, Routes } from "react-router-dom";
import { AppProvider } from "./hooks/useAppContext";
import LoginPage from "./pages/LoginPage";
import LobbyPage from "./pages/LobbyPage";
import GameLandingPage from "./pages/GameLandingPage";
import RoomPage from "./pages/RoomPage";
import RulesPage from "./pages/RulesPage";
import ProtectedRoute from "./components/ProtectedRoute";

export default function App() {
  return (
    <AppProvider>
      <Routes>
        <Route path="/" element={<Navigate to="/lobby" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/lobby"
          element={
            <ProtectedRoute>
              <LobbyPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/:gameType"
          element={
            <ProtectedRoute>
              <GameLandingPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/:gameType/rule"
          element={
            <ProtectedRoute>
              <RulesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/:gameType/:roomId"
          element={
            <ProtectedRoute>
              <RoomPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/rules/:gameType"
          element={<Navigate to="/lobby" replace />}
        />
      </Routes>
    </AppProvider>
  );
}

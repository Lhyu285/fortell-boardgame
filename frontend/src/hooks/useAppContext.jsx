import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { apiGet } from "../lib/api";

const AppContext = createContext(null);

export function AppProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [loadingUser, setLoadingUser] = useState(true);

  useEffect(() => {
    let active = true;
    apiGet("/api/auth/me")
      .then((data) => {
        if (!active) return;
        setCurrentUser(data.authenticated ? data.user : null);
      })
      .catch(() => {
        if (!active) return;
        setCurrentUser(null);
      })
      .finally(() => {
        if (!active) return;
        setLoadingUser(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const value = useMemo(
    () => ({
      currentUser,
      loadingUser,
      setCurrentUser
    }),
    [currentUser, loadingUser]
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useAppContext() {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error("useAppContext must be used inside AppProvider");
  }
  return context;
}

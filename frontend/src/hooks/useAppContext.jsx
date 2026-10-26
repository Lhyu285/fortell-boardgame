import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { apiGet } from "../lib/api";

const AppContext = createContext(null);

export const THEME_COLORS = [
  { name: "海蓝", value: "#3B82F6", dark: "#2563EB" },
  { name: "翡翠", value: "#10B981", dark: "#059669" },
  { name: "紫罗兰", value: "#8B5CF6", dark: "#7C3AED" },
  { name: "琥珀", value: "#F59E0B", dark: "#D97706" },
  { name: "珊瑚", value: "#F97316", dark: "#EA580C" },
  { name: "玫瑰", value: "#EC4899", dark: "#DB2777" },
  { name: "石墨", value: "#6B7280", dark: "#4B5563" },
  { name: "靛蓝", value: "#6366F1", dark: "#4F46E5" }
];

const DEFAULT_THEME = THEME_COLORS[0];

function getStoredTheme() {
  try {
    const storedValue = window.localStorage.getItem("fortell-theme-color");
    return THEME_COLORS.find((theme) => theme.value === storedValue) ?? DEFAULT_THEME;
  } catch {
    return DEFAULT_THEME;
  }
}

export function AppProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [loadingUser, setLoadingUser] = useState(true);
  const [theme, setTheme] = useState(getStoredTheme);

  useEffect(() => {
    document.documentElement.style.setProperty("--accent", theme.value);
    document.documentElement.style.setProperty("--accent-dark", theme.dark);
    document.documentElement.style.setProperty("--accent-soft", `${theme.value}18`);
    document.documentElement.style.setProperty("--accent-wash", `${theme.value}0d`);
    try {
      window.localStorage.setItem("fortell-theme-color", theme.value);
    } catch {
      // Local storage may be unavailable in private or embedded browser contexts.
    }
  }, [theme]);

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
      setCurrentUser,
      theme,
      setTheme
    }),
    [currentUser, loadingUser, theme]
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

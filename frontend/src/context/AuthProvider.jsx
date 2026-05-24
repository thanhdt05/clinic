import { useEffect, useState } from "react";
import { getCurrentUser, logoutApi } from "../api/authApi";
import { SESSION_EXPIRED } from "../api/http";
import AuthContext from "./AuthContext";

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    const clearUser = () => setUser(null);
    window.addEventListener(SESSION_EXPIRED, clearUser);
    getCurrentUser(controller.signal)
      .then(setUser)
      .catch(() => {
        if (!controller.signal.aborted) clearUser();
      })
      .finally(() => {
        if (!controller.signal.aborted) setCheckingSession(false);
      });

    return () => {
      controller.abort();
      window.removeEventListener(SESSION_EXPIRED, clearUser);
    };
  }, []);

  function updateUser(patch) {
    setUser((current) => (current ? { ...current, ...patch } : current));
  }

  async function logout() {
    await logoutApi();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, checkingSession, login: setUser, updateUser, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

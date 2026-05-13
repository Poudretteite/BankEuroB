import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/useAuthStore';
import { Layout } from './components/layout/Layout';

// 🔥 Lazy-loaded pages — code splitting: each page is a separate chunk
const LoginPage = lazy(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })));
const RegisterPage = lazy(() => import('./pages/RegisterPage').then(m => ({ default: m.RegisterPage })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const TransferPage = lazy(() => import('./pages/TransferPage').then(m => ({ default: m.TransferPage })));
const BlikPage = lazy(() => import('./pages/BlikPage').then(m => ({ default: m.BlikPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })));
const HistoryPage = lazy(() => import('./pages/HistoryPage').then(m => ({ default: m.HistoryPage })));

// Minimalny loader — pokazywany podczas ładowania kodu strony
const PageLoader = () => (
  <div style={{
    display: 'flex', justifyContent: 'center', alignItems: 'center',
    height: '100vh', color: 'var(--text-secondary)', fontSize: '1.1rem'
  }}>
    <div style={{ textAlign: 'center' }}>
      <div style={{
        width: 32, height: 32, border: '3px solid var(--glass-border)',
        borderTopColor: 'var(--accent-gold)', borderRadius: '50%',
        animation: 'spin 0.8s linear infinite', margin: '0 auto 12px'
      }} />
      Ładowanie...
    </div>
  </div>
);

const PrivateRoute = ({ children }: { children: React.ReactNode }) => {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
};

const PublicRoute = ({ children }: { children: React.ReactNode }) => {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated());
  return isAuthenticated ? <Navigate to="/dashboard" /> : <>{children}</>;
};

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route
            path="/login"
            element={
              <PublicRoute>
                <LoginPage />
              </PublicRoute>
            }
          />
          <Route
            path="/register"
            element={
              <PublicRoute>
                <RegisterPage />
              </PublicRoute>
            }
          />
          <Route
            path="/"
            element={<Navigate to="/dashboard" />}
          />
          <Route
            path="/dashboard"
            element={
              <PrivateRoute>
                <Layout>
                  <DashboardPage />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/transfer"
            element={
              <PrivateRoute>
                <Layout>
                  <TransferPage />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/blik"
            element={
              <PrivateRoute>
                <Layout>
                  <BlikPage />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/history"
            element={
              <PrivateRoute>
                <Layout>
                  <HistoryPage />
                </Layout>
              </PrivateRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <PrivateRoute>
                <Layout>
                  <SettingsPage />
                </Layout>
              </PrivateRoute>
            }
          />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;

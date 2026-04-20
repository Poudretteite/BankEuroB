import React from 'react';
import styles from './Navbar.module.css';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/useAuthStore';
import { useThemeStore } from '../../store/useThemeStore';
import { LogOut, Home, Send, Wallet, Sun, Moon } from 'lucide-react';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuthStore();
  const { theme, toggleTheme } = useThemeStore();
  const navigate = useNavigate();
  const location = useLocation();

  if (!user) return null;

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const isActive = (path: string) => location.pathname === path;

  return (
    <nav className={styles.navbar}>
      <div className={styles.navContainer}>
        <div className={styles.logo}>
          <Wallet className={styles.logoIcon} size={28} />
          <span>BankEuroB</span>
        </div>
        
        <div className={styles.navLinks}>
          <Link 
            to="/dashboard" 
            className={`${styles.navItem} ${isActive('/dashboard') ? styles.active : ''}`}
          >
            <Home size={18} />
            <span>Pulpit</span>
          </Link>
          <Link 
            to="/transfer" 
            className={`${styles.navItem} ${isActive('/transfer') ? styles.active : ''}`}
          >
            <Send size={18} />
            <span>Przelew</span>
          </Link>
        </div>
        
        <div className={styles.userSection}>
          <button className={styles.themeToggleBtn} onClick={toggleTheme}>
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </button>
          
          <div className={styles.userInfo}>
            <span className={styles.userName}>{user.firstName} {user.lastName}</span>
            <span className={styles.userRole}>{user.role}</span>
          </div>
          <button className={styles.logoutBtn} onClick={handleLogout}>
            <LogOut size={18} />
            <span>Wyloguj</span>
          </button>
        </div>
      </div>
    </nav>
  );
};

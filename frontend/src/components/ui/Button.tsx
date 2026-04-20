import React from 'react';
import styles from './Button.module.css';
import { Loader2 } from 'lucide-react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  className = '',
  disabled,
  ...props
}) => {
  const baseClass = `${styles.btn} ${styles[variant]} ${styles[size]} ${className}`;
  
  return (
    <button className={baseClass} disabled={isLoading || disabled} {...props}>
      {isLoading && <Loader2 className={styles.spinner} size={18} />}
      {!isLoading && children}
    </button>
  );
};

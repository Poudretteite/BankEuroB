import React, { forwardRef } from 'react';
import styles from './Input.module.css';
import { AlertCircle } from 'lucide-react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  icon?: React.ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, icon, className = '', ...props }, ref) => {
    return (
      <div className={`${styles.inputContainer} ${className}`}>
        {label && <label className={styles.label}>{label}</label>}
        
        <div className={styles.inputWrapper}>
          {icon && <span className={styles.icon}>{icon}</span>}
          <input
            ref={ref}
            className={`${styles.input} ${icon ? styles.withIcon : ''} ${error ? styles.errorInput : ''}`}
            {...props}
          />
        </div>
        
        {error && (
          <div className={styles.errorMessage}>
            <AlertCircle size={14} />
            <span>{error}</span>
          </div>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';

import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Mail, Lock, Wallet } from 'lucide-react';
import styles from './Auth.module.css';
import { Link, useNavigate } from 'react-router-dom';
import axiosClient from '../api/axiosClient';
import { useAuthStore } from '../store/useAuthStore';
import { useState, useEffect } from 'react';

const loginSchema = z.object({
  email: z.string().email('Niepoprawny format email'),
  password: z.string().min(1, 'Hasło jest wymagane'),
});

type LoginForm = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [pendingAttemptId, setPendingAttemptId] = useState<string | null>(null);
  
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginForm) => {
    try {
      const response = await axiosClient.post('/auth/login', data);
      
      if (response.data.requiresParentApproval) {
        setPendingAttemptId(response.data.loginAttemptId);
        return;
      }

      const { token, ...user } = response.data;
      setAuth(user, token);
      navigate('/dashboard');
    } catch (err: any) {
      setError('root', {
        type: 'manual',
        message: 'Nieprawidłowy email lub hasło. Spróbuj ponownie.',
      });
    }
  };

  useEffect(() => {
    if (!pendingAttemptId) return;

    const intervalId = setInterval(async () => {
      try {
        const response = await axiosClient.get(`/auth/login-status/${pendingAttemptId}`);
        if (!response.data.requiresParentApproval && response.data.token) {
          clearInterval(intervalId);
          const { token, ...user } = response.data;
          setAuth(user, token);
          navigate('/dashboard');
        }
      } catch (err: any) {
        if (err.response?.data?.message === 'Logowanie odrzucone przez rodzica' || err.response?.status === 400 || err.response?.status === 500) {
          clearInterval(intervalId);
          setPendingAttemptId(null);
          setError('root', {
            type: 'manual',
            message: 'Rodzic odrzucił logowanie.',
          });
        }
      }
    }, 2000);

    return () => clearInterval(intervalId);
  }, [pendingAttemptId, navigate, setAuth, setError]);

  if (pendingAttemptId) {
    return (
      <div className={styles.authContainer}>
        <div className={`glass-panel ${styles.authCard}`} style={{ textAlign: 'center' }}>
          <h2>Oczekiwanie na rodzica...</h2>
          <p>Twoje logowanie musi zostać zatwierdzone przez rodzica na jego urządzeniu.</p>
          <div style={{ marginTop: '20px' }}>
            <span className={styles.loader}></span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.authContainer}>
      <div className={`glass-panel ${styles.authCard}`}>
        <div className={styles.authHeader}>
          <Wallet className={styles.logoIcon} size={42} />
          <h1>Witaj w BankEuroB</h1>
          <p>Zaloguj się do swojej bankowości premium</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className={styles.authForm}>
          <Input
            label="Adres email"
            placeholder="jan.kowalski@example.com"
            icon={<Mail size={18} />}
            error={errors.email?.message}
            {...register('email')}
          />
          
          <Input
            label="Hasło"
            type="password"
            placeholder="••••••••"
            icon={<Lock size={18} />}
            error={errors.password?.message}
            {...register('password')}
          />
          
          {errors.root && (
            <div className={styles.globalError}>{errors.root.message}</div>
          )}

          <Button 
            type="submit" 
            size="lg" 
            isLoading={isSubmitting}
            className={styles.submitBtn}
          >
            Zaloguj się
          </Button>

          <p className={styles.authFooter}>
            Nie masz jeszcze konta? <Link to="/register">Zarejestruj się</Link>
          </p>
        </form>
      </div>
    </div>
  );
};

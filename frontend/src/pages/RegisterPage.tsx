import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Mail, Lock, User, Wallet } from 'lucide-react';
import styles from './Auth.module.css';
import { Link, useNavigate } from 'react-router-dom';
import axiosClient from '../api/axiosClient';

const registerSchema = z.object({
  email: z.string().email('Niepoprawny format email'),
  password: z.string().min(8, 'Hasło musi mieć minimum 8 znaków'),
  firstName: z.string().min(2, 'Imię jest wymagane'),
  lastName: z.string().min(2, 'Nazwisko jest wyamgane'),
  dateOfBirth: z.string().min(10, 'Wprowadź datę w formacie RRRR-MM-DD'),
  addressCountry: z.string().default('PL')
});

type RegisterForm = z.infer<typeof registerSchema>;

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema) as any,
  });

  const onSubmit = async (data: RegisterForm) => {
    try {
      await axiosClient.post('/auth/register', data);
      navigate('/login');
    } catch (err: any) {
      setError('root', {
        type: 'manual',
        message: err.response?.data?.message || 'Błąd rejestracji. Użytkownik o takim emailu może już istnieć.',
      });
    }
  };

  return (
    <div className={styles.authContainer}>
      <div className={`glass-panel ${styles.authCard}`}>
        <div className={styles.authHeader}>
          <Wallet className={styles.logoIcon} size={42} />
          <h1>Dołącz do BankEuroB</h1>
          <p>Utwórz konto, by otrzymać rachunek premium</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className={styles.authForm}>
          <Input
            label="Imię"
            placeholder="Jan"
            icon={<User size={18} />}
            error={errors.firstName?.message}
            {...register('firstName')}
          />
          <Input
            label="Nazwisko"
            placeholder="Kowalski"
            icon={<User size={18} />}
            error={errors.lastName?.message}
            {...register('lastName')}
          />
          <Input
            label="Data urodzenia (RRRR-MM-DD)"
            placeholder="1990-01-01"
            error={errors.dateOfBirth?.message}
            {...register('dateOfBirth')}
          />
          <Input
            label="Adres email"
            type="email"
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
            Zarejestruj się
          </Button>

          <p className={styles.authFooter}>
            Masz już konto? <Link to="/login">Zaloguj się</Link>
          </p>
        </form>
      </div>
    </div>
  );
};

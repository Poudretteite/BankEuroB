import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Send, FileText, Hash, DollarSign } from 'lucide-react';
import styles from './Transfer.module.css';
import axiosClient from '../api/axiosClient';
import { useAuthStore } from '../store/useAuthStore';

const transferSchema = z.object({
  receiverIban: z.string().min(15, 'Numer IBAN jest za krótki'),
  receiverName: z.string().min(2, 'Podaj nazwę odbiorcy'),
  title: z.string().min(1, 'Tytuł przelewu jest wymagany'),
  amount: z.coerce.number().positive('Kwota musi być większa od zera'),
});

type TransferForm = z.infer<typeof transferSchema>;

export const TransferPage: React.FC = () => {
  const { user } = useAuthStore();
  const [isSuccess, setIsSuccess] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TransferForm>({
    resolver: zodResolver(transferSchema) as any,
  });

  const onSubmit = async (data: TransferForm) => {
    try {
      await axiosClient.post('/transfers', {
        ...data,
        currency: 'EUR' // Główna waluta aplikacji
      });
      setIsSuccess(true);
      reset();
      setTimeout(() => setIsSuccess(false), 5000);
    } catch (err: any) {
      setError('root', {
        type: 'manual',
        message: err.response?.data?.message || 'Nie udało się zinicjować przelewu. Sprawdź saldo lub dane odbiorcy.',
      });
    }
  };

  return (
    <div className={styles.transferPage}>
      <h1 className={styles.pageTitle}>Zleć przelew</h1>
      <p className={styles.pageSubtitle}>Przelej środki bezpiecznie i natychmiastowo</p>

      {isSuccess && (
        <div className={styles.successToast}>
          Przelew został zrealizowany pomyślnie{user?.role === 'JUNIOR' ? ' i oczekuje na zatwierdzenie przez rodzica' : ''}!
        </div>
      )}

      {user?.role === 'JUNIOR' && (
        <div style={{ background: 'rgba(255,165,0,0.1)', color: 'orange', padding: '15px', borderRadius: '8px', marginBottom: '20px', border: '1px solid orange' }}>
          Jesteś zalogowany jako Junior. Wszystkie wykonane przez ciebie przelewy będą musiały zostać zaakceptowane przez twojego rodzica, zanim środki zostaną pobrane z konta.
        </div>
      )}

      <div className={`glass-panel ${styles.transferCard}`}>
        <form onSubmit={handleSubmit(onSubmit)} className={styles.transferForm}>
          
          <div className={styles.inputGroup}>
            <Input
              label="Odbiorca"
              placeholder="Jan Kowalski"
              icon={<Send size={18} />}
              error={errors.receiverName?.message}
              {...register('receiverName')}
            />
          </div>

          <div className={styles.inputGroup}>
            <Input
              label="Konto odbiorcy (IBAN)"
              placeholder="DE89370400440532013000"
              icon={<Hash size={18} />}
              error={errors.receiverIban?.message}
              {...register('receiverIban')}
            />
          </div>

          <div className={styles.row}>
            <div className={styles.flex2}>
              <Input
                label="Tytułem"
                placeholder="Spłata długu"
                icon={<FileText size={18} />}
                error={errors.title?.message}
                {...register('title')}
              />
            </div>
            <div className={styles.flex1}>
              <Input
                label="Kwota (EUR)"
                type="number"
                step="0.01"
                placeholder="0.00"
                icon={<DollarSign size={18} />}
                error={errors.amount?.message}
                {...register('amount')}
              />
            </div>
          </div>

          {errors.root && (
            <div className={styles.errorBox}>{errors.root.message}</div>
          )}

          <div className={styles.formActions}>
            <Button 
              type="submit" 
              size="lg" 
              isLoading={isSubmitting}
              className={styles.submitBtn}
            >
              Wykonaj Przelew
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};

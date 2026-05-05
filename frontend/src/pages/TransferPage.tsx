import React, { useState, useEffect } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Send, FileText, Hash, DollarSign, Zap, Clock, Building2, ShieldCheck } from 'lucide-react';
import styles from './Transfer.module.css';
import axiosClient from '../api/axiosClient';
import { useAuthStore } from '../store/useAuthStore';

const transferSchema = z.object({
  transferType: z.enum(['INTERNAL', 'SEPA_SCT', 'SEPA_INSTANT', 'RTGS_TARGET2']),
  receiverIban: z.string().min(15, 'Numer IBAN jest za krótki'),
  receiverName: z.string().min(2, 'Podaj nazwę odbiorcy'),
  title: z.string().min(1, 'Tytuł przelewu jest wymagany'),
  amount: z.coerce.number().positive('Kwota musi być większa od zera'),
});

type TransferForm = z.infer<typeof transferSchema>;

export const TransferPage: React.FC = () => {
  const { user } = useAuthStore();
  const [isSuccess, setIsSuccess] = useState(false);
  const [accounts, setAccounts] = useState<any[]>([]);

  const fetchAccounts = async () => {
    try {
      const res = await axiosClient.get('/accounts');
      setAccounts(res.data);
    } catch (err) {
      console.error('Błąd pobierania kont', err);
    }
  };

  useEffect(() => {
    fetchAccounts();
  }, []);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<TransferForm>({
    resolver: zodResolver(transferSchema) as any,
    defaultValues: {
      transferType: 'INTERNAL'
    }
  });

  const selectedType = watch('transferType');
  const watchedAmount = watch('amount') || 0;

  const getFeeValue = () => {
    switch(selectedType) {
      case 'SEPA_INSTANT': return 0.50;
      case 'RTGS_TARGET2': return 5.00;
      default: return 0.00;
    }
  };

  const mainAccount = accounts.length > 0 ? accounts[0] : null;
  const currentBalance = mainAccount ? mainAccount.availableBalance : 0;
  
  let projectedBalance = currentBalance - watchedAmount - getFeeValue();
  let willTriggerOverdraftFee = false;
  if (currentBalance >= 0 && projectedBalance < 0) {
    willTriggerOverdraftFee = true;
    projectedBalance -= 2.00; // Prowizja za wejście w debet
  }
  const isNegative = projectedBalance < 0;

  const onSubmit = async (data: TransferForm) => {
    if (accounts.length === 0) {
      setError('root', { type: 'manual', message: 'Nie znaleziono konta nadawcy.' });
      return;
    }

    try {
      await axiosClient.post('/transfers', {
        ...data,
        senderIban: accounts[0].iban, // Automatyczne przypisanie konta nadawcy
        receiverIban: data.receiverIban.replace(/\s+/g, ''), // Usunięcie spacji z IBAN
        currency: 'EUR' // Główna waluta aplikacji
      });
      setIsSuccess(true);
      reset({ transferType: 'INTERNAL', receiverIban: '', receiverName: '', title: '', amount: 0 });
      fetchAccounts(); // Aktualizacja dostępnych środków po przelewie
      setTimeout(() => setIsSuccess(false), 5000);
    } catch (err: any) {
      setError('root', {
        type: 'manual',
        message: err.response?.data?.message || 'Nie udało się zinicjować przelewu. Sprawdź saldo lub dane odbiorcy.',
      });
    }
  };

  const getFeeText = () => {
    switch(selectedType) {
      case 'SEPA_INSTANT': return 'Opłata: 0.50 EUR';
      case 'RTGS_TARGET2': return 'Opłata: 5.00 EUR';
      default: return 'Brak opłat (0.00 EUR)';
    }
  };

  const getDeliveryText = () => {
    switch(selectedType) {
      case 'INTERNAL': return 'Natychmiast';
      case 'SEPA_INSTANT': return 'W kilka sekund (24/7)';
      case 'RTGS_TARGET2': return 'Dziś (pilne)';
      case 'SEPA_SCT': return 'Kolejny dzień roboczy';
      default: return '';
    }
  };

  return (
    <div className={styles.transferPage}>
      <h1 className={styles.pageTitle}>Zleć przelew</h1>
      <p className={styles.pageSubtitle}>Wybierz typ przelewu i wyślij środki bezpiecznie</p>

      {isSuccess && (
        <div className={styles.successToast}>
          Przelew został zrealizowany pomyślnie{user?.role === 'JUNIOR' ? ' i oczekuje na zatwierdzenie przez rodzica' : ''}!
        </div>
      )}

      {user?.role === 'JUNIOR' && (
        <div style={{ background: 'rgba(255,165,0,0.1)', color: 'orange', padding: '15px', borderRadius: '8px', marginBottom: '20px', border: '1px solid orange' }}>
          Jesteś zalogowany jako Junior. Wszystkie wykonane przez ciebie przelewy będą musiały zostać zaakceptowane przez twojego rodzica.
        </div>
      )}

      <div className={`glass-panel ${styles.transferCard}`}>
        <form onSubmit={handleSubmit(onSubmit)} className={styles.transferForm}>
          
          <div style={{ marginBottom: '24px' }}>
            <label style={{ display: 'block', marginBottom: '12px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
              Wybierz typ przelewu
            </label>
            <Controller
              name="transferType"
              control={control}
              render={({ field }) => (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px' }}>
                  
                  {/* Wewnętrzny */}
                  <div 
                    onClick={() => field.onChange('INTERNAL')}
                    style={{ 
                      padding: '16px', 
                      borderRadius: '12px', 
                      border: field.value === 'INTERNAL' ? '2px solid var(--accent-gold)' : '1px solid var(--glass-border)',
                      background: field.value === 'INTERNAL' ? 'rgba(212, 175, 55, 0.1)' : 'rgba(255,255,255,0.02)',
                      cursor: 'pointer',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                      <Building2 size={18} color={field.value === 'INTERNAL' ? 'var(--accent-gold)' : 'var(--text-secondary)'} />
                      <strong style={{ fontSize: '0.95rem' }}>Wewnętrzny</strong>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Tylko w BankEuroB</div>
                  </div>

                  {/* SEPA */}
                  <div 
                    onClick={() => field.onChange('SEPA_SCT')}
                    style={{ 
                      padding: '16px', 
                      borderRadius: '12px', 
                      border: field.value === 'SEPA_SCT' ? '2px solid var(--accent-blue)' : '1px solid var(--glass-border)',
                      background: field.value === 'SEPA_SCT' ? 'rgba(0, 168, 255, 0.1)' : 'rgba(255,255,255,0.02)',
                      cursor: 'pointer',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                      <Clock size={18} color={field.value === 'SEPA_SCT' ? 'var(--accent-blue)' : 'var(--text-secondary)'} />
                      <strong style={{ fontSize: '0.95rem' }}>SEPA Standard</strong>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Inne banki w EUR</div>
                  </div>

                  {/* SEPA Instant */}
                  <div 
                    onClick={() => field.onChange('SEPA_INSTANT')}
                    style={{ 
                      padding: '16px', 
                      borderRadius: '12px', 
                      border: field.value === 'SEPA_INSTANT' ? '2px solid var(--success-color)' : '1px solid var(--glass-border)',
                      background: field.value === 'SEPA_INSTANT' ? 'rgba(46, 204, 113, 0.1)' : 'rgba(255,255,255,0.02)',
                      cursor: 'pointer',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                      <Zap size={18} color={field.value === 'SEPA_INSTANT' ? 'var(--success-color)' : 'var(--text-secondary)'} />
                      <strong style={{ fontSize: '0.95rem' }}>SEPA Instant</strong>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Natychmiast (0.50 EUR)</div>
                  </div>

                  {/* TARGET2 */}
                  <div 
                    onClick={() => field.onChange('RTGS_TARGET2')}
                    style={{ 
                      padding: '16px', 
                      borderRadius: '12px', 
                      border: field.value === 'RTGS_TARGET2' ? '2px solid #e74c3c' : '1px solid var(--glass-border)',
                      background: field.value === 'RTGS_TARGET2' ? 'rgba(231, 76, 60, 0.1)' : 'rgba(255,255,255,0.02)',
                      cursor: 'pointer',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                      <ShieldCheck size={18} color={field.value === 'RTGS_TARGET2' ? '#e74c3c' : 'var(--text-secondary)'} />
                      <strong style={{ fontSize: '0.95rem' }}>Pilny (TARGET)</strong>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Wysokie kwoty (5.00 EUR)</div>
                  </div>

                </div>
              )}
            />
          </div>
          
          <div style={{ padding: '12px 16px', background: 'rgba(0,0,0,0.2)', borderRadius: '8px', marginBottom: '24px', display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Czas realizacji: <strong style={{ color: 'var(--text-primary)' }}>{getDeliveryText()}</strong></span>
            <span style={{ color: 'var(--text-secondary)' }}>{getFeeText()}</span>
          </div>

          <div className={styles.inputGroup}>
            <Input
              label="Odbiorca"
              placeholder="Jan Kowalski lub Nazwa Firmy"
              icon={<Send size={18} />}
              error={errors.receiverName?.message}
              {...register('receiverName')}
            />
          </div>

          <div className={styles.inputGroup}>
            <Input
              label="Konto odbiorcy (IBAN)"
              placeholder={selectedType === 'INTERNAL' ? "Tylko z puli DE89..." : "IBAN odbiorcy np. DE89..."}
              icon={<Hash size={18} />}
              error={errors.receiverIban?.message}
              {...register('receiverIban')}
            />
          </div>

          <div className={styles.row}>
            <div className={styles.flex2}>
              <Input
                label="Tytułem"
                placeholder="Faktura 123/2026"
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

          {mainAccount && (
            <div style={{ marginTop: '12px', marginBottom: '24px', padding: '16px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--glass-border)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>Dostępne środki:</span>
                <strong style={{ color: 'var(--text-primary)' }}>{currentBalance.toFixed(2)} EUR</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>Saldo po przelewie:</span>
                <strong style={{ color: isNegative ? '#e74c3c' : 'var(--success-color)' }}>
                  {projectedBalance.toFixed(2)} EUR
                </strong>
              </div>
              {willTriggerOverdraftFee && (
                <div style={{ marginTop: '8px', fontSize: '0.8rem', color: '#e74c3c', textAlign: 'right' }}>
                  W tym jednorazowa prowizja za debet (2.00 EUR)
                </div>
              )}
            </div>
          )}

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

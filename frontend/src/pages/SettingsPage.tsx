import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Settings, User, Phone, MapPin, Save, KeyRound, ShieldCheck, CheckCircle2, AlertTriangle } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import { useAuthStore } from '../store/useAuthStore';


const settingsSchema = z.object({
  phone: z.string().min(5, 'Numer telefonu jest wymagany'),
  addressStreet: z.string().min(2, 'Podaj ulicę i numer domu'),
  addressCity: z.string().min(2, 'Podaj miasto'),
  addressCountry: z.string().min(2, 'Wybierz kraj'),
});

type SettingsForm = z.infer<typeof settingsSchema>;

const blikPinSchema = z.object({
  currentPin: z.string().length(4, 'PIN musi mieć 4 cyfry').optional(),
  newPin: z.string().length(4, 'PIN musi mieć 4 cyfry'),
  confirmPin: z.string().length(4, 'PIN musi mieć 4 cyfry'),
}).refine((data) => data.newPin === data.confirmPin, {
  message: 'Nowy PIN i potwierdzenie muszą być takie same',
  path: ['confirmPin'],
});

type BlikPinForm = z.infer<typeof blikPinSchema>;

export const SettingsPage: React.FC = () => {
  const [profileData, setProfileData] = useState<any>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  // BLIK PIN state
  const { setHasBlikPin } = useAuthStore();
  const [blikPinExists, setBlikPinExists] = useState(false);
  const [blikPinSuccess, setBlikPinSuccess] = useState(false);
  const [blikPinError, setBlikPinError] = useState('');
  const [blikSubmitting, setBlikSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<SettingsForm>({
    resolver: zodResolver(settingsSchema) as any,
  });

  const {
    register: registerBlik,
    handleSubmit: handleSubmitBlik,
    reset: resetBlik,
    formState: { errors: blikErrors },
  } = useForm<BlikPinForm>({
    resolver: zodResolver(blikPinSchema) as any,
    defaultValues: {
      currentPin: '',
      newPin: '',
      confirmPin: '',
    },
  });

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const response = await axiosClient.get('/customers/me');
        const data = response.data;
        setProfileData(data);
        setValue('phone', data.phone || '');
        setValue('addressStreet', data.addressStreet || '');
        setValue('addressCity', data.addressCity || '');
        setValue('addressCountry', data.addressCountry || 'PL');

        // Sprawdź czy PIN istnieje w bazie
        const hasPin = !!data.blikPin;
        setBlikPinExists(hasPin);
        setHasBlikPin(hasPin);

        setIsLoading(false);
      } catch (err) {
        console.error('Błąd pobierania profilu', err);
        setIsLoading(false);
      }
    };
    fetchProfile();
  }, [setValue, setHasBlikPin]);

  const onSubmit = async (data: SettingsForm) => {
    try {
      await axiosClient.put('/customers/me', data);
      setIsSuccess(true);
      setTimeout(() => setIsSuccess(false), 5000);
    } catch (err) {
      console.error('Błąd aktualizacji', err);
    }
  };

  // ── BLIK PIN: zapisz / zmień przez API ─────────────────────────────────
  const onBlikPinSubmit = async (data: BlikPinForm) => {
    setBlikPinError('');
    setBlikSubmitting(true);

    try {
      const payload: { currentPin?: string; newPin: string } = {
        newPin: data.newPin,
      };

      // Jeśli PIN już istnieje, wyślij też obecny PIN do weryfikacji
      if (blikPinExists && data.currentPin) {
        payload.currentPin = data.currentPin;
      }

      await axiosClient.put('/customers/me/blik-pin', payload);

      // Sukces – zaktualizuj stan lokalny
      setBlikPinExists(true);
      setHasBlikPin(true);
      setBlikPinSuccess(true);
      setTimeout(() => setBlikPinSuccess(false), 5000);
      resetBlik();
    } catch (err: any) {
      const message = err.response?.data?.message
        || err.response?.data?.error
        || 'Nie udało się zapisać PIN-u. Spróbuj ponownie.';
      setBlikPinError(message);
    } finally {
      setBlikSubmitting(false);
    }
  };

  if (isLoading) {
    return <div style={{ color: 'white', padding: '2rem', textAlign: 'center' }}>Ładowanie...</div>;
  }

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', padding: '2rem 1rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
        <Settings size={32} color="var(--accent-blue)" />
        <div>
          <h1 style={{ fontSize: '2rem', color: 'var(--text-primary)', margin: 0 }}>Ustawienia Konta</h1>
          <p style={{ color: 'var(--text-secondary)', margin: 0 }}>Zarządzaj swoimi danymi kontaktowymi</p>
        </div>
      </div>

      {isSuccess && (
        <div style={{ background: 'rgba(46, 204, 113, 0.2)', border: '1px solid var(--success-color)', color: 'white', padding: '16px', borderRadius: '8px', marginBottom: '24px' }}>
          Dane zostały pomyślnie zaktualizowane.
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '24px' }}>
        
        {/* Sekcja danych zablokowanych */}
        <div className="glass-panel" style={{ padding: '24px' }}>
          <h3 style={{ borderBottom: '1px solid var(--glass-border)', paddingBottom: '12px', marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <User size={18} /> Dane Główne (Tylko do odczytu)
          </h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
            Aby zmienić te dane, udaj się do najbliższej placówki BankEuroB z dokumentem tożsamości.
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Imię i Nazwisko</label>
              <div style={{ padding: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', color: 'rgba(255,255,255,0.5)' }}>
                {profileData?.firstName} {profileData?.lastName}
              </div>
            </div>
            
            <div>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>PESEL</label>
              <div style={{ padding: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', color: 'rgba(255,255,255,0.5)' }}>
                {profileData?.pesel}
              </div>
            </div>

            <div>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Data urodzenia</label>
              <div style={{ padding: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', color: 'rgba(255,255,255,0.5)' }}>
                {profileData?.dateOfBirth}
              </div>
            </div>

            <div>
              <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Email (Login)</label>
              <div style={{ padding: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', color: 'rgba(255,255,255,0.5)' }}>
                {profileData?.email}
              </div>
            </div>
          </div>
        </div>

        {/* Sekcja formularza kontaktowego */}
        <div className="glass-panel" style={{ padding: '24px' }}>
          <h3 style={{ borderBottom: '1px solid var(--glass-border)', paddingBottom: '12px', marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <MapPin size={18} /> Dane Kontaktowe
          </h3>
          
          <form onSubmit={handleSubmit(onSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            
            <Input
              label="Numer telefonu"
              icon={<Phone size={18} />}
              error={errors.phone?.message}
              {...register('phone')}
            />

            <Input
              label="Ulica i numer domu/lokalu"
              icon={<MapPin size={18} />}
              error={errors.addressStreet?.message}
              {...register('addressStreet')}
            />

            <Input
              label="Miasto"
              icon={<MapPin size={18} />}
              error={errors.addressCity?.message}
              {...register('addressCity')}
            />

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Kraj rezydencji</label>
              <select 
                {...register('addressCountry')}
                style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none', appearance: 'none' }}
              >
                <option value="PL">Polska (PL)</option>
                <option value="DE">Niemcy (DE)</option>
                <option value="FR">Francja (FR)</option>
                <option value="ES">Hiszpania (ES)</option>
                <option value="IT">Włochy (IT)</option>
              </select>
              {errors.addressCountry && <span style={{ color: 'red', fontSize: '0.8rem' }}>{errors.addressCountry.message}</span>}
            </div>

            <Button 
              type="submit" 
              isLoading={isSubmitting} 
              style={{ marginTop: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
            >
              <Save size={18} /> Zapisz Zmiany
            </Button>
            
          </form>
        </div>

        {/* Sekcja BLIK PIN */}
        <div className="glass-panel" style={{ padding: '24px' }}>
          <h3 style={{ borderBottom: '1px solid var(--glass-border)', paddingBottom: '12px', marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <KeyRound size={18} color="var(--accent-orange)" /> BLIK – Kod PIN
          </h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
            {blikPinExists
              ? 'Możesz zmienić swój PIN do BLIK. Podaj obecny PIN, a następnie nowy.'
              : 'Ustaw PIN do BLIK. Będzie on wymagany przy każdej płatności BLIK.'}
          </p>

          {blikPinSuccess && (
            <div style={{ background: 'rgba(46, 204, 113, 0.2)', border: '1px solid var(--success-color)', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <CheckCircle2 size={18} />
              {blikPinExists ? 'PIN został zmieniony.' : 'PIN został ustawiony.'}
            </div>
          )}

          {blikPinError && (
            <div style={{ background: 'rgba(231, 76, 60, 0.2)', border: '1px solid var(--error-color)', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <AlertTriangle size={18} />
              {blikPinError}
            </div>
          )}

          <form onSubmit={handleSubmitBlik(onBlikPinSubmit)} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            
            {blikPinExists && (
              <div>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Obecny PIN</label>
                <input
                  type="password"
                  inputMode="numeric"
                  maxLength={4}
                  placeholder="****"
                  {...registerBlik('currentPin')}
                  style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none', fontSize: '1.2rem', letterSpacing: '8px', textAlign: 'center' }}
                />
                {blikErrors.currentPin && <span style={{ color: 'red', fontSize: '0.8rem' }}>{blikErrors.currentPin.message}</span>}
              </div>
            )}

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>{blikPinExists ? 'Nowy PIN' : 'Nowy PIN (4 cyfry)'}</label>
              <input
                type="password"
                inputMode="numeric"
                maxLength={4}
                placeholder="****"
                {...registerBlik('newPin')}
                style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none', fontSize: '1.2rem', letterSpacing: '8px', textAlign: 'center' }}
              />
              {blikErrors.newPin && <span style={{ color: 'red', fontSize: '0.8rem' }}>{blikErrors.newPin.message}</span>}
            </div>

            <div>
              <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Potwierdź nowy PIN</label>
              <input
                type="password"
                inputMode="numeric"
                maxLength={4}
                placeholder="****"
                {...registerBlik('confirmPin')}
                style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none', fontSize: '1.2rem', letterSpacing: '8px', textAlign: 'center' }}
              />
              {blikErrors.confirmPin && <span style={{ color: 'red', fontSize: '0.8rem' }}>{blikErrors.confirmPin.message}</span>}
            </div>

            <Button
              type="submit"
              isLoading={blikSubmitting}
              style={{ marginTop: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
            >
              <ShieldCheck size={18} /> {blikPinExists ? 'Zmień PIN' : 'Ustaw PIN'}
            </Button>

          </form>
        </div>

      </div>
    </div>
  );
};

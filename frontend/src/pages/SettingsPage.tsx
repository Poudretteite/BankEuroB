import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Settings, User, Phone, MapPin, Save } from 'lucide-react';
import axiosClient from '../api/axiosClient';


const settingsSchema = z.object({
  phone: z.string().min(5, 'Numer telefonu jest wymagany'),
  addressStreet: z.string().min(2, 'Podaj ulicę i numer domu'),
  addressCity: z.string().min(2, 'Podaj miasto'),
  addressCountry: z.string().min(2, 'Wybierz kraj'),
});

type SettingsForm = z.infer<typeof settingsSchema>;

export const SettingsPage: React.FC = () => {
  const [profileData, setProfileData] = useState<any>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<SettingsForm>({
    resolver: zodResolver(settingsSchema) as any,
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
        setIsLoading(false);
      } catch (err) {
        console.error('Błąd pobierania profilu', err);
        setIsLoading(false);
      }
    };
    fetchProfile();
  }, [setValue]);

  const onSubmit = async (data: SettingsForm) => {
    try {
      await axiosClient.put('/customers/me', data);
      setIsSuccess(true);
      setTimeout(() => setIsSuccess(false), 5000);
    } catch (err) {
      console.error('Błąd aktualizacji', err);
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

      </div>
    </div>
  );
};

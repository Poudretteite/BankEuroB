import React, { useState } from 'react';
import { Input } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { Mail, Lock, User, Wallet, CreditCard, Phone, MapPin, Calendar, ArrowRight, ArrowLeft, Check } from 'lucide-react';
import styles from './Auth.module.css';
import { Link, useNavigate } from 'react-router-dom';
import axiosClient from '../api/axiosClient';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [globalError, setGlobalError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    dateOfBirth: '',
    pesel: '',
    phone: '',
    addressStreet: '',
    addressCity: '',
    addressCountry: 'PL',
    email: '',
    password: '',
    confirmPassword: '',
    acceptTerms: false,
    acceptDataProcessing: false
  });

  const handleChange = (field: string, value: string | boolean) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (fieldErrors[field]) {
      setFieldErrors(prev => ({ ...prev, [field]: '' }));
    }
  };

  const validateStep = (currentStep: number) => {
    const errors: Record<string, string> = {};
    let isValid = true;

    if (currentStep === 1) {
      if (!formData.firstName.trim()) errors.firstName = 'Imię jest wymagane';
      if (!formData.lastName.trim()) errors.lastName = 'Nazwisko jest wymagane';
      if (!formData.dateOfBirth) errors.dateOfBirth = 'Data urodzenia jest wymagana';
      if (!formData.pesel.trim() || formData.pesel.length !== 11) errors.pesel = 'PESEL musi mieć 11 cyfr';
    } else if (currentStep === 2) {
      if (!formData.phone.trim()) errors.phone = 'Numer telefonu jest wymagany';
      if (!formData.addressStreet.trim()) errors.addressStreet = 'Ulica i numer domu są wymagane';
      if (!formData.addressCity.trim()) errors.addressCity = 'Miasto jest wymagane';
    } else if (currentStep === 3) {
      if (!formData.email.trim() || !formData.email.includes('@')) errors.email = 'Podaj poprawny email';
      if (!formData.password || formData.password.length < 8) errors.password = 'Hasło musi mieć min. 8 znaków';
      if (formData.password !== formData.confirmPassword) errors.confirmPassword = 'Hasła nie są identyczne';
    }

    if (Object.keys(errors).length > 0) {
      isValid = false;
      setFieldErrors(errors);
    }

    return isValid;
  };

  const nextStep = () => {
    if (validateStep(step)) {
      setStep(prev => prev + 1);
    }
  };

  const prevStep = () => {
    setStep(prev => prev - 1);
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.acceptTerms || !formData.acceptDataProcessing) {
      setGlobalError('Musisz zaakceptować wymagane zgody.');
      return;
    }

    setIsSubmitting(true);
    setGlobalError('');

    try {
      const payload = { ...formData };
      await axiosClient.post('/auth/register', payload);
      navigate('/login');
    } catch (err: any) {
      setGlobalError(err.response?.data?.message || 'Błąd rejestracji. Użytkownik o takim emailu lub PESELu może już istnieć.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles.authContainer}>
      <div className={`glass-panel ${styles.authCard}`} style={{ maxWidth: '500px', width: '100%' }}>
        <div className={styles.authHeader}>
          <Wallet className={styles.logoIcon} size={42} />
          <h1>Dołącz do BankEuroB</h1>
          <p>Krok {step} z 4</p>
        </div>

        {/* Progress Bar */}
        <div style={{ display: 'flex', gap: '8px', marginBottom: '32px' }}>
          {[1, 2, 3, 4].map(i => (
            <div 
              key={i} 
              style={{ 
                height: '4px', 
                flex: 1, 
                backgroundColor: i <= step ? 'var(--accent-blue)' : 'var(--glass-border)',
                borderRadius: '2px',
                transition: 'background-color 0.3s ease'
              }}
            />
          ))}
        </div>

        <form onSubmit={onSubmit} className={styles.authForm}>
          {/* STEP 1: Personal Data */}
          {step === 1 && (
            <div style={{ animation: 'fadeIn 0.3s ease', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h3 style={{ marginBottom: '8px' }}>Dane osobowe</h3>
              <Input
                label="Imię"
                placeholder="Jan"
                icon={<User size={18} />}
                value={formData.firstName}
                onChange={e => handleChange('firstName', e.target.value)}
                error={fieldErrors.firstName}
              />
              <Input
                label="Nazwisko"
                placeholder="Kowalski"
                icon={<User size={18} />}
                value={formData.lastName}
                onChange={e => handleChange('lastName', e.target.value)}
                error={fieldErrors.lastName}
              />
              <Input
                label="Data urodzenia"
                type="date"
                icon={<Calendar size={18} />}
                value={formData.dateOfBirth}
                onChange={e => handleChange('dateOfBirth', e.target.value)}
                error={fieldErrors.dateOfBirth}
              />
              <Input
                label="PESEL"
                placeholder="11 cyfr"
                icon={<CreditCard size={18} />}
                value={formData.pesel}
                onChange={e => handleChange('pesel', e.target.value)}
                error={fieldErrors.pesel}
              />
            </div>
          )}

          {/* STEP 2: Contact & Address */}
          {step === 2 && (
            <div style={{ animation: 'fadeIn 0.3s ease', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h3 style={{ marginBottom: '8px' }}>Dane kontaktowe</h3>
              <Input
                label="Numer telefonu"
                placeholder="+48 123 456 789"
                icon={<Phone size={18} />}
                value={formData.phone}
                onChange={e => handleChange('phone', e.target.value)}
                error={fieldErrors.phone}
              />
              <Input
                label="Ulica i numer domu/mieszkania"
                placeholder="Kwiatowa 15/2"
                icon={<MapPin size={18} />}
                value={formData.addressStreet}
                onChange={e => handleChange('addressStreet', e.target.value)}
                error={fieldErrors.addressStreet}
              />
              <Input
                label="Miasto"
                placeholder="Warszawa"
                icon={<MapPin size={18} />}
                value={formData.addressCity}
                onChange={e => handleChange('addressCity', e.target.value)}
                error={fieldErrors.addressCity}
              />
              <div style={{ marginBottom: '4px' }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Kraj (prefiks IBAN)</label>
                <select 
                  value={formData.addressCountry}
                  onChange={e => handleChange('addressCountry', e.target.value)}
                  style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none', appearance: 'none' }}
                >
                  <option value="PL">Polska (PL)</option>
                  <option value="DE">Niemcy (DE)</option>
                  <option value="FR">Francja (FR)</option>
                  <option value="ES">Hiszpania (ES)</option>
                  <option value="IT">Włochy (IT)</option>
                </select>
              </div>
            </div>
          )}

          {/* STEP 3: Login Data */}
          {step === 3 && (
            <div style={{ animation: 'fadeIn 0.3s ease', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h3 style={{ marginBottom: '8px' }}>Dane logowania</h3>
              <Input
                label="Adres email (Login)"
                type="email"
                placeholder="jan.kowalski@example.com"
                icon={<Mail size={18} />}
                value={formData.email}
                onChange={e => handleChange('email', e.target.value)}
                error={fieldErrors.email}
              />
              <Input
                label="Hasło dostępu"
                type="password"
                placeholder="••••••••"
                icon={<Lock size={18} />}
                value={formData.password}
                onChange={e => handleChange('password', e.target.value)}
                error={fieldErrors.password}
              />
              <Input
                label="Powtórz hasło"
                type="password"
                placeholder="••••••••"
                icon={<Lock size={18} />}
                value={formData.confirmPassword}
                onChange={e => handleChange('confirmPassword', e.target.value)}
                error={fieldErrors.confirmPassword}
              />
            </div>
          )}

          {/* STEP 4: Consents */}
          {step === 4 && (
            <div style={{ animation: 'fadeIn 0.3s ease', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <h3 style={{ marginBottom: '8px' }}>Zgody i oświadczenia</h3>
              
              <label style={{ display: 'flex', gap: '12px', cursor: 'pointer', alignItems: 'flex-start' }}>
                <input 
                  type="checkbox" 
                  checked={formData.acceptTerms}
                  onChange={e => handleChange('acceptTerms', e.target.checked)}
                  style={{ width: '18px', height: '18px', marginTop: '4px' }}
                />
                <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                  <strong style={{ color: 'var(--text-primary)' }}>*Akceptuję Regulamin</strong> świadczenia usług bankowych drogą elektroniczną w ramach BankEuroB.
                </span>
              </label>

              <label style={{ display: 'flex', gap: '12px', cursor: 'pointer', alignItems: 'flex-start' }}>
                <input 
                  type="checkbox" 
                  checked={formData.acceptDataProcessing}
                  onChange={e => handleChange('acceptDataProcessing', e.target.checked)}
                  style={{ width: '18px', height: '18px', marginTop: '4px' }}
                />
                <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                  <strong style={{ color: 'var(--text-primary)' }}>*Wyrażam zgodę na przetwarzanie danych osobowych</strong> w celu zawarcia i realizacji umowy o prowadzenie rachunku bankowego.
                </span>
              </label>

              {globalError && (
                <div className={styles.globalError} style={{ marginTop: '10px' }}>{globalError}</div>
              )}
            </div>
          )}

          <div style={{ display: 'flex', gap: '12px', marginTop: '32px' }}>
            {step > 1 && (
              <Button 
                type="button" 
                variant="outline" 
                onClick={prevStep}
                style={{ flex: 1 }}
              >
                <ArrowLeft size={18} /> Wstecz
              </Button>
            )}
            
            {step < 4 ? (
              <Button 
                type="button" 
                onClick={nextStep}
                style={{ flex: 2 }}
              >
                Dalej <ArrowRight size={18} />
              </Button>
            ) : (
              <Button 
                type="submit" 
                isLoading={isSubmitting}
                style={{ flex: 2 }}
                disabled={!formData.acceptTerms || !formData.acceptDataProcessing}
              >
                <Check size={18} /> Utwórz konto
              </Button>
            )}
          </div>

          <p className={styles.authFooter} style={{ marginTop: '24px' }}>
            Masz już konto? <Link to="/login">Zaloguj się</Link>
          </p>
        </form>
      </div>
    </div>
  );
};

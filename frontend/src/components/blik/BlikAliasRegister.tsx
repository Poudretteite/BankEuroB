import React, { useState } from 'react';
import { Button } from '../ui/Button';
import { Smartphone, CheckCircle2, AlertTriangle, Save } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

interface BlikAliasRegisterProps {
  accounts: any[];
}

export const BlikAliasRegister: React.FC<BlikAliasRegisterProps> = ({ accounts }) => {
  const [aliasPhone, setAliasPhone] = useState('');
  const [aliasLoading, setAliasLoading] = useState(false);
  const [aliasSuccess, setAliasSuccess] = useState(false);
  const [aliasError, setAliasError] = useState('');

  const onRegisterAlias = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!aliasPhone || aliasPhone.length < 5) {
      setAliasError('Podaj prawidłowy numer telefonu (min. 5 znaków).');
      return;
    }
    if (!accounts || accounts.length === 0) {
      setAliasError('Brak konta powiązanego z Twoim profilem.');
      return;
    }

    setAliasLoading(true);
    setAliasError('');
    setAliasSuccess(false);

    try {
      const iban = accounts[0].iban;
      await axiosClient.post(`/klik/aliases/register?phone=${encodeURIComponent(aliasPhone)}&iban=${iban}&zone=PL`);
      setAliasSuccess(true);
      setAliasPhone('');
      setTimeout(() => setAliasSuccess(false), 5000);
    } catch (err: any) {
      const message = err.response?.data?.error || err.response?.data?.message || 'Nie udało się zarejestrować aliasu.';
      setAliasError(message);
    } finally {
      setAliasLoading(false);
    }
  };

  return (
    <div className="glass-panel" style={{ padding: '24px', marginTop: '24px' }}>
      <h3 style={{ borderBottom: '1px solid var(--glass-border)', paddingBottom: '12px', marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <Smartphone size={18} color="var(--accent-blue)" /> Odbieraj przelewy na telefon (KLIK)
      </h3>
      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
        Zarejestruj swój numer telefonu, aby inni mogli wysyłać Ci przelewy natychmiastowe bez podawania numeru konta.
      </p>

      {aliasSuccess && (
        <div style={{ background: 'rgba(46, 204, 113, 0.2)', border: '1px solid var(--success-color)', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <CheckCircle2 size={18} />
          Alias został pomyślnie zarejestrowany.
        </div>
      )}

      {aliasError && (
        <div style={{ background: 'rgba(231, 76, 60, 0.2)', border: '1px solid var(--error-color)', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertTriangle size={18} />
          {aliasError}
        </div>
      )}

      <form onSubmit={onRegisterAlias} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Twój numer telefonu (np. +48111222333)</label>
          <input
            type="text"
            placeholder="+48..."
            value={aliasPhone}
            onChange={(e) => setAliasPhone(e.target.value)}
            style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none' }}
          />
        </div>

        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Konto powiązane</label>
          <div style={{ padding: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px', color: 'rgba(255,255,255,0.5)', fontSize: '0.9rem' }}>
            {accounts.length > 0 ? accounts[0].iban : 'Brak przypisanego konta'}
          </div>
        </div>

        <Button
          type="submit"
          isLoading={aliasLoading}
          disabled={accounts.length === 0}
          style={{ marginTop: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
        >
          <Save size={18} /> Zarejestruj numer
        </Button>
      </form>
    </div>
  );
};

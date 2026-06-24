import React, { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '../ui/Button';
import { Send, CheckCircle2, AlertTriangle } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

interface BlikP2PTransferProps {
  accounts: any[];
}

export const BlikP2PTransfer: React.FC<BlikP2PTransferProps> = ({ accounts }) => {
  const queryClient = useQueryClient();
  const [receiverPhone, setReceiverPhone] = useState('');
  const [amount, setAmount] = useState('');
  const [title, setTitle] = useState('');
  const [receiverName, setReceiverName] = useState('');
  
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');

  const onSendTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!receiverPhone || receiverPhone.length < 5) {
      setError('Podaj prawidłowy numer telefonu.');
      return;
    }
    if (!amount || Number(amount) <= 0) {
      setError('Podaj prawidłową kwotę.');
      return;
    }
    if (!title) {
      setError('Tytuł jest wymagany.');
      return;
    }
    if (!receiverName) {
      setError('Nazwa odbiorcy jest wymagana.');
      return;
    }

    setLoading(true);
    setError('');
    setSuccess(false);

    try {
      // 1. Lookup
      const lookupRes = await axiosClient.get(`/klik/aliases/lookup/${encodeURIComponent(receiverPhone)}`);
      if (!lookupRes.data || !lookupRes.data.account_identifier || !lookupRes.data.account_identifier.value) {
        setError('Nie znaleziono powiązanego konta dla tego numeru telefonu.');
        setLoading(false);
        return;
      }
      const targetIban = lookupRes.data.account_identifier.value;

      // 2. Send SEPA_INSTANT
      await axiosClient.post('/transfers', {
        transferType: 'SEPA_INSTANT',
        senderIban: accounts[0].iban,
        receiverIban: targetIban,
        receiverBic: lookupRes.data.bank_code,
        receiverName: receiverName,
        title: title,
        amount: Number(amount),
        currency: 'EUR'
      });
      
      setSuccess(true);
      setReceiverPhone('');
      setAmount('');
      setTitle('');
      setReceiverName('');
      
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      setTimeout(() => setSuccess(false), 5000);
    } catch (err: any) {
      const message = err.response?.data?.error || err.response?.data?.message || 'Nie udało się wysłać przelewu.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="glass-panel" style={{ padding: '24px', marginTop: '24px' }}>
      <h3 style={{ borderBottom: '1px solid var(--glass-border)', paddingBottom: '12px', marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
        <Send size={18} color="var(--accent-orange)" /> Wyślij przelew na telefon (KLIK)
      </h3>
      <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
        Wyślij środki natychmiastowo na numer telefonu zarejestrowany w systemie KLIK.
      </p>

      {success && (
        <div style={{ background: 'rgba(46, 204, 113, 0.2)', border: '1px solid var(--success-color)', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <CheckCircle2 size={18} />
          Przelew został wysłany pomyślnie.
        </div>
      )}

      {error && (
        <div style={{ background: 'rgba(231, 76, 60, 0.2)', border: '1px solid var(--error-color)', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertTriangle size={18} />
          {error}
        </div>
      )}

      <form onSubmit={onSendTransfer} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
          <div>
            <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Numer telefonu odbiorcy</label>
            <input
              type="text"
              placeholder="+48..."
              value={receiverPhone}
              onChange={(e) => setReceiverPhone(e.target.value)}
              style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none' }}
            />
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Kwota (EUR)</label>
            <input
              type="number"
              step="0.01"
              placeholder="0.00"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none' }}
            />
          </div>
        </div>
        
        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Nazwa odbiorcy</label>
          <input
            type="text"
            placeholder="Jan Kowalski"
            value={receiverName}
            onChange={(e) => setReceiverName(e.target.value)}
            style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none' }}
          />
        </div>

        <div>
          <label style={{ display: 'block', marginBottom: '8px', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Tytuł przelewu</label>
          <input
            type="text"
            placeholder="Za pizzę"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none' }}
          />
        </div>

        <Button
          type="submit"
          isLoading={loading}
          disabled={accounts.length === 0}
          style={{ marginTop: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
        >
          <Send size={18} /> Wyślij przelew
        </Button>
      </form>
    </div>
  );
};

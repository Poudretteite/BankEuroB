import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axiosClient from '../api/axiosClient';
import { Check, X, UserPlus, Shield, User, Mail, Lock, Calendar, Fingerprint, Send, CreditCard, Phone, MapPin } from 'lucide-react';
import { Button } from './ui/Button';
import { Input } from './ui/Input';

export const JuniorManagementPanel: React.FC = () => {
  const queryClient = useQueryClient();
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
    dateOfBirth: '2015-01-01',
    pesel: '',
    phone: '',
    addressStreet: '',
    addressCity: '',
    addressCountry: 'PL'
  });

  const { data: pendingLogins } = useQuery({
    queryKey: ['pendingLogins'],
    queryFn: async () => {
      const res = await axiosClient.get('/junior/pending-logins');
      return res.data;
    },
    refetchInterval: 5000,
  });

  const { data: pendingTransfers } = useQuery({
    queryKey: ['pendingTransfers'],
    queryFn: async () => {
      const res = await axiosClient.get('/junior/pending-transfers');
      return res.data;
    },
    refetchInterval: 5000,
  });

  const approveLoginMutation = useMutation({
    mutationFn: async ({ id, approved }: { id: string; approved: boolean }) => {
      await axiosClient.post(`/junior/approve-login/${id}?approved=${approved}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pendingLogins'] }),
  });

  const approveTransferMutation = useMutation({
    mutationFn: async ({ id, approved }: { id: string; approved: boolean }) => {
      await axiosClient.post(`/junior/approve-transfer/${id}?approved=${approved}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pendingTransfers'] }),
  });

  const createJuniorMutation = useMutation({
    mutationFn: async (data: any) => {
      // Remove confirmPassword before sending
      const { confirmPassword, ...payload } = data;
      await axiosClient.post('/junior/account', payload);
    },
    onSuccess: () => {
      setShowCreateForm(false);
      alert('Konto Junior utworzone!');
    },
    onError: (error: any) => {
      alert(error.response?.data?.message || 'Wystąpił błąd podczas tworzenia konta.');
    }
  });

  const handleCreate = () => {
    if (formData.password !== formData.confirmPassword) {
      setPasswordError('Hasła nie są identyczne');
      return;
    }
    setPasswordError('');
    createJuniorMutation.mutate(formData);
  };

  return (
    <div className="glass-panel" style={{ padding: '24px', marginTop: '24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
        <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '10px', fontSize: '1.25rem' }}>
          <Shield size={24} color="var(--accent-gold)" /> Panel Zarządzania Rodzicielskiego
        </h3>
        <Button 
          variant={showCreateForm ? 'outline' : 'primary'}
          size="sm" 
          onClick={() => setShowCreateForm(!showCreateForm)}
        >
          {showCreateForm ? <X size={16} /> : <UserPlus size={16} />}
          {showCreateForm ? 'Anuluj' : 'Załóż konto Junior'}
        </Button>
      </div>

      {showCreateForm && (
        <div style={{ 
          background: 'rgba(255, 255, 255, 0.03)', 
          padding: '24px', 
          borderRadius: '16px', 
          marginBottom: '32px',
          border: '1px solid var(--glass-border)',
          animation: 'fadeIn 0.3s ease'
        }}>
          <h4 style={{ margin: '0 0 20px 0', fontSize: '1.1rem', color: 'var(--accent-gold)' }}>Nowy Profil Dziecka</h4>
          <div className="junior-form-grid">
            <Input 
              label="Imię" 
              placeholder="np. Kamil"
              icon={<User size={18} />}
              value={formData.firstName} 
              onChange={e => setFormData({...formData, firstName: e.target.value})} 
            />
            <Input 
              label="Nazwisko" 
              placeholder="np. Kowalski"
              icon={<User size={18} />}
              value={formData.lastName} 
              onChange={e => setFormData({...formData, lastName: e.target.value})} 
            />
            <Input 
              label="PESEL" 
              placeholder="11 cyfr"
              icon={<CreditCard size={18} />}
              value={formData.pesel} 
              onChange={e => setFormData({...formData, pesel: e.target.value})} 
            />
            <Input 
              label="Data urodzenia"
              type="date" 
              icon={<Calendar size={18} />}
              value={formData.dateOfBirth} 
              onChange={e => setFormData({...formData, dateOfBirth: e.target.value})} 
            />
            <Input 
              label="Email (identyfikator logowania)" 
              placeholder="kamil.k@example.com"
              icon={<Mail size={18} />}
              value={formData.email} 
              onChange={e => setFormData({...formData, email: e.target.value})} 
            />
            <Input 
              label="Numer telefonu" 
              placeholder="+48 123 456 789"
              icon={<Phone size={18} />}
              value={formData.phone} 
              onChange={e => setFormData({...formData, phone: e.target.value})} 
            />
            <Input 
              label="Ulica i numer domu" 
              placeholder="np. Kwiatowa 15/2"
              icon={<MapPin size={18} />}
              value={formData.addressStreet} 
              onChange={e => setFormData({...formData, addressStreet: e.target.value})} 
            />
            <Input 
              label="Miasto" 
              placeholder="np. Warszawa"
              icon={<MapPin size={18} />}
              value={formData.addressCity} 
              onChange={e => setFormData({...formData, addressCity: e.target.value})} 
            />
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <label style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>Kraj (prefiks IBAN)</label>
              <select 
                value={formData.addressCountry}
                onChange={e => setFormData({...formData, addressCountry: e.target.value})}
                style={{ width: '100%', padding: '12px 16px', borderRadius: '8px', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--glass-border)', color: 'white', outline: 'none', appearance: 'none', fontSize: '1rem' }}
              >
                <option value="PL">Polska (PL)</option>
                <option value="DE">Niemcy (DE)</option>
                <option value="FR">Francja (FR)</option>
                <option value="ES">Hiszpania (ES)</option>
                <option value="IT">Włochy (IT)</option>
              </select>
            </div>
            <Input 
              label="Hasło dostępu" 
              type="password" 
              placeholder="••••••••"
              icon={<Lock size={18} />}
              value={formData.password} 
              onChange={e => setFormData({...formData, password: e.target.value})} 
            />
            <Input 
              label="Powtórz hasło" 
              type="password" 
              placeholder="••••••••"
              icon={<Lock size={18} />}
              error={passwordError}
              value={formData.confirmPassword} 
              onChange={e => setFormData({...formData, confirmPassword: e.target.value})} 
            />
            <div style={{ display: 'flex', alignItems: 'flex-end', gridColumn: '1 / -1', marginTop: '10px' }}>
              <Button 
                style={{ width: '100%' }}
                onClick={handleCreate} 
                isLoading={createJuniorMutation.isPending}
              >
                Utwórz konto
              </Button>
            </div>
          </div>
        </div>
      )}

      {(pendingLogins && pendingLogins.length > 0) || (pendingTransfers && pendingTransfers.length > 0) ? (
        <div className="space-y-6">
          {pendingLogins && pendingLogins.length > 0 && (
            <div style={{ marginBottom: '24px' }}>
              <h4 style={{ color: 'var(--text-secondary)', marginBottom: '16px', fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Fingerprint size={16} /> OCZEKUJĄCE LOGOWANIA (2FA)
              </h4>
              {pendingLogins.map((login: any) => (
                <div key={login.id} className="pending-item">
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span className="status-tag login">Weryfikacja</span>
                      <strong style={{ fontSize: '0.95rem' }}>Próba logowania</strong>
                    </div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                      Inicjacja: {new Date(login.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <Button 
                      size="sm" 
                      onClick={() => approveLoginMutation.mutate({ id: login.id, approved: true })} 
                      style={{ background: 'var(--success-color)' }}
                    >
                      <Check size={14} /> Akceptuj
                    </Button>
                    <Button 
                      size="sm" 
                      variant="outline"
                      onClick={() => approveLoginMutation.mutate({ id: login.id, approved: false })} 
                      style={{ color: 'var(--error-color)', borderColor: 'rgba(239, 68, 68, 0.3)' }}
                    >
                      <X size={14} />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {pendingTransfers && pendingTransfers.length > 0 && (
            <div>
              <h4 style={{ color: 'var(--text-secondary)', marginBottom: '16px', fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Send size={16} /> PRZELEWY DO ZATWIERDZENIA
              </h4>
              {pendingTransfers.map((tx: any) => (
                <div key={tx.id} className="pending-item">
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span className="status-tag transfer">Przelew</span>
                      <strong style={{ fontSize: '1.1rem', color: 'var(--accent-gold)' }}>
                        {tx.amount} {tx.currency}
                      </strong>
                    </div>
                    <div style={{ fontSize: '0.85rem' }}>
                      Do: <span style={{ color: 'var(--text-primary)' }}>{tx.receiverName}</span>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontStyle: 'italic' }}>
                      Tytuł: {tx.title}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <Button 
                      size="sm" 
                      onClick={() => approveTransferMutation.mutate({ id: tx.id, approved: true })} 
                      style={{ background: 'var(--success-color)' }}
                    >
                      <Check size={14} /> Zatwierdź
                    </Button>
                    <Button 
                      size="sm" 
                      variant="outline"
                      onClick={() => approveTransferMutation.mutate({ id: tx.id, approved: false })} 
                      style={{ color: 'var(--error-color)', borderColor: 'rgba(239, 68, 68, 0.3)' }}
                    >
                      <X size={14} />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <div style={{ 
          color: 'var(--text-secondary)', 
          fontSize: '0.9rem', 
          textAlign: 'center', 
          padding: '40px 20px',
          background: 'rgba(255,255,255,0.02)',
          borderRadius: '12px',
          border: '1px dashed var(--glass-border)'
        }}>
          Brak oczekujących akcji. Twoje dziecko nie ma obecnie żadnych operacji wymagających autoryzacji.
        </div>
      )}
    </div>
  );
};

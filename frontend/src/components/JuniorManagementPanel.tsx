import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axiosClient from '../api/axiosClient';
import { Check, X, UserPlus, Shield } from 'lucide-react';
import { Button } from './ui/Button';

export const JuniorManagementPanel: React.FC = () => {
  const queryClient = useQueryClient();
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    dateOfBirth: '2015-01-01',
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
    mutationFn: async (data: typeof formData) => {
      await axiosClient.post('/junior/account', data);
    },
    onSuccess: () => {
      setShowCreateForm(false);
      alert('Konto Junior utworzone!');
    },
  });

  return (
    <div className="glass-panel" style={{ padding: '20px', marginTop: '20px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Shield size={20} color="var(--accent-gold)" /> Panel Rodzica (Konta Junior)
        </h3>
        <Button size="sm" onClick={() => setShowCreateForm(!showCreateForm)}>
          <UserPlus size={16} /> Załóż konto
        </Button>
      </div>

      {showCreateForm && (
        <div style={{ background: 'rgba(0,0,0,0.2)', padding: '15px', borderRadius: '8px', marginBottom: '20px' }}>
          <h4>Nowe konto Junior</h4>
          <div style={{ display: 'grid', gap: '10px', gridTemplateColumns: '1fr 1fr' }}>
            <input placeholder="Imię" value={formData.firstName} onChange={e => setFormData({...formData, firstName: e.target.value})} className="modern-input" />
            <input placeholder="Nazwisko" value={formData.lastName} onChange={e => setFormData({...formData, lastName: e.target.value})} className="modern-input" />
            <input placeholder="Email (Login)" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} className="modern-input" />
            <input placeholder="Hasło" type="password" value={formData.password} onChange={e => setFormData({...formData, password: e.target.value})} className="modern-input" />
            <input type="date" value={formData.dateOfBirth} onChange={e => setFormData({...formData, dateOfBirth: e.target.value})} className="modern-input" />
            <Button size="sm" onClick={() => createJuniorMutation.mutate(formData)} isLoading={createJuniorMutation.isPending}>Utwórz</Button>
          </div>
        </div>
      )}

      {pendingLogins && pendingLogins.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ color: 'var(--accent-gold)' }}>Oczekujące logowania (2FA)</h4>
          {pendingLogins.map((login: any) => (
            <div key={login.id} style={{ display: 'flex', justifyContent: 'space-between', background: 'rgba(255,255,255,0.05)', padding: '10px', borderRadius: '8px', marginBottom: '5px' }}>
              <span>Dziecko próbuje się zalogować... ({new Date(login.createdAt).toLocaleTimeString()})</span>
              <div style={{ display: 'flex', gap: '10px' }}>
                <Button size="sm" onClick={() => approveLoginMutation.mutate({ id: login.id, approved: true })} style={{ background: 'green' }}><Check size={14} /> Akceptuj</Button>
                <Button size="sm" onClick={() => approveLoginMutation.mutate({ id: login.id, approved: false })} variant="outline" style={{ borderColor: 'red', color: 'red' }}><X size={14} /> Odrzuć</Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {pendingTransfers && pendingTransfers.length > 0 && (
        <div>
          <h4 style={{ color: 'var(--accent-gold)' }}>Przelewy wymagające autoryzacji</h4>
          {pendingTransfers.map((tx: any) => (
            <div key={tx.id} style={{ display: 'flex', justifyContent: 'space-between', background: 'rgba(255,255,255,0.05)', padding: '10px', borderRadius: '8px', marginBottom: '5px' }}>
              <div>
                <strong>{tx.amount} {tx.currency}</strong> do {tx.receiverName}
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Tytuł: {tx.title}</div>
              </div>
              <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                <Button size="sm" onClick={() => approveTransferMutation.mutate({ id: tx.id, approved: true })} style={{ background: 'green' }}><Check size={14} /> Akceptuj</Button>
                <Button size="sm" onClick={() => approveTransferMutation.mutate({ id: tx.id, approved: false })} variant="outline" style={{ borderColor: 'red', color: 'red' }}><X size={14} /> Odrzuć</Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {(!pendingLogins || pendingLogins.length === 0) && (!pendingTransfers || pendingTransfers.length === 0) && (
        <div style={{ color: 'var(--text-secondary)', fontSize: '14px', textAlign: 'center', padding: '10px' }}>
          Brak oczekujących akcji.
        </div>
      )}
    </div>
  );
};

import React, { useEffect, useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, Clock, Building2, ShieldCheck, Zap, Receipt } from 'lucide-react';
import axiosClient from '../api/axiosClient';

interface Transaction {
  id: string;
  referenceNumber: string;
  transactionType: string;
  status: string;
  senderIban: string;
  senderName: string;
  receiverIban: string;
  receiverName: string;
  amount: number;
  currency: string;
  title: string;
  requestedAt: string;
  completedAt: string | null;
}

export const HistoryPage: React.FC = () => {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [myIban, setMyIban] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [filter, setFilter] = useState<'ALL' | 'INCOME' | 'EXPENSE'>('ALL');

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const accountsRes = await axiosClient.get('/accounts');
        if (accountsRes.data && accountsRes.data.length > 0) {
          const mainIban = accountsRes.data[0].iban;
          setMyIban(mainIban);

          const txRes = await axiosClient.get(`/transfers?iban=${mainIban}`);
          setTransactions(txRes.data.reverse()); // Nowsze na górze (jeśli backend daje rosnąco)
        }
        setIsLoading(false);
      } catch (err) {
        console.error('Błąd pobierania historii', err);
        setIsLoading(false);
      }
    };
    fetchHistory();
  }, []);

  const getFeeValue = (type: string) => {
    switch(type) {
      case 'SEPA_INSTANT': return 0.50;
      case 'RTGS_TARGET2': return 5.00;
      default: return 0.00;
    }
  };

  const getStatusBadge = (status: string) => {
    switch(status) {
      case 'COMPLETED': return <span style={{ background: 'rgba(46, 204, 113, 0.2)', color: 'var(--success-color)', padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem' }}>Zrealizowano</span>;
      case 'PROCESSING': return <span style={{ background: 'rgba(0, 168, 255, 0.2)', color: 'var(--accent-blue)', padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem' }}>Przetwarzanie</span>;
      case 'PENDING': return <span style={{ background: 'rgba(241, 196, 15, 0.2)', color: '#f1c40f', padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem' }}>Oczekuje (Junior)</span>;
      case 'REJECTED': return <span style={{ background: 'rgba(231, 76, 60, 0.2)', color: '#e74c3c', padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem' }}>Odrzucono</span>;
      case 'FAILED': return <span style={{ background: 'rgba(231, 76, 60, 0.2)', color: '#e74c3c', padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem' }}>Błąd</span>;
      default: return <span style={{ background: 'rgba(255, 255, 255, 0.1)', color: 'white', padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem' }}>{status}</span>;
    }
  };

  const getTypeIcon = (type: string) => {
    switch(type) {
      case 'INTERNAL': return <Building2 size={16} color="var(--accent-gold)" />;
      case 'SEPA_SCT': return <Clock size={16} color="var(--accent-blue)" />;
      case 'SEPA_INSTANT': return <Zap size={16} color="var(--success-color)" />;
      case 'RTGS_TARGET2': return <ShieldCheck size={16} color="#e74c3c" />;
      default: return <Receipt size={16} color="var(--text-secondary)" />;
    }
  };

  const filteredTransactions = transactions.filter(tx => {
    const isIncome = tx.receiverIban === myIban;
    if (filter === 'INCOME') return isIncome;
    if (filter === 'EXPENSE') return !isIncome;
    return true;
  });

  if (isLoading) {
    return <div style={{ color: 'white', padding: '2rem', textAlign: 'center' }}>Ładowanie historii...</div>;
  }

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', padding: '2rem 1rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
        <Clock size={32} color="var(--accent-blue)" />
        <div>
          <h1 style={{ fontSize: '2rem', color: 'var(--text-primary)', margin: 0 }}>Historia Transakcji</h1>
          <p style={{ color: 'var(--text-secondary)', margin: 0 }}>Przeglądaj wszystkie wpływy, wydatki i opłaty</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '8px', marginBottom: '24px' }}>
        <button 
          onClick={() => setFilter('ALL')}
          style={{ padding: '8px 16px', borderRadius: '8px', border: '1px solid var(--glass-border)', background: filter === 'ALL' ? 'rgba(255,255,255,0.1)' : 'transparent', color: 'white', cursor: 'pointer' }}
        >
          Wszystkie
        </button>
        <button 
          onClick={() => setFilter('INCOME')}
          style={{ padding: '8px 16px', borderRadius: '8px', border: '1px solid var(--success-color)', background: filter === 'INCOME' ? 'rgba(46, 204, 113, 0.1)' : 'transparent', color: 'var(--success-color)', cursor: 'pointer' }}
        >
          Wpływy
        </button>
        <button 
          onClick={() => setFilter('EXPENSE')}
          style={{ padding: '8px 16px', borderRadius: '8px', border: '1px solid #e74c3c', background: filter === 'EXPENSE' ? 'rgba(231, 76, 60, 0.1)' : 'transparent', color: '#e74c3c', cursor: 'pointer' }}
        >
          Wydatki
        </button>
      </div>

      <div className="glass-panel" style={{ padding: '0', overflow: 'hidden' }}>
        {filteredTransactions.length === 0 ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-secondary)' }}>Brak transakcji do wyświetlenia.</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {filteredTransactions.map((tx, idx) => {
              const isIncome = tx.receiverIban === myIban;
              const dateObj = new Date(tx.requestedAt);
              const fee = getFeeValue(tx.transactionType);
              
              return (
                <div 
                  key={tx.id} 
                  style={{ 
                    padding: '20px', 
                    borderBottom: idx === filteredTransactions.length - 1 ? 'none' : '1px solid var(--glass-border)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '16px',
                    transition: 'background 0.2s ease'
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.02)'}
                  onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                >
                  <div style={{ 
                    width: '48px', height: '48px', borderRadius: '50%', 
                    background: isIncome ? 'rgba(46, 204, 113, 0.1)' : 'rgba(231, 76, 60, 0.1)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center'
                  }}>
                    {isIncome ? <ArrowDownLeft size={24} color="var(--success-color)" /> : <ArrowUpRight size={24} color="#e74c3c" />}
                  </div>

                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '4px' }}>
                      <strong style={{ fontSize: '1.1rem', color: 'var(--text-primary)' }}>{tx.title}</strong>
                      <strong style={{ fontSize: '1.1rem', color: isIncome ? 'var(--success-color)' : '#e74c3c' }}>
                        {isIncome ? '+' : '-'}{tx.amount.toFixed(2)} {tx.currency}
                      </strong>
                    </div>
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <span>
                          {isIncome ? 'Od: ' : 'Do: '} 
                          <strong style={{ color: 'rgba(255,255,255,0.7)' }}>{isIncome ? (tx.senderName || tx.senderIban) : (tx.receiverName || tx.receiverIban)}</strong>
                        </span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }} title="Typ transakcji">
                            {getTypeIcon(tx.transactionType)}
                            {tx.transactionType}
                          </span>
                          <span>•</span>
                          <span>{dateObj.toLocaleDateString()} {dateObj.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</span>
                        </div>
                      </div>
                      
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                        {getStatusBadge(tx.status)}
                        {!isIncome && fee > 0 && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                            Prowizja wliczona: {fee.toFixed(2)} EUR
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

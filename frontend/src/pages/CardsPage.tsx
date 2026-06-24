import type React from 'react';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axiosClient from '../api/axiosClient';
import { useAuthStore } from '../store/useAuthStore';
import styles from './Cards.module.css';
import {
  CreditCard,
  Plus,
  Lock,
  Unlock,
  RefreshCw,
  AlertCircle,
  CheckCircle,
  Clock,
  Settings
} from 'lucide-react';

interface Card {
  cardToken: string;
  maskedPan: string;
  status: string;
  cardType: string;
  balance: number;
  dailyLimit: number;
  monthlyLimit: number;
  dailyTxnLimit: number;
  monthlyTxnLimit: number;
  bankId: string;
}

interface IssueResponse {
  status: string;
  cardToken: string;
  maskedPan: string;
  fullPan: string;
  cvv: string;
  expiryMonth: number;
  expiryYear: number;
  cardType: string;
  message: string;
}

const CARD_TYPES = [
  { value: 'VIRTUAL', label: 'Wirtualna', desc: 'Aktywuje się automatycznie w ciągu 1h' },
  { value: 'PHYSICAL', label: 'Fizyczna', desc: 'Wymaga aktywacji po otrzymaniu' },
  { value: 'PREPAID', label: 'Prepaid', desc: 'Z własnym saldem, aktywacja ręczna' },
];

const STATUS_BADGES: Record<string, { label: string; className: string }> = {
  REQUESTED: { label: 'Zamówiona', className: 'statusRequested' },
  PRODUCING: { label: 'W produkcji', className: 'statusProducing' },
  SHIPPED: { label: 'Wysłana', className: 'statusShipped' },
  ACTIVE: { label: 'Aktywna', className: 'statusActive' },
  BLOCKED: { label: 'Zablokowana', className: 'statusBlocked' },
};

export const CardsPage: React.FC = () => {
  const { user } = useAuthStore();
  const queryClient = useQueryClient();
  const [selectedType, setSelectedType] = useState('VIRTUAL');
  const [showIssueForm, setShowIssueForm] = useState(false);
  const [newCardData, setNewCardData] = useState<IssueResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Fetch accounts to check if user is a Junior
  const { data: accountsData } = useQuery({
    queryKey: ['accounts'],
    queryFn: async () => {
      const response = await axiosClient.get<{ accountType: string }[]>('/accounts');
      return response.data || [];
    }
  });
  const isJunior = accountsData?.some(acc => acc.accountType === 'JUNIOR');

  // Stan dla doładowania
  const [topupFor, setTopupFor] = useState<string | null>(null);
  const [topupAmount, setTopupAmount] = useState<number>(50);

  // Stan dla edycji limitów
  const [editingLimitsFor, setEditingLimitsFor] = useState<string | null>(null);
  const [limitValues, setLimitValues] = useState({
    dailyLimit: 0,
    monthlyLimit: 0,
    dailyTxnLimit: 0,
    monthlyTxnLimit: 0,
  });

  // Pobieranie listy kart
  const { data: cards, isLoading, refetch } = useQuery({
    queryKey: ['externalCards'],
    queryFn: async () => {
      const response = await axiosClient.get<{ cards: Card[] }>('/cards');
      return response.data.cards || [];
    },
    refetchInterval: 30000,
  });

  // Wydawanie karty
  const issueMutation = useMutation({
    mutationFn: async (cardType: string) => {
      const response = await axiosClient.post<IssueResponse>('/cards/issue', {
        userId: user?.email || 'user',
        cardType: cardType,
        initialBalance: 0.0,
      });
      return response.data;
    },
    onSuccess: (data) => {
      setNewCardData(data);
      setShowIssueForm(false);
      setError(null);
      setTimeout(() => queryClient.invalidateQueries({ queryKey: ['externalCards'] }), 2000);
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || err.message || 'Nieznany błąd');
    },
  });

  // Zmiana statusu karty
  const statusMutation = useMutation({
    mutationFn: async ({ cardToken, newStatus, reason }: { cardToken: string; newStatus: string; reason?: string }) => {
      await axiosClient.patch(`/cards/${cardToken}/status`, {
        status: newStatus,
        reason: reason || '',
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['externalCards'] });
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || err.message);
    },
  });

  // Zmiana limitów
  const limitsMutation = useMutation({
    mutationFn: async ({ cardToken, limits }: { cardToken: string; limits: any }) => {
      await axiosClient.patch(`/cards/${cardToken}/limits`, limits);
    },
    onSuccess: () => {
      setEditingLimitsFor(null);
      queryClient.invalidateQueries({ queryKey: ['externalCards'] });
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || err.message);
    },
  });

  // Doładowanie karty
  const topupMutation = useMutation({
    mutationFn: async ({ cardToken, amount }: { cardToken: string; amount: number }) => {
      await axiosClient.post(`/cards/${cardToken}/topup`, { amount });
    },
    onSuccess: () => {
      setTopupFor(null);
      setTopupAmount(50);
      queryClient.invalidateQueries({ queryKey: ['externalCards'] });
      // Odśwież konta jeśli są wyświetlane gdzieś indziej
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || err.message);
    },
  });

  // Aktywacja karty
  const activateMutation = useMutation({
    mutationFn: async (cardToken: string) => {
      await axiosClient.post(`/cards/${cardToken}/activate`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['externalCards'] });
    },
    onError: (err: any) => {
      setError(err.response?.data?.error || err.message);
    },
  });

  const handleIssueCard = () => {
    setNewCardData(null);
    setError(null);
    const cardToIssue = isJunior ? 'PREPAID' : selectedType;
    issueMutation.mutate(cardToIssue);
  };

  const handleBlockCard = (cardToken: string) => {
    if (window.confirm('Czy na pewno chcesz zablokować tę kartę?')) {
      statusMutation.mutate({ cardToken, newStatus: 'BLOCKED', reason: 'Zastrzeżona przez użytkownika' });
    }
  };

  const handleUnblockCard = (cardToken: string) => {
    statusMutation.mutate({ cardToken, newStatus: 'ACTIVE' });
  };

  const startEditingLimits = (card: Card) => {
    setEditingLimitsFor(card.cardToken);
    setLimitValues({
      dailyLimit: card.dailyLimit || 0,
      monthlyLimit: card.monthlyLimit || 0,
      dailyTxnLimit: card.dailyTxnLimit || 0,
      monthlyTxnLimit: card.monthlyTxnLimit || 0,
    });
  };

  const saveLimits = (cardToken: string) => {
    limitsMutation.mutate({ cardToken, limits: limitValues });
  };

  const handleTopup = (cardToken: string) => {
    topupMutation.mutate({ cardToken, amount: topupAmount });
  };

  const getStatusBadge = (status: string) => {
    const badge = STATUS_BADGES[status] || { label: status, className: 'statusDefault' };
    return (
      <span className={`${styles.statusBadge} ${styles[badge.className] || ''}`}>
        {badge.label}
      </span>
    );
  };

  return (
    <div className={styles.cardsContainer}>
      <div className={styles.header}>
        <h1 className={styles.pageTitle}>
          <CreditCard size={24} /> Karty Płatnicze
        </h1>
        <p className={styles.subtitle}>
          Zarządzaj kartami płatniczymi wydanymi przez Payment Gateway
        </p>
      </div>

      <div className={styles.issueSection}>
        <button
          className={styles.issueButton}
          onClick={() => { setShowIssueForm(!showIssueForm); setNewCardData(null); setError(null); }}
        >
          <Plus size={18} /> {showIssueForm ? 'Anuluj' : 'Wydaj nową kartę'}
        </button>
        <button className={styles.refreshButton} onClick={() => refetch()}>
          <RefreshCw size={16} /> Odśwież
        </button>
      </div>

      {showIssueForm && (
        <div className={`glass-panel ${styles.issueForm}`}>
          <h3>Wybierz typ karty</h3>
          <div className={styles.cardTypeGrid}>
            {CARD_TYPES.filter(type => !isJunior || type.value === 'PREPAID').map((type) => (
              <button
                key={type.value}
                className={`${styles.cardTypeOption} ${(!isJunior ? selectedType : 'PREPAID') === type.value ? styles.cardTypeSelected : ''}`}
                onClick={() => setSelectedType(type.value)}
              >
                <strong>{type.label}</strong>
                <small>{type.desc}</small>
              </button>
            ))}
          </div>

          <button
            className={styles.confirmIssueButton}
            onClick={handleIssueCard}
            disabled={issueMutation.isPending}
          >
            {issueMutation.isPending ? 'Wydawanie karty...' : `Wydaj kartę ${CARD_TYPES.find(t => t.value === (!isJunior ? selectedType : 'PREPAID'))?.label}`}
          </button>
        </div>
      )}

      {error && (
        <div className={styles.errorBox}>
          <AlertCircle size={16} /> {error}
        </div>
      )}

      {newCardData && (
        <div className={`glass-panel ${styles.newCardResult}`}>
          <div className={styles.resultHeader}>
            <CheckCircle size={20} className={styles.resultIcon} />
            <h3>Karta wydana pomyślnie!</h3>
          </div>
          <div className={styles.cardCredentials}>
            <div className={styles.credentialRow}>
              <span className={styles.credentialLabel}>Numer karty (PAN):</span>
              <span className={styles.credentialValue}>{newCardData.fullPan}</span>
            </div>
            <div className={styles.credentialRow}>
              <span className={styles.credentialLabel}>CVV:</span>
              <span className={styles.credentialValue}>{newCardData.cvv}</span>
            </div>
            <div className={styles.credentialRow}>
              <span className={styles.credentialLabel}>Data ważności:</span>
              <span className={styles.credentialValue}>
                {String(newCardData.expiryMonth).padStart(2, '0')}/{newCardData.expiryYear}
              </span>
            </div>
            <div className={styles.credentialRow}>
              <span className={styles.credentialLabel}>Maskowany PAN:</span>
              <span className={styles.credentialValue}>{newCardData.maskedPan}</span>
            </div>
            <div className={styles.credentialRow}>
              <span className={styles.credentialLabel}>Token:</span>
              <span className={styles.credentialValue}>{newCardData.cardToken}</span>
            </div>
          </div>
          <p className={styles.warningMessage}>
            ⚠️ {newCardData.message || 'Zapisz pełny numer PAN i CVV - nie będą już więcej wyświetlone!'}
          </p>
        </div>
      )}

      <div className={`glass-panel ${styles.cardsList}`}>
        <h3 className={styles.sectionTitle}>Twoje karty</h3>

        {isLoading ? (
          <div className={styles.loadingState}>
            <Clock size={24} /> Wczytywanie kart...
          </div>
        ) : cards && cards.length > 0 ? (
          <div className={styles.cardsGrid}>
            {cards.map((card) => (
              <div key={card.cardToken} className={`glass-panel ${styles.cardItem}`}>
                <div className={styles.cardHeader}>
                  <div className={styles.cardTypeIcon}>
                    <CreditCard size={20} />
                  </div>
                  <div className={styles.cardMeta}>
                    <span className={styles.cardMaskedPan}>{card.maskedPan}</span>
                    <span className={styles.cardType}>{card.cardType}</span>
                  </div>
                  {getStatusBadge(card.status)}
                </div>

                  <div className={styles.cardDetails}>
                    {card.cardType === 'PREPAID' && (
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Saldo:</span>
                        <span className={styles.detailValue}>
                          {card.balance?.toFixed(2)} {card.bankId?.startsWith('PL') ? 'PLN' : 'EUR'}
                        </span>
                      </div>
                    )}
                  
                    {editingLimitsFor === card.cardToken ? (
                    <div className={styles.limitsEditForm}>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Limit dzienny ({card.bankId?.startsWith('PL') ? 'PLN' : 'EUR'}):</span>
                        <input type="number" value={limitValues.dailyLimit} onChange={e => setLimitValues({...limitValues, dailyLimit: Number(e.target.value)})} className={styles.limitInput} />
                      </div>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Limit miesięczny ({card.bankId?.startsWith('PL') ? 'PLN' : 'EUR'}):</span>
                        <input type="number" value={limitValues.monthlyLimit} onChange={e => setLimitValues({...limitValues, monthlyLimit: Number(e.target.value)})} className={styles.limitInput} />
                      </div>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Limit dzienny (ilość):</span>
                        <input type="number" value={limitValues.dailyTxnLimit} onChange={e => setLimitValues({...limitValues, dailyTxnLimit: Number(e.target.value)})} className={styles.limitInput} />
                      </div>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Limit miesięczny (ilość):</span>
                        <input type="number" value={limitValues.monthlyTxnLimit} onChange={e => setLimitValues({...limitValues, monthlyTxnLimit: Number(e.target.value)})} className={styles.limitInput} />
                      </div>
                      <div className={styles.editActionButtons}>
                        <button onClick={() => saveLimits(card.cardToken)} disabled={limitsMutation.isPending} className={styles.saveButton}>
                          {limitsMutation.isPending ? 'Zapisywanie...' : 'Zapisz'}
                        </button>
                        <button onClick={() => setEditingLimitsFor(null)} className={styles.cancelButton}>Anuluj</button>
                      </div>
                    </div>
                  ) : topupFor === card.cardToken ? (
                    <div className={styles.limitsEditForm}>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Kwota ({card.bankId?.startsWith('PL') ? 'PLN' : 'EUR'}):</span>
                        <input type="number" min="1" value={topupAmount} onChange={e => setTopupAmount(Number(e.target.value))} className={styles.limitInput} />
                      </div>
                      <div className={styles.editActionButtons}>
                        <button onClick={() => handleTopup(card.cardToken)} disabled={topupMutation.isPending} className={styles.saveButton}>
                          {topupMutation.isPending ? 'Ładowanie...' : 'Zasil'}
                        </button>
                        <button onClick={() => setTopupFor(null)} className={styles.cancelButton}>Anuluj</button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Limit kwotowy:</span>
                        <span className={styles.detailValue}>{card.dailyLimit?.toFixed(2)} / {card.monthlyLimit?.toFixed(2)}</span>
                      </div>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>Limit transakcji:</span>
                        <span className={styles.detailValue}>{card.dailyTxnLimit || 0} / {card.monthlyTxnLimit || 0}</span>
                      </div>
                    </>
                  )}
                </div>

                <div className={styles.cardActions}>
                  {card.status === 'ACTIVE' && editingLimitsFor !== card.cardToken && (
                    <button
                      className={styles.blockButton}
                      onClick={() => handleBlockCard(card.cardToken)}
                      disabled={statusMutation.isPending}
                      style={{ background: 'transparent', border: '1px solid rgba(239, 68, 68, 0.5)', color: '#ef4444' }}
                    >
                      <Lock size={14} /> Zablokuj
                    </button>
                  )}
                  {card.status === 'ACTIVE' && editingLimitsFor !== card.cardToken && topupFor !== card.cardToken && (
                    <button
                      className={styles.unblockButton}
                      onClick={() => startEditingLimits(card)}
                      style={{ background: 'rgba(255,255,255,0.1)', border: 'none', color: 'white' }}
                    >
                      <Settings size={14} /> Limity
                    </button>
                  )}
                  {card.status === 'ACTIVE' && card.cardType === 'PREPAID' && editingLimitsFor !== card.cardToken && topupFor !== card.cardToken && (
                    <button
                      className={styles.unblockButton}
                      onClick={() => setTopupFor(card.cardToken)}
                      style={{ background: 'rgba(16, 185, 129, 0.2)', border: '1px solid rgba(16, 185, 129, 0.5)', color: '#10b981' }}
                    >
                      <Plus size={14} /> Doładuj
                    </button>
                  )}
                  {card.status === 'BLOCKED' && (
                    <button
                      className={styles.unblockButton}
                      onClick={() => handleUnblockCard(card.cardToken)}
                      disabled={statusMutation.isPending}
                    >
                      <Unlock size={14} /> Odblokuj
                    </button>
                  )}
                  {card.status === 'SHIPPED' && (
                    <button
                      className={styles.unblockButton}
                      onClick={() => activateMutation.mutate(card.cardToken)}
                      disabled={activateMutation.isPending}
                      style={{ background: 'rgba(59, 130, 246, 0.2)', border: '1px solid rgba(59, 130, 246, 0.5)', color: '#3b82f6' }}
                    >
                      <CheckCircle size={14} /> {activateMutation.isPending ? 'Aktywacja...' : 'Aktywuj'}
                    </button>
                  )}
                  {card.status === 'REQUESTED' && (
                    <span className={styles.pendingInfo}>
                      <Clock size={14} /> W trakcie przetwarzania
                    </span>
                  )}
                  {card.status === 'PRODUCING' && (
                    <span className={styles.pendingInfo}>
                      <Clock size={14} /> W produkcji
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className={styles.emptyState}>
            <CreditCard size={48} className={styles.emptyIcon} />
            <p>Brak kart. Wydaj swoją pierwszą kartę!</p>
          </div>
        )}
      </div>
    </div>
  );
};

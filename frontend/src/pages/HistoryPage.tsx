import React, { useEffect, useMemo, useState } from 'react';
import {
  ArrowDownLeft,
  ArrowUpRight,
  Clock,
  Building2,
  ShieldCheck,
  Zap,
  Receipt,
  Filter,
  XCircle,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Smartphone,
  CreditCard,
  ChevronDown
} from 'lucide-react';
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

type SortOption = 'date_desc' | 'date_asc' | 'amount_desc' | 'amount_asc';
type DirectionFilter = 'ALL' | 'INCOME' | 'EXPENSE';
type StatusFilter = 'ALL' | 'COMPLETED' | 'PROCESSING' | 'PENDING' | 'REJECTED_FAILED';
type TypeFilter = 'ALL' | 'INTERNAL' | 'SEPA_SCT' | 'SEPA_INSTANT' | 'RTGS_TARGET2' | 'BLIK' | 'CARD_PAYMENT';

// ─────────────── Helpers ───────────────

const getFeeValue = (type: string): number => {
  switch (type) {
    case 'SEPA_INSTANT': return 0.50;
    case 'RTGS_TARGET2': return 5.00;
    default: return 0.00;
  }
};

const getTypeLabelAndIcon = (type: string) => {
  switch (type) {
    case 'INTERNAL':
      return { label: 'Wewnętrzny', icon: <Building2 size={14} color="var(--accent-gold)" /> };
    case 'SEPA_SCT':
      return { label: 'SEPA', icon: <Clock size={14} color="var(--accent-blue)" /> };
    case 'SEPA_INSTANT':
      return { label: 'SEPA Express', icon: <Zap size={14} color="var(--success-color)" /> };
    case 'RTGS_TARGET2':
      return { label: 'SWIFT/TARGET2', icon: <ShieldCheck size={14} color="#e74c3c" /> };
    case 'BLIK':
      return { label: 'BLIK', icon: <Smartphone size={14} color="#8b5cf6" /> };
    case 'CARD_PAYMENT':
      return { label: 'Karta', icon: <CreditCard size={14} color="#ec4899" /> };
    default:
      return { label: type, icon: <Receipt size={14} color="var(--text-secondary)" /> };
  }
};

const getStatusInfo = (status: string) => {
  switch (status) {
    case 'COMPLETED':
      return {
        label: 'Zrealizowano',
        bg: 'rgba(46,204,113,0.15)',
        color: 'var(--success-color)',
        icon: <CheckCircle2 size={12} />
      };
    case 'PROCESSING':
      return {
        label: 'Przetwarzanie',
        bg: 'rgba(0,168,255,0.15)',
        color: 'var(--accent-blue)',
        icon: <Loader2 size={12} />
      };
    case 'PENDING':
      return {
        label: 'Oczekuje na zatwierdzenie',
        bg: 'rgba(241,196,15,0.15)',
        color: '#f1c40f',
        icon: <AlertCircle size={12} />
      };
    case 'REJECTED':
      return {
        label: 'Odrzucono',
        bg: 'rgba(231,76,60,0.15)',
        color: '#e74c3c',
        icon: <XCircle size={12} />
      };
    case 'FAILED':
      return {
        label: 'Błąd',
        bg: 'rgba(231,76,60,0.15)',
        color: '#e74c3c',
        icon: <XCircle size={12} />
      };
    default:
      return {
        label: status,
        bg: 'rgba(255,255,255,0.1)',
        color: 'var(--text-secondary)',
        icon: null
      };
  }
};

const formatDateGroup = (dateStr: string): string => {
  const date = new Date(dateStr);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  if (date.toDateString() === today.toDateString()) return 'Dzisiaj';
  if (date.toDateString() === yesterday.toDateString()) return 'Wczoraj';
  return date.toLocaleDateString('pl-PL', { day: '2-digit', month: 'long', year: 'numeric' });
};

// ─────────────── Component ───────────────

export const HistoryPage: React.FC = () => {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [myIban, setMyIban] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters & sorting
  const [sortBy, setSortBy] = useState<SortOption>('date_desc');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');
  const [dirFilter, setDirFilter] = useState<DirectionFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
  const [showFilters, setShowFilters] = useState(true);

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const accountsRes = await axiosClient.get('/accounts');
        if (accountsRes.data && accountsRes.data.length > 0) {
          const mainIban = accountsRes.data[0].iban;
          setMyIban(mainIban);
          const txRes = await axiosClient.get(`/transfers?iban=${mainIban}`);
          setTransactions(txRes.data);
        }
      } catch (err) {
        console.error('Błąd pobierania historii', err);
        setError('Nie udało się pobrać historii transakcji.');
      } finally {
        setIsLoading(false);
      }
    };
    fetchHistory();
  }, []);

  // Active filters count (for badge)
  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (dirFilter !== 'ALL') count++;
    if (statusFilter !== 'ALL') count++;
    if (typeFilter !== 'ALL') count++;
    if (startDate) count++;
    if (endDate) count++;
    return count;
  }, [dirFilter, statusFilter, typeFilter, startDate, endDate]);

  // Apply filters and sorting
  const processedTransactions = useMemo(() => {
    let result = [...transactions];

    // Direction filter
    if (dirFilter === 'INCOME') result = result.filter(tx => tx.receiverIban === myIban);
    if (dirFilter === 'EXPENSE') result = result.filter(tx => tx.senderIban === myIban);

    // Status filter
    if (statusFilter === 'COMPLETED') result = result.filter(tx => tx.status === 'COMPLETED');
    if (statusFilter === 'PROCESSING') result = result.filter(tx => tx.status === 'PROCESSING');
    if (statusFilter === 'PENDING') result = result.filter(tx => tx.status === 'PENDING');
    if (statusFilter === 'REJECTED_FAILED') result = result.filter(tx => tx.status === 'REJECTED' || tx.status === 'FAILED');

    // Type filter (BLIK/CARD_PAYMENT are placeholders — will return empty for now)
    if (typeFilter !== 'ALL') result = result.filter(tx => tx.transactionType === typeFilter);

    // Date range filter
    if (startDate) {
      const start = new Date(startDate);
      start.setHours(0, 0, 0, 0);
      result = result.filter(tx => new Date(tx.requestedAt) >= start);
    }
    if (endDate) {
      const end = new Date(endDate);
      end.setHours(23, 59, 59, 999);
      result = result.filter(tx => new Date(tx.requestedAt) <= end);
    }

    // Sorting
    result.sort((a, b) => {
      switch (sortBy) {
        case 'date_desc': return new Date(b.requestedAt).getTime() - new Date(a.requestedAt).getTime();
        case 'date_asc': return new Date(a.requestedAt).getTime() - new Date(b.requestedAt).getTime();
        case 'amount_desc': return b.amount - a.amount;
        case 'amount_asc': return a.amount - b.amount;
        default: return 0;
      }
    });

    return result;
  }, [transactions, myIban, dirFilter, statusFilter, typeFilter, sortBy, startDate, endDate]);

  // Group by date (only when sorting by date)
  const groupedTransactions = useMemo(() => {
    if (sortBy !== 'date_desc' && sortBy !== 'date_asc') {
      return null; // No grouping when sorted by amount
    }
    const groups: { label: string; transactions: Transaction[] }[] = [];
    const seen: Record<string, number> = {};
    processedTransactions.forEach(tx => {
      const date = new Date(tx.requestedAt);
      const key = date.toDateString();
      const label = formatDateGroup(tx.requestedAt);
      if (seen[key] === undefined) {
        seen[key] = groups.length;
        groups.push({ label, transactions: [tx] });
      } else {
        groups[seen[key]].transactions.push(tx);
      }
    });
    return groups;
  }, [processedTransactions, sortBy]);

  // ─── Sub-components ───

  const renderTransaction = (tx: Transaction, idx: number, arr: Transaction[]) => {
    const isIncome = tx.receiverIban === myIban;
    const isRejected = tx.status === 'REJECTED' || tx.status === 'FAILED';
    const isPending = tx.status === 'PENDING';
    const dateObj = new Date(tx.requestedAt);
    const fee = getFeeValue(tx.transactionType);
    const statusInfo = getStatusInfo(tx.status);
    const { label: typeLabel, icon: typeIcon } = getTypeLabelAndIcon(tx.transactionType);

    return (
      <div
        key={tx.id}
        style={{
          padding: '18px 20px',
          borderBottom: idx === arr.length - 1 ? 'none' : '1px solid var(--glass-border)',
          display: 'flex',
          alignItems: 'center',
          gap: '14px',
          transition: 'background 0.2s ease',
          opacity: isRejected ? 0.7 : 1,
          cursor: 'default',
        }}
        onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.025)'}
        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
      >
        {/* Icon */}
        <div style={{
          width: '46px', height: '46px', borderRadius: '50%', flexShrink: 0,
          background: isRejected
            ? 'rgba(100,100,100,0.12)'
            : isPending
              ? 'rgba(241,196,15,0.12)'
              : isIncome
                ? 'rgba(46,204,113,0.12)'
                : 'rgba(231,76,60,0.12)',
          display: 'flex', alignItems: 'center', justifyContent: 'center'
        }}>
          {isRejected
            ? <XCircle size={22} color="var(--text-secondary)" />
            : isPending
              ? <AlertCircle size={22} color="#f1c40f" />
              : isIncome
                ? <ArrowDownLeft size={22} color="var(--success-color)" />
                : <ArrowUpRight size={22} color="#e74c3c" />
          }
        </div>

        {/* Details */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
            <strong style={{
              fontSize: '0.95rem',
              color: 'var(--text-primary)',
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '55%',
              textDecoration: isRejected ? 'line-through' : 'none'
            }}>
              {tx.title}
            </strong>
            <strong style={{
              fontSize: '1rem',
              color: isRejected
                ? 'var(--text-secondary)'
                : isIncome
                  ? 'var(--success-color)'
                  : '#e74c3c',
              whiteSpace: 'nowrap',
              textDecoration: isRejected ? 'line-through' : 'none'
            }}>
              {isIncome ? '+' : '-'}{tx.amount.toFixed(2)} {tx.currency}
            </strong>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '6px', flexWrap: 'wrap', gap: '6px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: 'var(--text-secondary)', fontSize: '0.8rem', flexWrap: 'wrap' }}>
              <span style={{ color: 'rgba(255,255,255,0.55)' }}>
                {isIncome ? 'Od: ' : 'Do: '}
                <strong style={{ color: 'rgba(255,255,255,0.75)' }}>
                  {isIncome ? (tx.senderName || tx.senderIban) : (tx.receiverName || tx.receiverIban)}
                </strong>
              </span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                {typeIcon}
                <span style={{ fontSize: '0.75rem' }}>{typeLabel}</span>
              </span>
              <span>{dateObj.toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })}</span>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
              {/* Status badge */}
              <span style={{
                display: 'inline-flex', alignItems: 'center', gap: '4px',
                background: statusInfo.bg, color: statusInfo.color,
                padding: '3px 8px', borderRadius: '6px', fontSize: '0.72rem', fontWeight: 600
              }}>
                {statusInfo.icon} {statusInfo.label}
              </span>
              {/* Fee info */}
              {!isIncome && fee > 0 && (
                <span style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
                  Prowizja: {fee.toFixed(2)} EUR
                </span>
              )}
            </div>
          </div>
        </div>
      </div>
    );
  };

  const renderEmptyMessage = () => (
    <div style={{ padding: '48px 20px', textAlign: 'center', color: 'var(--text-secondary)' }}>
      <Receipt size={40} style={{ opacity: 0.2, marginBottom: '12px' }} />
      <p style={{ margin: 0, fontSize: '0.9rem' }}>Brak transakcji spełniających kryteria filtrowania.</p>
    </div>
  );

  // ─── Button style helper ───
  const filterBtn = (active: boolean, color?: string): React.CSSProperties => ({
    padding: '7px 14px',
    borderRadius: '8px',
    border: `1px solid ${active ? (color || 'rgba(255,255,255,0.4)') : 'var(--glass-border)'}`,
    background: active ? `${color ? color + '22' : 'rgba(255,255,255,0.08)'}` : 'transparent',
    color: active ? (color || 'var(--text-primary)') : 'var(--text-secondary)',
    cursor: 'pointer',
    fontSize: '0.82rem',
    fontFamily: 'inherit',
    fontWeight: active ? 600 : 400,
    transition: 'all 0.15s ease',
    whiteSpace: 'nowrap' as const,
  });

  if (isLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '60vh', color: 'var(--text-secondary)', gap: '12px' }}>
        <Loader2 size={24} style={{ animation: 'spin 1s linear infinite' }} />
        Ładowanie historii...
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center', color: '#e74c3c' }}>
        {error}
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: '2rem 1rem' }}>

      {/* ── Nagłówek ── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '28px', flexWrap: 'wrap', gap: '12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div style={{ width: '48px', height: '48px', borderRadius: '14px', background: 'rgba(59,130,246,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Clock size={24} color="var(--accent-blue)" />
          </div>
          <div>
            <h1 style={{ fontSize: '1.7rem', color: 'var(--text-primary)', margin: 0, fontWeight: 700 }}>Historia Transakcji</h1>
            <p style={{ color: 'var(--text-secondary)', margin: 0, fontSize: '0.85rem' }}>
              {processedTransactions.length} z {transactions.length} operacji
            </p>
          </div>
        </div>

        {/* Toggle filtrów */}
        <button
          onClick={() => setShowFilters(f => !f)}
          style={{
            display: 'flex', alignItems: 'center', gap: '8px',
            padding: '10px 16px', borderRadius: '10px',
            border: `1px solid ${activeFilterCount > 0 ? 'var(--accent-blue)' : 'var(--glass-border)'}`,
            background: activeFilterCount > 0 ? 'rgba(59,130,246,0.12)' : 'rgba(255,255,255,0.04)',
            color: activeFilterCount > 0 ? 'var(--accent-blue)' : 'var(--text-secondary)',
            cursor: 'pointer', fontFamily: 'inherit', fontSize: '0.85rem', fontWeight: 500
          }}
        >
          <Filter size={16} />
          Filtry
          {activeFilterCount > 0 && (
            <span style={{ background: 'var(--accent-blue)', color: 'white', borderRadius: '50%', width: '18px', height: '18px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.7rem', fontWeight: 700 }}>
              {activeFilterCount}
            </span>
          )}
          <ChevronDown size={14} style={{ transform: showFilters ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }} />
        </button>
      </div>

      {/* ── Panel filtrów (rozwijany) ── */}
      {showFilters && (
        <div className="glass-panel" style={{ padding: '20px', marginBottom: '20px', display: 'flex', flexDirection: 'column', gap: '16px', animation: 'fadeInDown 0.2s ease' }}>

          {/* Sortowanie */}
            <div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: '8px' }}>Zakres dat</div>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                <input type="date" value={startDate} onChange={e=>setStartDate(e.target.value)} style={filterBtn(startDate!=='' )} />
                <input type="date" value={endDate} onChange={e=>setEndDate(e.target.value)} style={filterBtn(endDate!=='' )} />
                {(startDate||endDate) && (
                  <button onClick={()=>{setStartDate('');setEndDate('');}} style={filterBtn(true,'#e74c3c')}>Reset dat</button>
                )}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <button style={filterBtn(sortBy === 'date_desc')} onClick={()=>setSortBy('date_desc')}>📅 Najnowsze</button>
              <button style={filterBtn(sortBy === 'date_asc')} onClick={()=>setSortBy('date_asc')}>📅 Najstarsze</button>
              <button style={filterBtn(sortBy === 'amount_desc')} onClick={()=>setSortBy('amount_desc')}>💶 Kwota malejąco</button>
              <button style={filterBtn(sortBy === 'amount_asc')} onClick={()=>setSortBy('amount_asc')}>💶 Kwota rosnąco</button>
            </div>
          

          {/* Kierunek */}
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: '8px' }}>
              Kierunek
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <button style={filterBtn(dirFilter === 'ALL')} onClick={() => setDirFilter('ALL')}>Wszystkie</button>
              <button style={filterBtn(dirFilter === 'INCOME', 'var(--success-color)')} onClick={() => setDirFilter('INCOME')}>↓ Wpływy</button>
              <button style={filterBtn(dirFilter === 'EXPENSE', '#e74c3c')} onClick={() => setDirFilter('EXPENSE')}>↑ Wydatki</button>
            </div>
          </div>

          {/* Status */}
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: '8px' }}>
              Status
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <button style={filterBtn(statusFilter === 'ALL')} onClick={() => setStatusFilter('ALL')}>Wszystkie</button>
              <button style={filterBtn(statusFilter === 'COMPLETED', 'var(--success-color)')} onClick={() => setStatusFilter('COMPLETED')}>✓ Zrealizowane</button>
              <button style={filterBtn(statusFilter === 'PROCESSING', 'var(--accent-blue)')} onClick={() => setStatusFilter('PROCESSING')}>⟳ W trakcie</button>
              <button style={filterBtn(statusFilter === 'PENDING', '#f1c40f')} onClick={() => setStatusFilter('PENDING')}>⏳ Oczekujące (Junior)</button>
              <button style={filterBtn(statusFilter === 'REJECTED_FAILED', '#e74c3c')} onClick={() => setStatusFilter('REJECTED_FAILED')}>✕ Odrzucone / Błąd</button>
            </div>
          </div>

          {/* Typ płatności */}
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.8px', marginBottom: '8px' }}>
              Typ płatności
            </div>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <button style={filterBtn(typeFilter === 'ALL')} onClick={() => setTypeFilter('ALL')}>Wszystkie</button>
              <button style={filterBtn(typeFilter === 'INTERNAL', 'var(--accent-gold)')} onClick={() => setTypeFilter('INTERNAL')}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><Building2 size={12} />Wewnętrzny</span>
              </button>
              <button style={filterBtn(typeFilter === 'SEPA_SCT', 'var(--accent-blue)')} onClick={() => setTypeFilter('SEPA_SCT')}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><Clock size={12} />SEPA</span>
              </button>
              <button style={filterBtn(typeFilter === 'SEPA_INSTANT', 'var(--success-color)')} onClick={() => setTypeFilter('SEPA_INSTANT')}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><Zap size={12} />SEPA Express</span>
              </button>
              <button style={filterBtn(typeFilter === 'RTGS_TARGET2', '#e74c3c')} onClick={() => setTypeFilter('RTGS_TARGET2')}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><ShieldCheck size={12} />SWIFT/TARGET2</span>
              </button>
              <button style={filterBtn(typeFilter === 'BLIK', '#8b5cf6')} onClick={() => setTypeFilter('BLIK')}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><Smartphone size={12} />BLIK</span>
              </button>
              <button style={filterBtn(typeFilter === 'CARD_PAYMENT', '#ec4899')} onClick={() => setTypeFilter('CARD_PAYMENT')}>
                <span style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><CreditCard size={12} />Karta</span>
              </button>
            </div>
          </div>

          {/* Reset */}
          {activeFilterCount > 0 && (
            <button
              onClick={() => { setDirFilter('ALL'); setStatusFilter('ALL'); setTypeFilter('ALL'); }}
              style={{ alignSelf: 'flex-start', padding: '6px 12px', borderRadius: '8px', border: '1px solid rgba(231,76,60,0.3)', background: 'rgba(231,76,60,0.08)', color: '#e74c3c', cursor: 'pointer', fontFamily: 'inherit', fontSize: '0.8rem' }}
            >
              Wyczyść filtry
            </button>
          )}
        </div>
      )}

      {/* ── Lista transakcji ── */}
      {processedTransactions.length === 0 ? (
        renderEmptyMessage()
      ) : groupedTransactions ? (
        // Grouped by date
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {groupedTransactions.map(group => (
            <div key={group.label}>
              {/* Date group header */}
              <div style={{
                fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase',
                letterSpacing: '1px', color: 'var(--text-secondary)',
                padding: '0 4px 10px 4px',
                display: 'flex', alignItems: 'center', gap: '10px'
              }}>
                <span>{group.label}</span>
                <span style={{ flex: 1, height: '1px', background: 'var(--glass-border)' }} />
                <span style={{ opacity: 0.6, fontWeight: 400, textTransform: 'none', letterSpacing: 0 }}>
                  {group.transactions.length} {group.transactions.length === 1 ? 'operacja' : 'operacje'}
                </span>
              </div>

              <div className="glass-panel" style={{ padding: 0, overflow: 'hidden' }}>
                {group.transactions.map((tx, idx, arr) => renderTransaction(tx, idx, arr))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        // Flat list (sorted by amount — no grouping)
        <div className="glass-panel" style={{ padding: 0, overflow: 'hidden' }}>
          {processedTransactions.map((tx, idx, arr) => renderTransaction(tx, idx, arr))}
        </div>
      )}

    </div>
  );
};

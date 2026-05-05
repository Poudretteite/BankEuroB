import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import axiosClient from '../api/axiosClient';
import { useAuthStore } from '../store/useAuthStore';
import styles from './Dashboard.module.css';
import {
  CreditCard,
  ArrowUpRight,
  ArrowDownRight,
  Clock,
  Send,
  PieChart,
  MoreHorizontal,
  Wifi,
  TrendingUp,
  TrendingDown,
  Award,
  CreditCard as CreditCardIcon
} from 'lucide-react';
import {
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  Legend
} from 'recharts';
import { Link } from 'react-router-dom';
import { JuniorManagementPanel } from '../components/JuniorManagementPanel';

interface Account {
  id: string;
  iban: string;
  accountType: string;
  currency: string;
  balance: number;
  availableBalance: number;
}

interface Transaction {
  id: string;
  senderIban: string;
  receiverIban: string;
  title: string;
  amount: number;
  currency: string;
  requestedAt: string;
  status: string;
}

const generateAnalyticsData = (transactions: Transaction[] | undefined, mainIban: string | undefined) => {
  if (!transactions || !mainIban) return [];
  
  const data = [];
  const now = new Date();
  
  // Cofamy się o 5 miesięcy + bieżący = 6 miesięcy
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const monthName = d.toLocaleDateString('pl-PL', { month: 'short' });
    const year = d.getFullYear();
    const month = d.getMonth();
    
    let income = 0;
    let expense = 0;
    
    transactions.forEach(tx => {
      const txDate = new Date(tx.requestedAt);
      // Jeśli transakcja była w tym miesiącu i roku
      if (txDate.getFullYear() === year && txDate.getMonth() === month && tx.status !== 'FAILED') {
        if (tx.receiverIban === mainIban) {
          income += tx.amount;
        }
        if (tx.senderIban === mainIban) {
          expense += tx.amount;
        }
      }
    });
    
    data.push({
      name: monthName.charAt(0).toUpperCase() + monthName.slice(1),
      Wpływy: parseFloat(income.toFixed(2)),
      Wydatki: parseFloat(expense.toFixed(2)),
    });
  }
  return data;
};

export const DashboardPage: React.FC = () => {
  const { user } = useAuthStore();
  const [extCardStatus, setExtCardStatus] = useState<string | null>(null);
  
  const handleIssueCardClick = async () => {
    try {
      setExtCardStatus('Łączenie z Card Provider...');
      const response = await axiosClient.post('/cards/integrate');
      if (response.data?.response) {
        setExtCardStatus(`Sukces! Wydano kartę o tokenie: ${response.data.response}`);
      } else if (response.data?.error) {
        setExtCardStatus(`System Kart Niedostępny: ${response.data.error}`);
      }
    } catch (err: any) {
      setExtCardStatus(`Błąd połączenia do serwera kart: ${err.response?.data?.error || err.message}`);
    }
    
    // Znika powiadomienie po 10 sek
    setTimeout(() => setExtCardStatus(null), 10000);
  };

  const { data: accounts, isLoading: accountsLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: async () => {
      const response = await axiosClient.get<Account[]>('/accounts');
      return response.data;
    }
  });

  const mainAccount = accounts?.[0];

  const { data: transactions, isLoading: txLoading } = useQuery({
    queryKey: ['transactions', mainAccount?.iban],
    queryFn: async () => {
      if (!mainAccount?.iban) return [];
      const response = await axiosClient.get<Transaction[]>(`/transfers?iban=${mainAccount.iban}`);
      return response.data;
    },
    enabled: !!mainAccount?.iban
  });

  const chartData = useMemo(() => {
    return generateAnalyticsData(transactions, mainAccount?.iban);
  }, [transactions, mainAccount]);

  if (accountsLoading) return <div className={styles.loader}>Synchronizacja systemu...</div>;

  return (
    <div className={styles.dashboardContainer}>
      {/* Header Powitalny */}
      <div className={styles.welcomeSection}>
        <h1 className={styles.pageTitle}>Dzień dobry, {user?.firstName}</h1>
        <p className={styles.subtitle}>Oto podsumowanie Twoich finansów na dzień {new Date().toLocaleDateString('pl-PL')}</p>
      </div>

      <div className={styles.bentoGrid}>

        {/* LEWA KOLUMNA */}
        <div className={styles.mainColumn}>

          {/* Główny rachunek */}
          {mainAccount ? (
            <div className={`glass-panel ${styles.walletCard}`}>
              <div className={styles.walletHeader}>
                <div className={styles.walletTitle}>
                  <CreditCard size={20} className={styles.walletIcon} />
                  <span>Rachunek {mainAccount.currency}</span>
                </div>
                <div className={styles.walletType}>{mainAccount.accountType}</div>
              </div>

              <div className={styles.walletDetailsRow}>
                <div className={styles.walletBalance}>
                  <span className={styles.currencySymbol}>{mainAccount.currency === 'EUR' ? '€' : mainAccount.currency}</span>
                  <span className={styles.amount}>
                    {mainAccount.balance.toLocaleString('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                  </span>
                </div>
                <div className={styles.ibanBlock}>
                  <div className={styles.ibanLabel}>IBAN</div>
                  <div className={styles.ibanValue}>{mainAccount.iban.match(/.{1,4}/g)?.join(' ')}</div>
                </div>
              </div>
            </div>
          ) : (
            <div className={`glass-panel ${styles.emptyState}`}>Nie odnaleziono konta głównego</div>
          )}

          {/* Wykres aktywności */}
          <div className={`glass-panel ${styles.chartCard}`}>
            <div className={styles.cardHeader}>
              <h3 className={styles.cardTitle}><PieChart size={18} /> Analityka Salda (Faktyczna)</h3>
              <span className={styles.badge}>Ostatnie 6 miesięcy</span>
            </div>

            <div className={styles.chartWrapper}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} margin={{ top: 10, right: 0, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                  <XAxis dataKey="name" stroke="var(--text-secondary)" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="var(--text-secondary)" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: 'var(--bg-secondary)', border: '1px solid var(--glass-border)', borderRadius: '8px' }}
                    itemStyle={{ color: 'var(--text-primary)' }}
                    cursor={{fill: 'rgba(255,255,255,0.05)'}}
                  />
                  <Legend iconType="circle" wrapperStyle={{ fontSize: '12px', paddingTop: '10px' }} />
                  <Bar dataKey="Wpływy" fill="var(--success-color)" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="Wydatki" fill="#e74c3c" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <JuniorManagementPanel />
        </div>

        {/* PRAWA KOLUMNA */}
        <div className={styles.sideColumn}>

          {/* Szybkie Akcje z Integracją Modułu Kart */}
          <div className={`glass-panel ${styles.quickActionsCard}`}>
            <h3 className={styles.cardTitle}>Szybki dostęp</h3>
            
            {extCardStatus && (
              <div className={styles.integrationToast}>
                {extCardStatus}
              </div>
            )}
            
            <div className={styles.actionsGrid}>
              <Link to="/transfer" className={styles.actionBtn}>
                <div className={styles.actionIcon}><Send size={20} /></div>
                <span>Przelew</span>
              </Link>
              <button className={styles.actionBtn} onClick={handleIssueCardClick}>
                <div className={`${styles.actionIcon} ${styles.iconGold}`}><CreditCardIcon size={20} /></div>
                <span>Wydaj Kartę (gRPC)</span>
              </button>
              <button className={styles.actionBtn}>
                <div className={`${styles.actionIcon} ${styles.iconPurple}`}><MoreHorizontal size={20} /></div>
                <span>Więcej</span>
              </button>
            </div>
          </div>

          {/* SZYBKI WIDGET - Kursy Walut (WIZUALNY, BEZ LOGIKI) */}
          <div className={`glass-panel ${styles.ratesCard}`}>
            <h3 className={styles.cardTitle}>Kursy Walut (Live)</h3>
            <div className={styles.ratesList}>
              <div className={styles.rateItem}>
                <div className={styles.ratePairs}>
                  <span className={styles.rateFlag}>🇪🇺</span> EUR / PLN
                </div>
                <div className={styles.rateValueBox}>
                  <span className={styles.rateValue}>4.3215</span>
                  <div className={`${styles.rateTrend} ${styles.trendUp}`}><TrendingUp size={14} /> +0.02%</div>
                </div>
              </div>
              <div className={styles.rateItem}>
                <div className={styles.ratePairs}>
                  <span className={styles.rateFlag}>🇺🇸</span> EUR / USD
                </div>
                <div className={styles.rateValueBox}>
                  <span className={styles.rateValue}>1.0850</span>
                  <div className={`${styles.rateTrend} ${styles.trendDown}`}><TrendingDown size={14} /> -0.15%</div>
                </div>
              </div>
            </div>
          </div>

          {/* SZYBKI WIDGET - Gamifikacja Profilu (WIZUALNY) */}
          <div className={`glass-panel ${styles.progressCard}`}>
            <div className={styles.progressHeader}>
              <h3 className={styles.cardTitle}><Award size={18} className={styles.awardIcon} /> Dołącz do VIP</h3>
              <span className={styles.progressPct}>80%</span>
            </div>
            <p className={styles.progressText}>Zweryfikuj numer telefonu, by uzyskać najwyższy darmowy próg prowizyjny.</p>
            <div className={styles.progressBarBg}>
              <div className={styles.progressBarFill}></div>
            </div>
          </div>

          {/* Wirtualna Karta Płatnicza (Wizualny gadżet) */}
          <div className={styles.creditCardVisual}>
            <div className={styles.ccGlow}></div>
            <div className={styles.ccInner}>
              <div className={styles.ccTop}>
                <Wifi size={24} className={styles.ccWifi} />
                <span className={styles.ccLogo}>BankEuroB</span>
              </div>
              <div className={styles.ccMiddle}>
                <div className={styles.ccChip}></div>
              </div>
              <div className={styles.ccBottom}>
                <div className={styles.ccNumber}>•••• •••• •••• 9012</div>
                <div className={styles.ccNameInfo}>
                  <div className={styles.ccCardholder}>{user?.firstName} {user?.lastName}</div>
                  <div className={styles.ccExpires}>12/28</div>
                </div>
              </div>
            </div>
          </div>

          {/* Ostatnie transakcje */}
          <div className={`glass-panel ${styles.transactionsCard}`}>
            <div className={styles.cardHeader}>
              <h3 className={styles.cardTitle}>Ostatnia aktywność</h3>
              <Link to="/history" className={styles.seeAllLink}>Wszystkie</Link>
            </div>

            <div className={styles.transactionsList}>
              {txLoading ? (
                <div className={styles.loaderSmall}>Wczytywanie...</div>
              ) : transactions && transactions.length > 0 ? (
                transactions.slice(0, 4).map(tx => {
                  const isOutgoing = tx.senderIban === mainAccount?.iban;
                  return (
                    <div key={tx.id} className={styles.transactionItem}>
                      <div className={`${styles.txIcon} ${isOutgoing ? styles.txOutgoing : styles.txIncoming}`}>
                        {isOutgoing ? <ArrowUpRight size={16} /> : <ArrowDownRight size={16} />}
                      </div>

                      <div className={styles.txDetails}>
                        <div className={styles.txTitle}>{tx.title}</div>
                        <div className={styles.txDate}>
                          {new Date(tx.requestedAt).toLocaleString('pl-PL', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                        </div>
                      </div>

                      <div className={`${styles.txAmount} ${isOutgoing ? styles.textOut : styles.textIn}`}>
                        {isOutgoing ? '-' : '+'}{tx.amount.toFixed(2)} {tx.currency}
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className={styles.emptyActivity}>
                  <Clock size={24} className={styles.emptyIcon} />
                  <p>Brak historii operacji konta.</p>
                </div>
              )}
            </div>
          </div>

        </div>
      </div>
    </div>
  );
};

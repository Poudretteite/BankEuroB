import React, { useMemo } from 'react';
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
  ShieldCheck,
  MoreHorizontal,
  Wifi,
  TrendingUp,
  TrendingDown,
  Award
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import { Link } from 'react-router-dom';

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
  timestamp: string;
  status: string;
}

// Mockowe punkty do wykresu by pulpit był żywszy wizualnie
const generateMockChartData = (currentBalance: number) => {
  const data = [];
  let bal = currentBalance - 1500;
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i * 3);
    bal += Math.random() * 500 - 150;
    data.push({
      name: d.toLocaleDateString('pl-PL', { day: 'numeric', month: 'short' }),
      balance: Math.max(bal, 100).toFixed(2),
    });
  }
  return data;
};

export const DashboardPage: React.FC = () => {
  const { user } = useAuthStore();

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
    if (!mainAccount) return [];
    return generateMockChartData(mainAccount.balance);
  }, [mainAccount]);

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
              <h3 className={styles.cardTitle}><PieChart size={18} /> Analityka Salda (Projekcja)</h3>
              <span className={styles.badge}>Ostatnie 30 dni</span>
            </div>

            <div className={styles.chartWrapper}>
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 10, right: 0, left: -25, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorBalance" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="var(--accent-blue)" stopOpacity={0.4} />
                      <stop offset="95%" stopColor="var(--accent-blue)" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                  <XAxis dataKey="name" stroke="var(--text-secondary)" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis stroke="var(--text-secondary)" fontSize={12} tickLine={false} axisLine={false} />
                  <Tooltip
                    contentStyle={{ backgroundColor: 'var(--bg-secondary)', border: '1px solid var(--glass-border)', borderRadius: '8px' }}
                    itemStyle={{ color: 'var(--accent-gold)' }}
                  />
                  <Area type="monotone" dataKey="balance" stroke="var(--accent-blue)" strokeWidth={3} fillOpacity={1} fill="url(#colorBalance)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        {/* PRAWA KOLUMNA */}
        <div className={styles.sideColumn}>

          {/* Szybkie Akcje */}
          <div className={`glass-panel ${styles.quickActionsCard}`}>
            <h3 className={styles.cardTitle}>Szybki dostęp</h3>
            <div className={styles.actionsGrid}>
              <Link to="/transfer" className={styles.actionBtn}>
                <div className={styles.actionIcon}><Send size={20} /></div>
                <span>Przelew</span>
              </Link>
              <button className={styles.actionBtn}>
                <div className={`${styles.actionIcon} ${styles.iconGold}`}><ShieldCheck size={20} /></div>
                <span>Ubezpieczenie</span>
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
              <Link to="#" className={styles.seeAllLink}>Wszystkie</Link>
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
                          {Array.isArray(tx.timestamp)
                            ? `${tx.timestamp[0]}-${String(tx.timestamp[1]).padStart(2, '0')}-${String(tx.timestamp[2]).padStart(2, '0')} ${String(tx.timestamp[3]).padStart(2, '0')}:${String(tx.timestamp[4]).padStart(2, '0')}` // Fallback gdy Spring zrzuci tablicę
                            : new Date(tx.timestamp).toLocaleString('pl-PL', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
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

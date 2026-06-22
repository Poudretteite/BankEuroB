import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import {
  Smartphone,
  ShieldCheck,
  CheckCircle2,
  XCircle,
  Clock,
  RefreshCw,
  KeyRound,
  Loader2,
  Fingerprint,
  Bell,
  DollarSign,
  Building2,
  User as UserIcon,
  Settings,
  AlertTriangle
} from 'lucide-react';
import styles from './Blik.module.css';
import { useAuthStore } from '../store/useAuthStore';
import axiosClient from '../api/axiosClient';
import { useQuery } from '@tanstack/react-query';
import { BlikAliasRegister } from '../components/blik/BlikAliasRegister';
import { BlikP2PTransfer } from '../components/blik/BlikP2PTransfer';

// ─── Typy ─────────────────────────────────────────────────────────────────
interface PendingTransaction {
  id: string;
  klik_transaction_id: string;
  merchant_name: string;
  amount: number;
  currency: string;
  status: string;
  received_at: string;
  expires_at: string;
  seconds_left: number;
}

interface BlikConfirmResult {
  success: boolean;
  reference_number: string;
  amount: number;
  currency: string;
  merchant_name: string;
  message: string;
}

// ─── Pomocniczy hook: pokazuje cyfrę przez 2s, potem maskuje ──────────────
function usePinVisibility() {
  const [visibleUntil, setVisibleUntil] = useState<number[]>([]);
  const [, setTick] = useState(0);

  const revealDigit = (index: number) => {
    setVisibleUntil((prev) => {
      const next = [...prev];
      next[index] = Date.now() + 2000;
      return next;
    });
  };

  const isVisible = (index: number) => {
    return (visibleUntil[index] ?? 0) > Date.now();
  };

  useEffect(() => {
    const interval = setInterval(() => {
      setTick((prev) => prev + 1);
    }, 500);
    return () => clearInterval(interval);
  }, []);

  return { revealDigit, isVisible };
}

// ─── Komponent ─────────────────────────────────────────────────────────────
export const BlikPage: React.FC = () => {
  const navigate = useNavigate();
  const { getHasBlikPin, setHasBlikPin } = useAuthStore();

  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts'],
    queryFn: async () => {
      const res = await axiosClient.get('/accounts');
      return res.data;
    },
    staleTime: 30000,
  });

  const [step, setStep] = useState<'code' | 'pending' | 'confirm' | 'processing' | 'success' | 'error'>('code');
  const [blikCode, setBlikCode] = useState('');
  const [countdown, setCountdown] = useState(120);
  const [errorMessage, setErrorMessage] = useState('');
  const [txRef, setTxRef] = useState('');
  const [pin, setPin] = useState<string[]>(['', '', '', '']);
  const pinRefs = useRef<(HTMLInputElement | null)[]>([]);
  const [pendingTx, setPendingTx] = useState<PendingTransaction | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Widzialność cyfr PIN
  const { revealDigit: revealPinDigit, isVisible: isPinVisible } = usePinVisibility();

  // ── Stan dla PIN setup ─────────────────────────────────────────────────
  const [showPinSetup, setShowPinSetup] = useState(false);
  const [setupPin, setSetupPin] = useState<string[]>(['', '', '', '']);
  const [setupConfirmPin, setSetupConfirmPin] = useState<string[]>(['', '', '', '']);
  const [setupError, setSetupError] = useState('');
  const setupPinRefs = useRef<(HTMLInputElement | null)[]>([]);
  const setupConfirmRefs = useRef<(HTMLInputElement | null)[]>([]);
  const { revealDigit: revealSetupDigit, isVisible: isSetupVisible } = usePinVisibility();
  const { revealDigit: revealConfirmDigit, isVisible: isConfirmVisible } = usePinVisibility();

  // ── Ładowanie stanu PIN-u z backendu ───────────────────────────────────
  const [initialLoading, setInitialLoading] = useState(true);

  useEffect(() => {
    const checkBlikPinStatus = async () => {
      try {
        const response = await axiosClient.get('/customers/me');
        const hasPin = !!response.data.blikPin;
        setHasBlikPin(hasPin);

        if (!hasPin) {
          setShowPinSetup(true);
        }
      } catch (err) {
        console.error('Błąd sprawdzania statusu PIN-u', err);
        setShowPinSetup(true);
      } finally {
        setInitialLoading(false);
      }
    };
    checkBlikPinStatus();
  }, [setHasBlikPin]);

  // ── Generowanie kodu BLIK przez API ────────────────────────────────────
  const generateBlikCode = useCallback(async () => {
    try {
      const response = await axiosClient.post('/klik/codes/generate');
      const data = response.data;
      setBlikCode(data.code);
      // setCodeExpiresIn(data.expires_in);
      setCountdown(data.expires_in);
      setStep('code');
    } catch (err: any) {
      console.error('Błąd generowania kodu BLIK', err);
      setErrorMessage(err.response?.data?.error || 'Nie udało się wygenerować kodu BLIK');
      setStep('error');
    }
  }, []);

  // Generuj kod przy pierwszym renderze
  useEffect(() => {
    if (!initialLoading && getHasBlikPin() && !showPinSetup) {
      generateBlikCode();
    }
  }, [initialLoading, getHasBlikPin, showPinSetup, generateBlikCode]);

  // ── Odliczanie czasu kodu ──────────────────────────────────────────────
  useEffect(() => {
    if (step !== 'code' && step !== 'pending') return;
    if (countdown <= 0) {
      if (step === 'code') {
        generateBlikCode();
      }
      return;
    }
    const timer = setInterval(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [step, countdown, generateBlikCode]);

  // ── Polling oczekujących transakcji z backendu ─────────────────────────
  useEffect(() => {
    if (step !== 'code') {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
      return;
    }

    const pollPendingTransactions = async () => {
      try {
        const response = await axiosClient.get('/klik/pending-transactions');
        const transactions: PendingTransaction[] = response.data;

        if (transactions.length > 0) {
          const tx = transactions[0]; // weź pierwszą oczekującą
          setPendingTx(tx);
          setStep('pending');
          if (pollingRef.current) {
            clearInterval(pollingRef.current);
            pollingRef.current = null;
          }
        }
      } catch (err) {
        console.error('Błąd pollingu transakcji BLIK', err);
      }
    };

    // Polluj co 3 sekundy
    pollingRef.current = setInterval(pollPendingTransactions, 3000);

    // Od razu sprawdź przy starcie
    pollPendingTransactions();

    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [step]);

  // ── Obsługa PIN (4 cyfry) z widzialnością ──────────────────────────────
  const handlePinChange = (index: number, value: string) => {
    if (value && !/^\d$/.test(value)) return;
    const newPin = [...pin];
    newPin[index] = value;
    setPin(newPin);

    if (value) {
      revealPinDigit(index);
      if (index < 3) {
        pinRefs.current[index + 1]?.focus();
      }
    }
  };

  const handlePinKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace') {
      if (pin[index]) {
        const newPin = [...pin];
        newPin[index] = '';
        setPin(newPin);
      } else if (index > 0) {
        pinRefs.current[index - 1]?.focus();
      }
    }
  };

  // ── Autoryzacja PIN-em przez API ───────────────────────────────────────
  const confirmWithPin = async () => {
    const pinStr = pin.join('');
    if (pinStr.length !== 4 || !pendingTx) return;

    setStep('processing');
    setErrorMessage('');

    try {
      const response = await axiosClient.post('/klik/payments/authorize', null, {
        params: {
          klikTransactionId: pendingTx.klik_transaction_id,
          pin: pinStr,
        },
      });

      const result: BlikConfirmResult = response.data;

      if (result.success) {
        setTxRef(result.reference_number);
        setStep('success');
      } else {
        setErrorMessage(result.message || 'Transakcja odrzucona');
        setPin(['', '', '', '']);
        setStep('error');
      }
    } catch (err: any) {
      const message = err.response?.data?.message
        || err.response?.data?.error
        || 'Błąd autoryzacji. Spróbuj ponownie.';
      setErrorMessage(message);
      setPin(['', '', '', '']);
      setStep('error');
    }
  };

  // ── Odrzucenie transakcji przez API ────────────────────────────────────
  const rejectTransaction = async () => {
    if (!pendingTx) return;

    try {
      await axiosClient.post('/klik/payments/reject', null, {
        params: {
          klikTransactionId: pendingTx.klik_transaction_id,
        },
      });
    } catch (err) {
      console.error('Błąd odrzucania transakcji', err);
    }

    setPendingTx(null);
    generateBlikCode();
  };

  // ── Reset ──────────────────────────────────────────────────────────────
  const handleNewCode = () => {
    setPin(['', '', '', '']);
    setPendingTx(null);
    setErrorMessage('');
    setTxRef('');
    generateBlikCode();
  };


  // ── Obsługa PIN setup (4 cyfry) z widzialnością i backspace ────────────
  const handleSetupPinChange = (
    index: number,
    value: string,
    arr: string[],
    setter: React.Dispatch<React.SetStateAction<string[]>>,
    refs: React.MutableRefObject<(HTMLInputElement | null)[]>,
    reveal: (i: number) => void
  ) => {
    if (value && !/^\d$/.test(value)) return;
    const newArr = [...arr];
    newArr[index] = value;
    setter(newArr);
    if (value) {
      reveal(index);
      if (index < 3) {
        refs.current[index + 1]?.focus();
      }
    }
  };

  const handleSetupPinKeyDown = (
    index: number,
    e: React.KeyboardEvent,
    arr: string[],
    setter: React.Dispatch<React.SetStateAction<string[]>>,
    refs: React.MutableRefObject<(HTMLInputElement | null)[]>
  ) => {
    if (e.key === 'Backspace') {
      if (arr[index]) {
        const newArr = [...arr];
        newArr[index] = '';
        setter(newArr);
      } else if (index > 0) {
        refs.current[index - 1]?.focus();
      }
    }
  };

  const confirmPinSetup = async () => {
    setSetupError('');
    const pinStr = setupPin.join('');
    const confirmStr = setupConfirmPin.join('');

    if (pinStr.length !== 4 || confirmStr.length !== 4) {
      setSetupError('Wprowadź 4-cyfrowy PIN w obu polach.');
      return;
    }

    if (pinStr !== confirmStr) {
      setSetupError('Wprowadzone PIN-y nie są zgodne.');
      return;
    }

    // Zapisz PIN przez API
    try {
      await axiosClient.put('/customers/me/blik-pin', {
        newPin: pinStr,
      });

      setHasBlikPin(true);
      setShowPinSetup(false);
      setSetupPin(['', '', '', '']);
      setSetupConfirmPin(['', '', '', '']);
      generateBlikCode();
    } catch (err: any) {
      const message = err.response?.data?.message
        || err.response?.data?.error
        || 'Nie udało się zapisać PIN-u. Spróbuj ponownie.';
      setSetupError(message);
    }
  };

  // ── Formatowanie czasu ─────────────────────────────────────────────────
  const formatCountdown = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: Ładowanie
  // ═══════════════════════════════════════════════════════════════════════
  if (initialLoading) {
    return (
      <div className={styles.blikPage}>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '300px' }}>
          <Loader2 size={32} className={styles.waitingSpinner} />
        </div>
      </div>
    );
  }

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: Krok 1 – Kod BLIK (generowany przez bank)
  // ═══════════════════════════════════════════════════════════════════════
  const renderCodeScreen = () => (
    <div className={styles.blikForm}>
      <div className={styles.blikHeader}>
        <div className={styles.blikLogo}>
          <KeyRound size={32} className={styles.blikLogoIcon} />
          <span className={styles.blikLogoText}>BLIK</span>
        </div>
        <p className={styles.blikSubtitle}>
          Twój kod do płatności – wpisz go na stronie sklepu lub terminalu
        </p>
      </div>

      {/* Wyświetlanie kodu BLIK */}
      <div className={styles.codeDisplay}>
        {blikCode.split('').map((digit, i) => (
          <span key={i} className={styles.codeDigit}>{digit}</span>
        ))}
      </div>

      {/* Timer */}
      <div className={`${styles.codeTimer} ${countdown <= 30 ? styles.codeTimerWarning : ''}`}>
        <Clock size={16} />
        <span>
          Kod ważny przez: <strong>{formatCountdown(countdown)}</strong>
        </span>
        {countdown <= 30 && (
          <span className={styles.timerWarning}>Kod zaraz wygaśnie!</span>
        )}
      </div>

      {/* Instrukcja */}
      <div className={styles.instructions}>
        <div className={styles.instructionStep}>
          <div className={styles.instructionNumber}>1</div>
          <p>Wpisz powyższy kod na stronie sklepu lub terminalu płatniczym</p>
        </div>
        <div className={styles.instructionStep}>
          <div className={styles.instructionNumber}>2</div>
          <p>Potwierdź transakcję PIN-em w aplikacji BankEuroB</p>
        </div>
      </div>

      {/* Przyciski */}
      <div className={styles.codeActions}>
        <button className={styles.generateNewBtn} onClick={handleNewCode}>
          <RefreshCw size={16} />
          Generuj nowy kod
        </button>
      </div>

      {/* Oczekiwanie na transakcję */}
      <div className={styles.waitingIndicator}>
        <Loader2 size={16} className={styles.waitingSpinner} />
        <span>Oczekiwanie na transakcję...</span>
      </div>
    </div>
  );

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: Krok 2 – Otrzymano transakcję (powiadomienie)
  // ═══════════════════════════════════════════════════════════════════════
  const renderPendingScreen = () => (
    <div className={styles.blikForm}>
      <div className={styles.notificationBanner}>
        <Bell size={24} className={styles.notificationIcon} />
        <span>Otrzymano żądanie płatności BLIK</span>
      </div>

      <div className={styles.transactionDetails}>
        <div className={styles.txDetailRow}>
          <Building2 size={18} className={styles.txDetailIcon} />
          <div>
            <div className={styles.txDetailLabel}>Odbiorca</div>
          <div className={styles.txDetailValue}>{pendingTx?.merchant_name}</div>

          </div>
        </div>
        <div className={styles.txDetailRow}>
          <UserIcon size={18} className={styles.txDetailIcon} />
          <div>
            <div className={styles.txDetailLabel}>Tytuł</div>
            <div className={styles.txDetailValue}>Płatność BLIK</div>

          </div>
        </div>
        <div className={styles.txDetailRow}>
          <Clock size={18} className={styles.txDetailIcon} />
          <div>
            <div className={styles.txDetailLabel}>Czas</div>
            <div className={styles.txDetailValue}>{pendingTx?.received_at ? new Date(pendingTx.received_at).toLocaleTimeString('pl-PL') : ''}</div>

          </div>
        </div>
        <div className={styles.txDivider} />
        <div className={styles.txAmountRow}>
          <DollarSign size={22} className={styles.txAmountIcon} />
          <div>
            <div className={styles.txDetailLabel}>Kwota</div>
            <div className={styles.txAmountValue}>
              {pendingTx?.amount.toFixed(2)} <span className={styles.txCurrency}>PLN</span>
            </div>
          </div>
        </div>
      </div>

      {/* Timer kodu */}
      <div className={`${styles.codeTimer} ${countdown <= 30 ? styles.codeTimerWarning : ''}`}>
        <Clock size={16} />
        <span>
          Kod wygasa za: <strong>{formatCountdown(countdown)}</strong>
        </span>
      </div>

      {/* Przycisk do przejścia do PIN — czyścimy PIN przy wejściu */}
      <Button
        size="lg"
        className={styles.confirmPinBtn}
        onClick={() => {
          setPin(['', '', '', '']);
          setStep('confirm');
        }}
      >
        <Fingerprint size={20} />
        Potwierdź PIN-em
      </Button>

      <button className={styles.rejectBtn} onClick={rejectTransaction}>
        Odrzuć transakcję
      </button>
    </div>
  );

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: Krok 3 – Wprowadzenie PIN
  // ═══════════════════════════════════════════════════════════════════════
  const renderPinScreen = () => (
    <div className={styles.blikForm}>
      <div className={styles.pinHeader}>
        <Fingerprint size={40} className={styles.pinIcon} />
        <h2 className={styles.pinTitle}>Potwierdź transakcję</h2>
        <p className={styles.pinSubtitle}>
          Wprowadź PIN, aby zatwierdzić płatność BLIK
        </p>
      </div>

      {/* Podsumowanie transakcji */}
      <div className={styles.pinSummary}>
        <div className={styles.pinSummaryRow}>
          <span className={styles.pinSummaryLabel}>Odbiorca</span>
          <span className={styles.pinSummaryValue}>{pendingTx?.merchant_name}</span>

        </div>
        <div className={styles.pinSummaryRow}>
          <span className={styles.pinSummaryLabel}>Kwota</span>
          <span className={styles.pinSummaryAmount}>
            {pendingTx?.amount.toFixed(2)} PLN
          </span>
        </div>
      </div>

      {/* 4-cyfrowy PIN (cyfra widoczna przez 2s po wpisaniu) */}
      <div className={styles.pinSection}>
        <label className={styles.pinLabel}>Wprowadź PIN</label>
        <div className={styles.pinRow}>
          {pin.map((digit, index) => (
            <input
              key={index}
              ref={(el) => { pinRefs.current[index] = el; }}
              type={digit && isPinVisible(index) ? 'text' : 'password'}
              inputMode="numeric"
              maxLength={1}
              value={digit}
              onChange={(e) => handlePinChange(index, e.target.value)}
              onKeyDown={(e) => handlePinKeyDown(index, e)}
              className={`${styles.pinInput} ${digit ? styles.pinFilled : ''}`}
              autoFocus={index === 0}
            />
          ))}
        </div>
      </div>

      {/* Przycisk zatwierdzenia */}
      <Button
        size="lg"
        className={styles.confirmBtn}
        onClick={confirmWithPin}
        disabled={pin.join('').length !== 4}
      >
        <ShieldCheck size={18} />
        Zatwierdź płatność
      </Button>

      <button className={styles.backBtn} onClick={() => {
        setPin(['', '', '', '']);
        setStep('pending');
      }}>
        Anuluj
      </button>
    </div>
  );

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: Processing
  // ═══════════════════════════════════════════════════════════════════════
  const renderProcessing = () => (
    <div className={styles.statusScreen}>
      <div className={styles.statusSpinner}>
        <Loader2 size={48} className={styles.spinnerIcon} />
      </div>
      <h2 className={styles.statusTitle}>Przetwarzanie płatności BLIK</h2>
      <p className={styles.statusText}>
        Trwa autoryzacja transakcji...
      </p>
      <div className={styles.statusProgressBar}>
        <div className={styles.statusProgressFill} />
      </div>
    </div>
  );

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: Sukces
  // ═══════════════════════════════════════════════════════════════════════
  const renderSuccess = () => (
    <div className={styles.statusScreen}>
      <div className={styles.successIconWrapper}>
        <CheckCircle2 size={56} className={styles.successIcon} />
      </div>
      <h2 className={styles.statusTitle}>Płatność BLIK zrealizowana!</h2>
      <p className={styles.statusText}>
        Transakcja została autoryzowana i przetworzona pomyślnie.
      </p>

      <div className={styles.successDetails}>
        <div className={styles.successRow}>
          <span>Numer referencyjny</span>
          <strong>{txRef}</strong>
        </div>
        <div className={styles.successRow}>
          <span>Kwota</span>
          <strong>{pendingTx?.amount.toFixed(2)} PLN</strong>
        </div>
        <div className={styles.successRow}>
          <span>Odbiorca</span>
          <strong>{pendingTx?.merchant_name}</strong>

        </div>
      </div>

      <Button size="lg" className={styles.newPaymentBtn} onClick={handleNewCode}>
        Nowy kod BLIK
      </Button>
    </div>
  );

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: Błąd
  // ═══════════════════════════════════════════════════════════════════════
  const renderError = () => (
    <div className={styles.statusScreen}>
      <div className={styles.errorIconWrapper}>
        <XCircle size={56} className={styles.errorIcon} />
      </div>
      <h2 className={styles.statusTitle}>Transakcja odrzucona</h2>
      <p className={styles.statusText}>{errorMessage}</p>

      <div className={styles.errorActions}>
        <Button size="lg" className={styles.retryBtn} onClick={() => {
          setPin(['', '', '', '']);
          setStep('confirm');
        }}>
          <RefreshCw size={18} />
          Spróbuj ponownie
        </Button>
        <button className={styles.backBtn} onClick={handleNewCode}>
          Nowy kod BLIK
        </button>
      </div>
    </div>
  );

  // ═══════════════════════════════════════════════════════════════════════
  // RENDER: PIN Setup (gdy brak PIN-u)
  // ═══════════════════════════════════════════════════════════════════════
  const renderPinSetup = () => (
    <div className={styles.blikForm}>
      <div className={styles.pinHeader}>
        <AlertTriangle size={40} className={styles.pinIcon} style={{ color: 'var(--accent-orange)' }} />
        <h2 className={styles.pinTitle}>Wymagany kod PIN</h2>
        <p className={styles.pinSubtitle}>
          Aby korzystać z BLIK, musisz najpierw ustawić kod PIN.
          Możesz to zrobić tutaj lub w ustawieniach konta.
        </p>
      </div>

      {setupError && (
        <div style={{ background: 'rgba(231, 76, 60, 0.2)', border: '1px solid var(--error-color)', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem' }}>
          <AlertTriangle size={16} />
          {setupError}
        </div>
      )}

      {/* Nowy PIN */}
      <div className={styles.pinSection}>
        <label className={styles.pinLabel}>Wprowadź nowy PIN (4 cyfry)</label>
        <div className={styles.pinRow}>
          {setupPin.map((digit, index) => (
            <input
              key={index}
              ref={(el) => { setupPinRefs.current[index] = el; }}
              type={digit && isSetupVisible(index) ? 'text' : 'password'}
              inputMode="numeric"
              maxLength={1}
              value={digit}
              onChange={(e) => handleSetupPinChange(index, e.target.value, setupPin, setSetupPin, setupPinRefs, revealSetupDigit)}
              onKeyDown={(e) => handleSetupPinKeyDown(index, e, setupPin, setSetupPin, setupPinRefs)}
              className={`${styles.pinInput} ${digit ? styles.pinFilled : ''}`}
              autoFocus={index === 0}
            />
          ))}
        </div>
      </div>

      {/* Potwierdź PIN */}
      <div className={styles.pinSection}>
        <label className={styles.pinLabel}>Potwierdź nowy PIN</label>
        <div className={styles.pinRow}>
          {setupConfirmPin.map((digit, index) => (
            <input
              key={index}
              ref={(el) => { setupConfirmRefs.current[index] = el; }}
              type={digit && isConfirmVisible(index) ? 'text' : 'password'}
              inputMode="numeric"
              maxLength={1}
              value={digit}
              onChange={(e) => handleSetupPinChange(index, e.target.value, setupConfirmPin, setSetupConfirmPin, setupConfirmRefs, revealConfirmDigit)}
              onKeyDown={(e) => handleSetupPinKeyDown(index, e, setupConfirmPin, setSetupConfirmPin, setupConfirmRefs)}
              className={`${styles.pinInput} ${digit ? styles.pinFilled : ''}`}
            />
          ))}
        </div>
      </div>

      <Button
        size="lg"
        className={styles.confirmBtn}
        onClick={confirmPinSetup}
        disabled={setupPin.join('').length !== 4 || setupConfirmPin.join('').length !== 4}
      >
        <ShieldCheck size={18} />
        Ustaw PIN i kontynuuj
      </Button>

      <button className={styles.backBtn} onClick={() => navigate('/settings')}>
        <Settings size={16} /> Przejdź do ustawień
      </button>
    </div>
  );

  // ═══════════════════════════════════════════════════════════════════════
  // Główny render
  // ═══════════════════════════════════════════════════════════════════════
  return (
    <div className={styles.blikPage}>
      <h1 className={styles.pageTitle}>BLIK</h1>
      <p className={styles.pageSubtitle}>
        Płać szybko i bezpiecznie kodem BLIK
      </p>

      <div className={`glass-panel ${styles.blikCard}`}>
        {showPinSetup && renderPinSetup()}
        {!showPinSetup && step === 'code' && renderCodeScreen()}
        {!showPinSetup && step === 'pending' && renderPendingScreen()}
        {!showPinSetup && step === 'confirm' && renderPinScreen()}
        {!showPinSetup && step === 'processing' && renderProcessing()}
        {!showPinSetup && step === 'success' && renderSuccess()}
        {!showPinSetup && step === 'error' && renderError()}
      </div>

      {/* Informacje o BLIK – tylko na ekranie kodu */}
      {!showPinSetup && step === 'code' && (
        <div className={styles.blikInfo}>
          <div className={styles.infoItem}>
            <ShieldCheck size={18} className={styles.infoIcon} />
            <div>
              <strong>Bezpieczeństwo</strong>
              <p>Kod BLIK jest jednorazowy i ważny tylko 2 minuty. Każdą transakcję zatwierdzasz PIN-em.</p>
            </div>
          </div>
          <div className={styles.infoItem}>
            <Smartphone size={18} className={styles.infoIcon} />
            <div>
              <strong>Jak to działa?</strong>
              <p>Wygeneruj kod → wpisz go na stronie sklepu → potwierdź PIN-em w aplikacji BankEuroB</p>
            </div>
          </div>
        </div>
      )}

      {/* KLIK P2P - Przelew i Rejestracja (Zawsze widoczne pod BLIKIEM) */}
      <BlikP2PTransfer accounts={accounts} />
      <BlikAliasRegister accounts={accounts} />

    </div>
  );
};

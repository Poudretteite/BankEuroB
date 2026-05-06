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

// ─── Typy ─────────────────────────────────────────────────────────────────
interface PendingTransaction {
  id: string;
  merchantName: string;
  amount: number;
  title: string;
  timestamp: string;
}

// ─── Pomocniczy hook: pokazuje cyfrę przez 2s, potem maskuje ──────────────
function usePinVisibility() {
  const [visibleUntil, setVisibleUntil] = useState<number[]>([]);
  // Tick wymusza re-render co 500ms, aby przeterminowane cyfry się zamaskowały
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

  // Tick co 500ms – wymusza re-render, aby ukryć cyfry po upływie 2s
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

  const [step, setStep] = useState<'code' | 'pending' | 'confirm' | 'processing' | 'success' | 'error'>('code');
  const [blikCode, setBlikCode] = useState('');
  const [countdown, setCountdown] = useState(120);
  const [errorMessage, setErrorMessage] = useState('');
  const [txRef, setTxRef] = useState('');
  const [pin, setPin] = useState<string[]>(['', '', '', '']);
  const pinRefs = useRef<(HTMLInputElement | null)[]>([]);
  const [pendingTx, setPendingTx] = useState<PendingTransaction | null>(null);

  // Widzialność cyfr PIN – pokazuje cyfrę przez 2s po wpisaniu
  const { revealDigit: revealPinDigit, isVisible: isPinVisible } = usePinVisibility();

  // ── Stan dla PIN setup w BlikPage ──────────────────────────────────────
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
        // W razie błędu – pokaż setup na wszelki wypadek
        setShowPinSetup(true);
      } finally {
        setInitialLoading(false);
      }
    };
    checkBlikPinStatus();
  }, [setHasBlikPin]);

  // ── Generowanie kodu BLIK ──────────────────────────────────────────────
  const generateBlikCode = useCallback(() => {
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    setBlikCode(code);
    setCountdown(120);
    setStep('code');
  }, []);

  // Generuj kod przy pierwszym renderze (tylko jeśli PIN istnieje i nie ma setupu)
  useEffect(() => {
    if (!initialLoading && getHasBlikPin() && !showPinSetup) {
      generateBlikCode();
    }
  }, [initialLoading, getHasBlikPin, showPinSetup, generateBlikCode]);

  // ── Odliczanie 2 minut ─────────────────────────────────────────────────
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

  // ── Symulacja nadejścia transakcji (po wygenerowaniu kodu) ────────────
  useEffect(() => {
    if (step !== 'code' || !blikCode) return;

    const timer = setTimeout(() => {
      setPendingTx({
        id: 'TXN' + Date.now().toString().slice(-8),
        merchantName: 'Zakupy Online Sp. z o.o.',
        amount: parseFloat((Math.random() * 500 + 10).toFixed(2)),
        title: 'Zamówienie #' + Math.floor(Math.random() * 10000),
        timestamp: new Date().toLocaleTimeString('pl-PL'),
      });
      setStep('pending');
    }, 5000 + Math.random() * 8000);

    return () => clearTimeout(timer);
  }, [blikCode, step]);

  // ── Obsługa PIN (4 cyfry) z widzialnością ──────────────────────────────
  const handlePinChange = (index: number, value: string) => {
    if (value && !/^\d$/.test(value)) return;
    const newPin = [...pin];
    newPin[index] = value;
    setPin(newPin);

    if (value) {
      revealPinDigit(index); // pokaż cyfrę przez 2s
      if (index < 3) {
        pinRefs.current[index + 1]?.focus();
      }
    }
  };

  const handlePinKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace') {
      if (pin[index]) {
        // Usuń bieżącą cyfrę
        const newPin = [...pin];
        newPin[index] = '';
        setPin(newPin);
      } else if (index > 0) {
        // Przejdź do poprzedniego pola
        pinRefs.current[index - 1]?.focus();
      }
    }
  };

  // ── Potwierdzenie PIN-em (weryfikacja przez API) ───────────────────────
  const confirmWithPin = async () => {
    const pinStr = pin.join('');
    if (pinStr.length !== 4) return;

    // Weryfikacja PIN-u przez backend: wysyłamy PUT z currentPin = pinStr i newPin = pinStr
    // Jeśli backend zwróci sukces – PIN jest poprawny
    try {
      await axiosClient.put('/customers/me/blik-pin', {
        currentPin: pinStr,
        newPin: pinStr,
      });
    } catch {
      // Jeśli backend rzucił błędem – PIN jest nieprawidłowy
      setErrorMessage('Nieprawidłowy PIN. Spróbuj ponownie.');
      setPin(['', '', '', '']);
      setStep('error');
      return;
    }

    setStep('processing');
    setErrorMessage('');

    try {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      const mockRef = 'BLK' + Date.now().toString().slice(-8);
      setTxRef(mockRef);
      setStep('success');
    } catch {
      setErrorMessage('Błąd przetwarzania. Spróbuj ponownie.');
      setStep('error');
    }
  };

  // ── Odrzucenie transakcji ──────────────────────────────────────────────
  const rejectTransaction = () => {
    setStep('code');
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
            <div className={styles.txDetailValue}>{pendingTx?.merchantName}</div>
          </div>
        </div>
        <div className={styles.txDetailRow}>
          <UserIcon size={18} className={styles.txDetailIcon} />
          <div>
            <div className={styles.txDetailLabel}>Tytuł</div>
            <div className={styles.txDetailValue}>{pendingTx?.title}</div>
          </div>
        </div>
        <div className={styles.txDetailRow}>
          <Clock size={18} className={styles.txDetailIcon} />
          <div>
            <div className={styles.txDetailLabel}>Czas</div>
            <div className={styles.txDetailValue}>{pendingTx?.timestamp}</div>
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
          <span className={styles.pinSummaryValue}>{pendingTx?.merchantName}</span>
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
          <strong>{pendingTx?.merchantName}</strong>
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
    </div>
  );
};

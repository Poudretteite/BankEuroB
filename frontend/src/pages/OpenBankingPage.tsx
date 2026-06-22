import React, { useState, useEffect } from 'react';
import styles from './OpenBanking.module.css';
import { openBankingApi } from '../api/openbanking';
import type { ExternalAccount } from '../api/openbanking';

export function OpenBankingPage() {
  const [accounts, setAccounts] = useState<ExternalAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState<ExternalAccount | null>(null);

  useEffect(() => {
    fetchAccounts();
  }, []);

  const fetchAccounts = async () => {
    try {
      setLoading(true);
      const data = await openBankingApi.getExternalAccounts();
      setAccounts(data);
    } catch (err) {
      console.error('Failed to fetch external accounts', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1>Inne Banki (PSD2)</h1>
        <button className={styles.linkButton} onClick={() => setShowLinkModal(true)}>
          + Podłącz bank
        </button>
      </div>

      {loading ? (
        <p>Wczytywanie...</p>
      ) : accounts.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-secondary)' }}>
          <p>Nie masz podłączonych żadnych kont z innych banków.</p>
        </div>
      ) : (
        <div className={styles.accountsGrid}>
          {accounts.map(acc => (
            <div key={acc.id} className={styles.accountCard}>
              <span className={styles.bankBadge}>{acc.bankUrl}</span>
              <div className={styles.balance}>{acc.balance.toFixed(2)} {acc.currency}</div>
              <div className={styles.iban}>{acc.iban}</div>
              <button 
                className={styles.transferButton}
                onClick={() => {
                  setSelectedAccount(acc);
                  setShowTransferModal(true);
                }}
              >
                Zleć przelew z tego konta
              </button>
            </div>
          ))}
        </div>
      )}

      {showLinkModal && (
        <LinkBankModal 
          onClose={() => setShowLinkModal(false)} 
          onSuccess={() => {
            setShowLinkModal(false);
            fetchAccounts();
          }} 
        />
      )}

      {showTransferModal && selectedAccount && (
        <ExternalTransferModal
          account={selectedAccount}
          onClose={() => setShowTransferModal(false)}
          onSuccess={() => {
            setShowTransferModal(false);
            fetchAccounts();
          }}
        />
      )}
    </div>
  );
}

function LinkBankModal({ onClose, onSuccess }: { onClose: () => void, onSuccess: () => void }) {
  const [url, setUrl] = useState('http://localhost:8080'); // Domyślnie EuroBankA backend
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setIsSubmitting(true);
      setError('');
      await openBankingApi.linkBank(url, email, password);
      onSuccess();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Nie udało się połączyć');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modal}>
        <h2>Podłącz zewnętrzny bank</h2>
        {error && <div className={styles.errorMsg}>{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className={styles.formGroup}>
            <label>Adres API Banku</label>
            <input value={url} onChange={e => setUrl(e.target.value)} required />
          </div>
          <div className={styles.formGroup}>
            <label>Email w obcym banku</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)} required />
          </div>
          <div className={styles.formGroup}>
            <label>Hasło</label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)} required />
          </div>
          <div className={styles.modalActions}>
            <button type="button" className={styles.cancelBtn} onClick={onClose} disabled={isSubmitting}>Anuluj</button>
            <button type="submit" className={styles.submitBtn} disabled={isSubmitting}>Połącz konto</button>
          </div>
        </form>
      </div>
    </div>
  );
}

function ExternalTransferModal({ account, onClose, onSuccess }: { account: ExternalAccount, onClose: () => void, onSuccess: () => void }) {
  const [toAccount, setToAccount] = useState('');
  const [amount, setAmount] = useState('');
  const [desc, setDesc] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setIsSubmitting(true);
      setError('');
      await openBankingApi.executeTransfer({
        linkedBankId: account.linkedBankId,
        fromAccountId: account.id,
        toAccountNumber: toAccount,
        amount: parseFloat(amount),
        currency: account.currency,
        description: desc || 'Przelew zewnętrzny'
      });
      onSuccess();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Przelew nie powiódł się');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modal}>
        <h2>Przelew z obcego konta</h2>
        <p style={{marginBottom: '16px', fontSize: '14px', color: 'var(--text-secondary)'}}>Dostępne: {account.balance.toFixed(2)} {account.currency}</p>
        
        {error && <div className={styles.errorMsg}>{error}</div>}
        
        <form onSubmit={handleSubmit}>
          <div className={styles.formGroup}>
            <label>Na konto (IBAN)</label>
            <input value={toAccount} onChange={e => setToAccount(e.target.value)} required placeholder="DE..." />
          </div>
          <div className={styles.formGroup}>
            <label>Kwota</label>
            <input type="number" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} required />
          </div>
          <div className={styles.formGroup}>
            <label>Tytuł (opcjonalnie)</label>
            <input value={desc} onChange={e => setDesc(e.target.value)} />
          </div>
          <div className={styles.modalActions}>
            <button type="button" className={styles.cancelBtn} onClick={onClose} disabled={isSubmitting}>Anuluj</button>
            <button type="submit" className={styles.submitBtn} disabled={isSubmitting}>Wyślij</button>
          </div>
        </form>
      </div>
    </div>
  );
}

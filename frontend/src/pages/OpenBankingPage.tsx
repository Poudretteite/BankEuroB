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

  const handleUnlink = async (linkedBankId: string) => {
    if (confirm('Czy na pewno chcesz usunąć to połączone konto?')) {
      try {
        await openBankingApi.unlinkBank(linkedBankId);
        fetchAccounts();
      } catch (err) {
        console.error('Failed to unlink bank', err);
        alert('Nie udało się usunąć połączenia.');
      }
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
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <span className={styles.bankBadge}>
                  {acc.bankUrl.includes('8090') ? 'Bank Euro A' : 
                   acc.bankUrl.replace('http://host.docker.internal:', 'Inny Bank :')}
                </span>
              </div>
              {acc.ownerName && <div style={{ fontSize: '0.95rem', color: 'var(--text-secondary)', marginBottom: '8px' }}>👤 {acc.ownerName}</div>}
              <div className={styles.balance}>{acc.balance.toFixed(2)} {acc.currency}</div>
              <div className={styles.iban}>{acc.iban || acc.accountNumber}</div>
              <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
                <button 
                  className={styles.transferButton}
                  style={{ flex: 1 }}
                  onClick={() => {
                    setSelectedAccount(acc);
                    setShowTransferModal(true);
                  }}
                >
                  Zleć przelew
                </button>
                <button 
                  className={styles.transferButton}
                  style={{ flex: 0, backgroundColor: 'transparent', border: '1px solid #ef4444', color: '#ef4444', padding: '0 12px' }}
                  onClick={() => handleUnlink(acc.linkedBankId)}
                  title="Usuń połączenie z bankiem"
                >
                  Usuń
                </button>
              </div>
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
  const [bic, setBic] = useState('');
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
        bic: bic,
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
            <label>BIC / SWIFT (wymagany dla SEPA)</label>
            <input value={bic} onChange={e => setBic(e.target.value)} required placeholder="Nbp..." />
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

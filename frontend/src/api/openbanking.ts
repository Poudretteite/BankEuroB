import axiosClient from './axiosClient';

export interface ExternalAccount {
  id: string;
  linkedBankId: string;
  bankUrl: string;
  iban?: string;
  accountNumber?: string;
  balance: number;
  currency: string;
  accountType: string;
  ownerName?: string;
}

export const openBankingApi = {
  linkBank: async (bankUrl: string, email: string, password: string) => {
    return axiosClient.post('/open-banking/link', { bankUrl, email, password });
  },

  getExternalAccounts: async (): Promise<ExternalAccount[]> => {
    const response = await axiosClient.get('/open-banking/accounts');
    return response.data;
  },

  executeTransfer: async (data: {
    linkedBankId: string;
    fromAccountId: string;
    toAccountNumber: string;
    bic?: string;
    amount: number;
    currency: string;
    description: string;
  }) => {
    const response = await axiosClient.post('/open-banking/transfers', data);
    return response.data;
  },

  unlinkBank: async (linkedBankId: string) => {
    return axiosClient.delete(`/open-banking/link/${linkedBankId}`);
  }
};

import axiosClient from './axiosClient';

export interface ExternalAccount {
  id: string;
  linkedBankId: string;
  bankUrl: string;
  iban: string;
  balance: number;
  currency: string;
  accountType: string;
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
    amount: number;
    currency: string;
    description: string;
  }) => {
    const response = await axiosClient.post('/open-banking/transfers', data);
    return response.data;
  }
};

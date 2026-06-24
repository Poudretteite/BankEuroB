package com.bankeurob.transfer.aml;

import com.bankeurob.transfer.Transaction;
import org.springframework.stereotype.Service;

@Service
public class AmlService {

    public boolean isSuspicious(Transaction transaction) {
        if (transaction.getTitle() != null && transaction.getTitle().toUpperCase().contains("AML_TEST")) {
            return true;
        }
        // Wersja mockowana - ok. 10% szansy na blokadę
        return Math.random() < 0.1;
    }
}

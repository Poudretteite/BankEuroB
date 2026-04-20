package com.bankeurob.account;

import com.bankeurob.account.dto.AccountDto;
import com.bankeurob.security.CustomerUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<AccountDto> getMyAccounts(Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        UUID customerId = userDetails.getCustomerId();
        return accountRepository.findByCustomerId(customerId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountById(UUID accountId, Authentication authentication) {
        CustomerUserDetails userDetails = (CustomerUserDetails) authentication.getPrincipal();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Konto nie znalezione: " + accountId));
        if (!account.getCustomer().getId().equals(userDetails.getCustomerId())) {
            throw new AccessDeniedException("Brak dostępu do tego konta");
        }
        return toDto(account);
    }

    private AccountDto toDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .iban(account.getIban())
                .bic(account.getBic())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .dailyLimit(account.getDailyLimit())
                .isActive(account.getIsActive())
                .createdAt(account.getCreatedAt())
                .build();
    }
}

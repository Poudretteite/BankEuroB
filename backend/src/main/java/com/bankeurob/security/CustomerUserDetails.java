package com.bankeurob.security;

import com.bankeurob.account.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class CustomerUserDetails implements UserDetails {

    private final Customer customer;

    public UUID getCustomerId() {
        return customer.getId();
    }

    public Customer getCustomer() {
        return customer;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + customer.getRole()));
    }

    @Override
    public String getPassword() {
        return customer.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return customer.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return customer.getIsActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return customer.getIsActive();
    }
}

package com.aisocialgame.backend.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.aisocialgame.backend.entity.UserAccount;

public class AccountUserDetails implements UserDetails {

    private final UserAccount user;

    public AccountUserDetails(UserAccount user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (user.isAdmin()) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public UserAccount getUser() {
        return user;
    }

    public boolean isAdmin() {
        return user.isAdmin();
    }
}

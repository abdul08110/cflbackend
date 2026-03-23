package com.friendsfantasy.fantasybackend.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AdminPrincipal {

    private final Long id;
    private final String username;
}

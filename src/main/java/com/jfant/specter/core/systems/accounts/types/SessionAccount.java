/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.systems.accounts.types;

import com.mojang.util.UndashedUuid;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.accounts.TokenVault;
import com.jfant.specter.core.systems.accounts.AccountType;
import com.jfant.specter.core.systems.accounts.TokenAccount;
import com.jfant.specter.core.utils.network.Http;
import net.minecraft.client.User;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

public class SessionAccount extends Account<SessionAccount> implements TokenAccount {
    private String accessToken;

    public SessionAccount(String label) {
        super(AccountType.Session, label);
        accessToken = label;
    }

    @Override
    public SessionAccount fromTag(CompoundTag tag) {
        super.fromTag(tag);

        accessToken = TokenVault.reveal(tag.getStringOr("token", ""));
        return this;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        tag.putString("token", TokenVault.protect(accessToken));
        return tag;
    }

    @Override
    public boolean fetchInfo() {
        if (accessToken == null || accessToken.isBlank()) return false;

        ProfileResponse profile;
        try {
            profile = Http.get("https://api.minecraftservices.com/minecraft/profile")
                .bearer(accessToken)
                .sendJson(ProfileResponse.class);
        } catch (IllegalArgumentException e) {
            SpecterCore.LOG.error("Session account token was rejected ({})", e.getClass().getSimpleName());
            return false;
        }

        if (profile == null || profile.id == null || profile.name == null) return false;

        cache.username = profile.name;
        cache.uuid = profile.id;

        return true;
    }

    @Override
    public boolean login() {
        if (accessToken == null || accessToken.isBlank()) return false;

        super.login();

        setSession(new User(cache.username, UndashedUuid.fromStringLenient(cache.uuid), accessToken, Optional.empty(), Optional.empty()), accessToken);
        return true;
    }

    @Override
    public String getToken() {
        return accessToken;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SessionAccount account2)) return false;
        return account2.name.equals(this.name);
    }

    private static class ProfileResponse {
        public String id;
        public String name;
    }
}

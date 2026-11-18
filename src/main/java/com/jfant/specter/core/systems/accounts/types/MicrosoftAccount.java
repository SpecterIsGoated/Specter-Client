package com.jfant.specter.core.systems.accounts.types;

import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.accounts.AccountType;
import com.jfant.specter.core.systems.accounts.MicrosoftLogin;
import com.mojang.util.UndashedUuid;
import java.util.Optional;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;

public class MicrosoftAccount extends Account<MicrosoftAccount> {
   @Nullable
   private String token;

   public MicrosoftAccount(String refreshToken) {
      super(AccountType.Microsoft, refreshToken);
   }

   @Override
   public boolean fetchInfo() {
      this.token = this.auth();
      return this.token != null;
   }

   @Override
   public boolean login() {
      if (this.token == null) {
         return false;
      } else {
         super.login();
         setSession(new User(this.cache.username, UndashedUuid.fromStringLenient(this.cache.uuid), this.token, Optional.empty(), Optional.empty()));
         return true;
      }
   }

   @Nullable
   private String auth() {
      MicrosoftLogin.LoginData data = MicrosoftLogin.login(this.name);
      if (data != null && data.newRefreshToken() != null) {
         this.name = data.newRefreshToken();
         this.cache.username = data.username();
         this.cache.uuid = data.uuid();
         return data.mcToken();
      } else {
         return null;
      }
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof MicrosoftAccount account ? account.name.equals(this.name) : false;
   }
}

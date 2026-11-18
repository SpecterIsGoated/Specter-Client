package com.jfant.specter.core.systems.accounts.types;

import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.accounts.AccountType;
import java.util.Optional;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

public class CrackedAccount extends Account<CrackedAccount> {
   public CrackedAccount(String name) {
      super(AccountType.Cracked, name);
   }

   @Override
   public boolean fetchInfo() {
      this.cache.username = this.name;
      return true;
   }

   @Override
   public boolean login() {
      super.login();
      setSession(new User(this.name, UUIDUtil.createOfflinePlayerUUID(this.name), "", Optional.empty(), Optional.empty()));
      return true;
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof CrackedAccount account ? account.getUsername().equals(this.getUsername()) : false;
   }
}

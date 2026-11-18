package com.jfant.specter.core.gui.widgets;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.gui.screens.accounts.AccountInfoScreen;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.gui.widgets.pressable.WConfirmedMinus;
import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.accounts.Accounts;
import com.jfant.specter.core.systems.accounts.TokenAccount;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import com.jfant.specter.core.utils.render.color.Color;

public abstract class WAccount extends WHorizontalList {
   public Runnable refreshScreenAction;
   private final WidgetScreen screen;
   private final Account<?> account;

   public WAccount(WidgetScreen screen, Account<?> account) {
      this.screen = screen;
      this.account = account;
   }

   protected abstract Color loggedInColor();

   protected abstract Color accountTypeColor();

   @Override
   public void init() {
      this.add(this.theme.texture(32.0, 32.0, this.account.getCache().getHeadTexture().needsRotate() ? 90.0 : 0.0, this.account.getCache().getHeadTexture()));
      WLabel name = this.add(this.theme.label(this.account.getUsername())).widget();
      if (SpecterCore.mc.getUser().getName().equalsIgnoreCase(this.account.getUsername())) {
         name.color = this.loggedInColor();
      }

      WLabel label = this.add(this.theme.label("(" + this.account.getType() + ")")).expandCellX().right().widget();
      label.color = this.accountTypeColor();
      if (this.account instanceof TokenAccount) {
         WButton info = this.add(this.theme.button("Info")).widget();
         info.action = () -> SpecterCore.mc.setScreen(new AccountInfoScreen(this.theme, this.account));
      }

      WButton login = this.add(this.theme.button("Login")).widget();
      login.action = () -> {
         login.minWidth = login.width;
         login.set("...");
         this.screen.locked = true;
         MeteorExecutor.execute(() -> {
            if (this.account.fetchInfo() && this.account.login()) {
               name.set(this.account.getUsername());
               Accounts.get().save();
               this.screen.taskAfterRender = this.refreshScreenAction;
            }

            login.minWidth = 0.0;
            login.set("Login");
            this.screen.locked = false;
         });
      };
      WConfirmedMinus remove = this.add(this.theme.confirmedMinus()).widget();
      remove.action = () -> {
         Accounts.get().remove(this.account);
         if (this.refreshScreenAction != null) {
            this.refreshScreenAction.run();
         }
      };
   }
}

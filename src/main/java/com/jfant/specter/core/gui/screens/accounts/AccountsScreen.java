package com.jfant.specter.core.gui.screens.accounts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.WindowScreen;
import com.jfant.specter.core.gui.widgets.WAccount;
import com.jfant.specter.core.gui.widgets.containers.WContainer;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WButton;
import com.jfant.specter.core.systems.accounts.Account;
import com.jfant.specter.core.systems.accounts.AccountType;
import com.jfant.specter.core.systems.accounts.Accounts;
import com.jfant.specter.core.utils.misc.NbtUtils;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import org.jetbrains.annotations.Nullable;

public class AccountsScreen extends WindowScreen {
   public AccountsScreen(GuiTheme theme) {
      super(theme, "Accounts");
   }

   @Override
   public void initWidgets() {
      for (Account<?> account : Accounts.get()) {
         WAccount wAccount = this.add(this.theme.account(this, account)).expandX().widget();
         wAccount.refreshScreenAction = this::reload;
      }

      WHorizontalList l = this.add(this.theme.horizontalList()).expandX().widget();
      this.addButton(l, "Cracked", () -> SpecterCore.mc.setScreen(new AddCrackedAccountScreen(this.theme, this)));
      this.addButton(l, "Altening", () -> SpecterCore.mc.setScreen(new AddAlteningAccountScreen(this.theme, this)));
      this.addButton(l, "Session", () -> SpecterCore.mc.setScreen(new AddSessionAccountScreen(this.theme, this)));
      this.addButton(l, "Microsoft", () -> SpecterCore.mc.setScreen(new AddMicrosoftAccountScreen(this.theme, this)));
   }

   private void addButton(WContainer c, String text, Runnable action) {
      WButton button = c.add(this.theme.button(text)).expandX().widget();
      button.action = action;
   }

   public static void addAccount(@Nullable AddAccountScreen screen, AccountsScreen parent, Account<?> account) {
      if (screen != null) {
         screen.locked = true;
      }

      MeteorExecutor.execute(() -> {
         if (!account.fetchInfo()) {
            SpecterCore.mc.execute(() -> {
               if (screen != null) {
                  screen.locked = false;
               }
            });
         } else {
            Accounts.get().add(account);
            if (account.login()) {
               if (account.getType() != AccountType.Cracked) {
                  account.getCache().loadHead(parent::reload);
               }

               Accounts.get().save();
            }

            SpecterCore.mc.execute(() -> {
               if (screen != null) {
                  screen.locked = false;
                  screen.onClose();
               }

               parent.reload();
            });
         }
      });
   }

   @Override
   public boolean toClipboard() {
      return NbtUtils.toClipboard(Accounts.get());
   }

   @Override
   public boolean fromClipboard() {
      return NbtUtils.fromClipboard(Accounts.get());
   }
}

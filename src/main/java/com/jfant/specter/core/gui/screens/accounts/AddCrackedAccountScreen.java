package com.jfant.specter.core.gui.screens.accounts;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.systems.accounts.Accounts;
import com.jfant.specter.core.systems.accounts.types.CrackedAccount;

public class AddCrackedAccountScreen extends AddAccountScreen {
   public AddCrackedAccountScreen(GuiTheme theme, AccountsScreen parent) {
      super(theme, "Add Cracked Account", parent);
   }

   @Override
   public void initWidgets() {
      WTable t = this.add(this.theme.table()).widget();
      t.add(this.theme.label("Name: "));
      WTextBox name = t.add(this.theme.textBox("", "seasnail8169", (text, c) -> c > ' ' && c < 127)).minWidth(400.0).expandX().widget();
      name.setFocused(true);
      t.row();
      this.add = t.add(this.theme.button("Add")).expandX().widget();
      this.add.action = () -> {
         String username = name.get().trim();
         if (username.length() <= 16) {
            CrackedAccount account = new CrackedAccount(username);
            if (!Accounts.get().exists(account)) {
               AccountsScreen.addAccount(this, this.parent, account);
            }
         }
      };
      this.enterAction = this.add.action;
   }
}

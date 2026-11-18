package com.jfant.specter.core.gui.screens.accounts;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.systems.accounts.types.SessionAccount;

public class AddSessionAccountScreen extends AddAccountScreen {
   public AddSessionAccountScreen(GuiTheme theme, AccountsScreen parent) {
      super(theme, "Add Session Account", parent);
   }

   @Override
   public void initWidgets() {
      WTable t = this.add(this.theme.table()).widget();
      t.add(this.theme.label("Access Token: "));
      WTextBox token = t.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      token.setFocused(true);
      t.row();
      this.add = t.add(this.theme.button("Add")).expandX().widget();
      this.add.action = () -> {
         if (!token.get().isEmpty()) {
            SessionAccount account = new SessionAccount(token.get());
            AccountsScreen.addAccount(this, this.parent, account);
         }
      };
      this.enterAction = this.add.action;
   }
}

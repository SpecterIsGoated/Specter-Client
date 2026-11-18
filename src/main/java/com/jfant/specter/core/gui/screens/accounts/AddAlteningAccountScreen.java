package com.jfant.specter.core.gui.screens.accounts;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.widgets.containers.WTable;
import com.jfant.specter.core.gui.widgets.input.WTextBox;
import com.jfant.specter.core.systems.accounts.types.TheAlteningAccount;

public class AddAlteningAccountScreen extends AddAccountScreen {
   public AddAlteningAccountScreen(GuiTheme theme, AccountsScreen parent) {
      super(theme, "Add The Altening Account", parent);
   }

   @Override
   public void initWidgets() {
      WTable t = this.add(this.theme.table()).widget();
      t.add(this.theme.label("Token: "));
      WTextBox token = t.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      token.setFocused(true);
      t.row();
      this.add = t.add(this.theme.button("Add")).expandX().widget();
      this.add.action = () -> {
         if (!token.get().isEmpty()) {
            AccountsScreen.addAccount(this, this.parent, new TheAlteningAccount(token.get()));
         }
      };
      this.enterAction = this.add.action;
   }
}

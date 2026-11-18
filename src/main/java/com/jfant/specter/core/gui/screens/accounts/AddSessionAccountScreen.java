/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

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
        WTable t = add(theme.table()).widget();

        // Access token
        t.add(theme.label("Access Token: "));
        WTextBox token = t.add(theme.textBox("")).minWidth(400).expandX().widget();
        token.setFocused(true);
        t.row();

        // Add
        add = t.add(theme.button("Add")).expandX().widget();
        add.action = () -> {
            if (!token.get().isEmpty()) {
                SessionAccount account = new SessionAccount(token.get());
                AccountsScreen.addAccount(this, parent, account);
            }
        };

        enterAction = add.action;
    }
}

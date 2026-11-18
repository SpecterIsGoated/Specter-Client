/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

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
        WTable t = add(theme.table()).widget();

        // Token
        t.add(theme.label("Token: "));
        WTextBox token = t.add(theme.textBox("")).minWidth(400).expandX().widget();
        token.setFocused(true);
        t.row();

        // Add
        add = t.add(theme.button("Add")).expandX().widget();
        add.action = () -> {
            if (!token.get().isEmpty()) {
                AccountsScreen.addAccount(this, parent, new TheAlteningAccount(token.get()));
            }
        };

        enterAction = add.action;
    }
}

/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.gui.tabs;

import com.jfant.specter.core.gui.GuiTheme;
import com.jfant.specter.core.gui.utils.Cell;
import com.jfant.specter.core.gui.widgets.WWidget;
import com.jfant.specter.core.gui.widgets.containers.WWindow;

public abstract class WindowTabScreen extends TabScreen {
    protected final WWindow window;

    public WindowTabScreen(GuiTheme theme, Tab tab) {
        super(theme, tab);

        window = super.add(theme.window(tab.name)).center().widget();
    }

    @Override
    public <W extends WWidget> Cell<W> add(W widget) {
        return window.add(widget);
    }

    @Override
    public void clear() {
        window.clear();
    }
}

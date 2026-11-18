/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.gui.themes.meteor.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.themes.meteor.MeteorWidget;
import com.jfant.specter.core.gui.widgets.WTooltip;
import com.jfant.specter.core.renderer.text.TextRenderer;
import com.jfant.specter.core.renderer.text.VanillaTextRenderer;

public class WMeteorTooltip extends WTooltip implements MeteorWidget {
    public WMeteorTooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (TextRenderer.get() == VanillaTextRenderer.INSTANCE) {
            renderer.fill(this, theme().backgroundColor.get());
        } else {
            renderer.quad(this, theme().backgroundColor.get());
        }
    }
}

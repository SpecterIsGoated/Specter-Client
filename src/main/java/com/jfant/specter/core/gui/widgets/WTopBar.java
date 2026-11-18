/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.gui.widgets;

import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.tabs.Tab;
import com.jfant.specter.core.gui.tabs.TabScreen;
import com.jfant.specter.core.gui.tabs.Tabs;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WPressable;
import com.jfant.specter.core.utils.render.color.Color;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.sdl.SDLMouse;

import static com.jfant.specter.core.SpecterCore.mc;

public abstract class WTopBar extends WHorizontalList {
    protected abstract Color getButtonColor(boolean pressed, boolean hovered);

    protected abstract Color getNameColor();

    public WTopBar() {
        spacing = 0;
    }

    @Override
    public void init() {
        for (Tab tab : Tabs.get()) {
            add(new WTopBarButton(tab));
        }
    }

    protected class WTopBarButton extends WPressable {
        private final Tab tab;

        public WTopBarButton(Tab tab) {
            this.tab = tab;
        }

        @Override
        protected void onCalculateSize() {
            double pad = pad();

            width = pad + theme.textWidth(tab.name) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onPressed(int button) {
            Screen screen = mc.gui.screen();

            if (!(screen instanceof TabScreen tabScreen) || tabScreen.tab != tab) {
                double mouseX = mc.mouseHandler.xpos();
                double mouseY = mc.mouseHandler.ypos();

                tab.openScreen(theme);
                SDLMouse.SDL_WarpMouseInWindow(mc.getWindow().handle(), (float) mouseX, (float) mouseY);
            }
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double pad = pad();
            Color color = getButtonColor(pressed || (mc.gui.screen() instanceof TabScreen tabScreen && tabScreen.tab == tab), mouseOver);

            renderer.quad(x, y, width, height, color);
            renderer.text(tab.name, x + pad, y + pad, getNameColor(), false);
        }
    }
}

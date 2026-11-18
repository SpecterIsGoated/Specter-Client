package com.jfant.specter.core.gui.widgets;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.gui.renderer.GuiRenderer;
import com.jfant.specter.core.gui.tabs.Tab;
import com.jfant.specter.core.gui.tabs.TabScreen;
import com.jfant.specter.core.gui.tabs.Tabs;
import com.jfant.specter.core.gui.widgets.containers.WHorizontalList;
import com.jfant.specter.core.gui.widgets.pressable.WPressable;
import com.jfant.specter.core.utils.render.color.Color;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public abstract class WTopBar extends WHorizontalList {
   protected abstract Color getButtonColor(boolean var1, boolean var2);

   protected abstract Color getNameColor();

   public WTopBar() {
      this.spacing = 0.0;
   }

   @Override
   public void init() {
      for (Tab tab : Tabs.get()) {
         this.add(new WTopBar.WTopBarButton(tab));
      }
   }

   protected class WTopBarButton extends WPressable {
      private final Tab tab;

      public WTopBarButton(Tab tab) {
         this.tab = tab;
      }

      @Override
      protected void onCalculateSize() {
         double pad = this.pad();
         this.width = pad + this.theme.textWidth(this.tab.name) + pad;
         this.height = pad + this.theme.textHeight() + pad;
      }

      @Override
      protected void onPressed(int button) {
         Screen screen = SpecterCore.mc.screen;
         if (!(screen instanceof TabScreen) || ((TabScreen)screen).tab != this.tab) {
            double mouseX = SpecterCore.mc.mouseHandler.xpos();
            double mouseY = SpecterCore.mc.mouseHandler.ypos();
            this.tab.openScreen(this.theme);
            GLFW.glfwSetCursorPos(SpecterCore.mc.getWindow().handle(), mouseX, mouseY);
         }
      }

      @Override
      protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
         double pad = this.pad();
         Color color = WTopBar.this.getButtonColor(
            this.pressed || SpecterCore.mc.screen instanceof TabScreen && ((TabScreen)SpecterCore.mc.screen).tab == this.tab, this.mouseOver
         );
         renderer.quad(this.x, this.y, this.width, this.height, color);
         renderer.text(this.tab.name, this.x + pad, this.y + pad, WTopBar.this.getNameColor(), false);
      }
   }
}

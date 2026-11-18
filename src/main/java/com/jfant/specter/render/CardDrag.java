package com.jfant.specter.render;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.systems.hud.screens.HudEditorScreen;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

public final class CardDrag {
   private static CardDrag holding;
   private static final float CENTER_SNAP = 0.0625F;
   private final CardSpot owner;
   private boolean dragging;
   private boolean wasDown;
   private boolean taken;
   private float grabX;
   private float grabY;

   CardDrag(CardSpot owner) {
      this.owner = owner;
   }

   static CardSpot dragged() {
      return holding != null && holding.dragging ? holding.owner : null;
   }

   public void update(Setting<Corner> corner, Setting<Integer> offsetX, Setting<Integer> offsetY, float x, float y, float width, float height) {
      Window window = SpecterCore.mc.getWindow();
      boolean down = (SpecterCore.mc.screen instanceof ChatScreen || SpecterCore.mc.screen instanceof HudEditorScreen)
         && GLFW.glfwGetMouseButton(window.handle(), 0) == 1;
      boolean pressed = down && !this.wasDown;
      this.wasDown = down;
      if (!down) {
         this.dragging = false;
         this.taken = false;
         if (holding == this) {
            holding = null;
         }
      } else {
         float mouseX = (float)SpecterCore.mc.mouseHandler.getScaledXPos(window);
         float mouseY = (float)SpecterCore.mc.mouseHandler.getScaledYPos(window);
         if (!this.dragging) {
            if (this.taken || !pressed || mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
               return;
            }

            if (this.owner.click(mouseX - x, mouseY - y)) {
               this.taken = true;
               if (holding != null) {
                  holding.dragging = false;
                  holding = null;
               }

               return;
            }

            this.dragging = true;
            holding = this;
            this.grabX = mouseX - x;
            this.grabY = mouseY - y;
         }

         if (holding != this) {
            this.dragging = false;
         } else {
            int screenWidth = window.getGuiScaledWidth();
            int screenHeight = window.getGuiScaledHeight();
            float left = Math.clamp(mouseX - this.grabX, 0.0F, Math.max(0.0F, screenWidth - width));
            float top = Math.clamp(mouseY - this.grabY, 0.0F, Math.max(0.0F, screenHeight - height));
            float[] free = CardLayout.free(this.owner, left, top, width, height);
            left = free[0];
            top = free[1];
            float middleX = left + width / 2.0F;
            float screenMiddle = screenWidth / 2.0F;
            boolean bottom = top + height / 2.0F > screenHeight / 2.0F;
            boolean centered = Math.abs(middleX - screenMiddle) <= screenWidth * 0.0625F;
            Corner pin = centered ? Corner.centered(bottom) : Corner.of(middleX > screenMiddle, bottom);
            if (corner.get() != pin) {
               corner.set(pin);
            }

            offsetX.set(Math.round(centered ? left - (screenWidth - width) / 2.0F : (pin.right ? screenWidth - width - left : left)));
            offsetY.set(Math.round(pin.bottom ? screenHeight - height - top : top));
         }
      }
   }
}

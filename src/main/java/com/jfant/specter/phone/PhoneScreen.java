package com.jfant.specter.phone;

import com.jfant.specter.modules.DonutPhone;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The screen that is open while the phone is out. Opening a screen is what frees the mouse cursor and stops
 * movement/attack keys reaching the game; closing it gives the mouse back to the game, so the phone never leaves the
 * input in a stuck state. Everything visible is drawn by {@link PhoneOS}; this only forwards input.
 */
public final class PhoneScreen extends Screen {
   PhoneScreen() {
      super(Component.literal("Donut Phone"));
   }

   @Override
   protected void init() {
      // SDL3 only delivers typed characters while text input is on
      this.minecraft.textInputManager().startTextInput(this);
      PhoneOS.get().screenBack();
   }

   @Override
   public void removed() {
      this.minecraft.textInputManager().stopTextInput(this);
      PhoneOS.get().screenLeft();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public void onClose() {
      PhoneOS.get().close();
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      // normally drawn under the HUD by PhoneOS.hudPass; only here when the HUD is hidden
      if (!PhoneOS.get().drawnUnderHud()) PhoneOS.get().drawDim(ctx);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      if (PhoneOS.get().drawnUnderHud()) return;
      double scale = this.minecraft.getWindow().getGuiScale();
      double mx = this.minecraft.mouseHandler.xpos() / scale, my = this.minecraft.mouseHandler.ypos() / scale;
      PhoneOS.get().renderInterface(ctx, mx, my);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
      return PhoneOS.get().input().press(event.x(), event.y(), event.button());
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      return PhoneOS.get().input().release(event.x(), event.y(), event.button());
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return PhoneOS.get().input().drag(event.x(), event.y());
   }

   @Override
   public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      return PhoneOS.get().input().scroll(x, y, vertical);
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      PhoneOS os = PhoneOS.get();
      DonutPhone m = PhoneOS.module();
      boolean typing = os.input().typing();
      if (!typing && m != null) {
         // the chat key again: regular chat (opened next tick, so the typed letter doesn't land in the chat box)
         if (m.chatKeyTwice() && this.minecraft.options.keyChat.matches(event)) {
            m.requestChat();
            return true;
         }
         if (m.openKeyMatches(event)) {
            os.close();
            return true;
         }
      }
      os.input().key(event.key(), event.modifiers());
      return true;
   }

   /** Drop a PNG/JPG on the game window while the phone is out: it becomes the wallpaper. */
   @Override
   public void onFilesDrop(java.util.List<java.nio.file.Path> files) {
      for (java.nio.file.Path f : files) {
         if (com.jfant.specter.phone.core.CustomWallpapers.isPicture(f.getFileName().toString())) {
            PhoneOS.get().useWallpaperFile(f);
            return;
         }
      }
      PhoneOS.get().toast("Drop a PNG, JPG or WebP to use it as your wallpaper");
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      return PhoneOS.get().input().chr(event.codepoint());
   }
}

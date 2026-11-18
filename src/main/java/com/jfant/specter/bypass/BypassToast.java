package com.jfant.specter.bypass;

import com.jfant.specter.render.CardText;
import com.jfant.specter.render.PurpleCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class BypassToast implements Toast {
   private static final int TIMER_TRACK = -2145973978;
   private static final int TIMER_FILL = -523789323;
   private static final int TEXT_X = 30;
   private static final int TEXT_PAD = 7;
   private static final int RIGHT_GAP = 3;
   private static final int MAX_WIDTH = 240;
   private static final long DISPLAY_MILLIS = 5000L;
   private final Object token;
   private final ItemStack icon;
   private final boolean sound;
   private Component title;
   private Component message;
   private int width;
   private int count = 1;
   private boolean changed;
   private boolean hiding;
   private long changedAt;
   private Visibility wanted = Visibility.HIDE;

   private BypassToast(Object token, ItemStack icon, Component title, Component message, boolean sound) {
      this.token = token;
      this.icon = icon.copy();
      this.title = title;
      this.message = message;
      this.sound = sound;
      this.fit();
   }

   public static void addOrUpdate(ItemStack icon, String title, Component message, boolean sound) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isSameThread()) {
         mc.execute(() -> addOrUpdate(icon, title, message, sound));
      } else {
         ToastManager toasts = mc.gui.toastManager();
         BypassToast toast = toasts.getToast(BypassToast.class, title);
         if (toast == null) {
            toasts.addToast(new BypassToast(title, icon, Component.literal(title), message, sound));
         } else {
            toast.count++;
            toast.message = message;
            toast.changed = true;
            toast.fit();
         }
      }
   }

   private String countLabel() {
      return this.count > 1 ? " x" + this.count : "";
   }

   private void fit() {
      int text = Math.max(CardText.width(this.title) + CardText.width(this.countLabel()), CardText.width(this.message));
      this.width = Mth.clamp(30 + text + 7, 160, 240);
   }

   @Override
   public Object getToken() {
      return this.token;
   }

   @Override
   public SoundEvent getSoundEvent() {
      return this.sound ? SoundEvents.NOTE_BLOCK_BELL.value() : null;
   }

   @Override
   public Visibility getWantedVisibility() {
      return this.wanted;
   }

   @Override
   public int width() {
      return this.width;
   }

   @Override
   public void update(ToastManager toasts, long visibleFor) {
      if (!this.hiding) {
         if (this.changed) {
            this.changedAt = visibleFor;
            this.changed = false;
         }

         this.hiding = visibleFor - this.changedAt >= 5000.0 * toasts.getNotificationDisplayTimeMultiplier();
         this.wanted = this.hiding ? Visibility.HIDE : Visibility.SHOW;
      }
   }

   @Override
   public void onFinishedRendering() {
      if (this.changed) {
         this.hiding = false;
         Minecraft.getInstance().gui.toastManager().addToast(this);
      }
   }

   @Override
   public void render(GuiGraphicsExtractor graphics, Font font, long visibleFor) {
      int right = this.width - 3;
      int bottom = 31;
      PurpleCard.card(graphics, 0, 1, right, bottom);
      this.drawTimer(graphics, visibleFor, right, bottom);
      boolean single = this.message.getString().isEmpty();
      this.drawText(graphics, this.title, this.countLabel(), single ? 12 : 6, -1065473);
      if (!single) {
         this.drawText(graphics, this.message, "", 17, -1911561);
      }

      graphics.renderItem(this.icon, 8, 8);
   }

   private void drawTimer(GuiGraphicsExtractor graphics, long visibleFor, int right, int bottom) {
      double duration = 5000.0 * Minecraft.getInstance().gui.toastManager().getNotificationDisplayTimeMultiplier();
      float left = 1.0F - (float)Mth.clamp((visibleFor - this.changedAt) / duration, 0.0, 1.0);
      int x0 = 5;
      int x1 = right - 5;
      int y = bottom - 3;
      graphics.fill(x0, y, x1, y + 1, -2145973978);
      graphics.fill(x0, y, x0 + Math.round((x1 - x0) * left), y + 1, -523789323);
   }

   private void drawText(GuiGraphicsExtractor graphics, Component text, String suffix, int y, int color) {
      int room = this.width - 3 - 30 - 7 - CardText.width(suffix);
      FormattedCharSequence shown = CardText.clip(text, room);
      CardText.draw(graphics, shown, 30.0F, (float)y, color, true);
      if (!suffix.isEmpty()) {
         CardText.draw(graphics, suffix, (float)(30 + CardText.width(shown)), (float)y, -4149795, true);
      }
   }
}

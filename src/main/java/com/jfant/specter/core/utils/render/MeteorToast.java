package com.jfant.specter.core.utils.render;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.render.color.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MeteorToast implements Toast {
   private static final int TITLE_COLOR = Color.fromRGBA(145, 61, 226, 255);
   private static final int TEXT_COLOR = Color.fromRGBA(220, 220, 220, 255);
   private static final Identifier TEXTURE = Identifier.parse("toast/advancement");
   private static final long DEFAULT_DURATION = 6000L;
   private static final SoundInstance DEFAULT_SOUND = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 1.0F);
   @NotNull
   private final Component title;
   @Nullable
   private final Component text;
   @Nullable
   private final ItemStack icon;
   @Nullable
   private final SoundInstance customSound;
   private final long duration;
   private boolean playedSound;
   private long start = -1L;
   private Visibility visibility = Visibility.HIDE;

   private MeteorToast(MeteorToast.Builder builder) {
      this.title = builder.title;
      this.text = builder.text;
      this.icon = builder.icon;
      this.customSound = builder.customSound;
      this.duration = builder.duration;
   }

   @Override
   public Visibility getWantedVisibility() {
      return this.visibility;
   }

   @Override
   public void update(ToastManager manager, long time) {
      if (this.start == -1L) {
         this.start = time;
      }

      this.visibility = time - this.start >= this.duration ? Visibility.HIDE : Visibility.SHOW;
      if (!this.playedSound) {
         SpecterCore.mc.getSoundManager().play(this.customSound);
         this.playedSound = true;
      }
   }

   @Override
   public void render(GuiGraphics context, Font textRenderer, long startTime) {
      context.blitSprite(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, this.width(), this.height());
      int textX = this.icon != null ? 28 : 12;
      int titleY = 12;
      if (this.text != null) {
         context.drawString(textRenderer, this.text, textX, 18, TEXT_COLOR, false);
         titleY = 7;
      }

      context.drawString(textRenderer, this.title, textX, titleY, TITLE_COLOR, false);
      if (this.icon != null) {
         context.renderItem(this.icon, 8, 8);
      }
   }

   public static class Builder {
      @NotNull
      private final Component title;
      @Nullable
      private Component text;
      @Nullable
      private ItemStack icon;
      @Nullable
      private SoundInstance customSound = MeteorToast.DEFAULT_SOUND;
      private long duration = 6000L;

      public Builder(@NotNull String title) {
         this.title = Component.literal(title).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(MeteorToast.TITLE_COLOR)));
      }

      public MeteorToast.Builder text(@Nullable String text) {
         this.text = text != null && !text.trim().isEmpty()
            ? Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(MeteorToast.TEXT_COLOR)))
            : null;
         return this;
      }

      public MeteorToast.Builder icon(@Nullable Item item) {
         this.icon = item != null ? item.getDefaultInstance() : null;
         return this;
      }

      public MeteorToast.Builder sound(@Nullable SoundInstance sound) {
         this.customSound = sound;
         return this;
      }

      public MeteorToast.Builder duration(long duration) {
         this.duration = Math.max(0L, duration);
         return this;
      }

      public MeteorToast build() {
         return new MeteorToast(this);
      }
   }
}

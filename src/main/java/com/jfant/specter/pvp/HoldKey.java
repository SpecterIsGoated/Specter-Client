package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.core.utils.misc.input.Input;
import net.minecraft.client.KeyMapping;

public final class HoldKey {
   private final Setting<HoldKey.Mode> mode;
   private final Setting<Keybind> key;

   public HoldKey(SettingGroup group, String what) {
      this.mode = group.add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("activation"))
                  .description("Hold Use: " + what + " while you hold your use button (right-click). Hold Key: while you hold the key below."))
               .defaultValue(HoldKey.Mode.Use))
            .build()
      );
      this.key = group.add(
         new KeybindSetting.Builder()
            .name("hold-key")
            .description("The key to hold, with activation on Hold Key.")
            .defaultValue(Keybind.none())
            .visible(() -> this.mode.get() == HoldKey.Mode.Key)
            .build()
      );
   }

   public boolean held() {
      if (SpecterCore.mc.gui.screen() != null) {
         return false;
      } else {
         return this.mode.get() != HoldKey.Mode.Key ? Input.isPressed(SpecterCore.mc.options.keyUse) : this.key.get().isSet() && this.key.get().isPressed();
      }
   }

   public boolean onUseButton() {
      return this.mode.get() == HoldKey.Mode.Use;
   }

   public void suppress() {
      if (this.onUseButton()) {
         KeyMapping use = SpecterCore.mc.options.keyUse;
         use.setDown(false);

         while (use.consumeClick()) {
         }
      }
   }

   public void release() {
      if (this.onUseButton()) {
         SpecterCore.mc.options.keyUse.setDown(SpecterCore.mc.gui.screen() == null && Input.isPressed(SpecterCore.mc.options.keyUse));
      }
   }

   public static enum Mode {
      Use("Hold Use"),
      Key("Hold Key");

      private final String title;

      private Mode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}

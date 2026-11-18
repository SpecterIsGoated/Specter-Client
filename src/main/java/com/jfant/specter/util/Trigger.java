package com.jfant.specter.util;

import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.utils.misc.Keybind;
import net.minecraft.client.Minecraft;

public final class Trigger {
   private static final int REARM_MARGIN = 2;
   private static final int COOLDOWN_TICKS = 400;
   private static final int JOIN_GRACE = 40;
   private final Setting<Trigger.Mode> mode;
   private final Setting<Keybind> key;
   private final Setting<Integer> y;
   private boolean armed = true;
   private boolean keyDown;
   private int cooldown;

   public Trigger(SettingGroup group, String what, int defaultY) {
      this.mode = group.add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description(
                     "Key: it stays on and "
                        + what
                        + " each time you press the key below. Below Y: it stays on and "
                        + what
                        + " each time you go down to the height below."
                  ))
               .defaultValue(Trigger.Mode.Key))
            .build()
      );
      this.key = group.add(
         new KeybindSetting.Builder()
            .name("key")
            .description("Press this to run it. Not the module's own bind - that turns the module on and off.")
            .defaultValue(Keybind.none())
            .visible(() -> this.mode.get() == Trigger.Mode.Key)
            .build()
      );
      this.y = group.add(
         new IntSetting.Builder()
            .name("y")
            .description("Runs when your feet are at or below this height. It is ready again once you are 2 blocks above it.")
            .defaultValue(defaultY)
            .range(-64, 320)
            .sliderRange(-64, 128)
            .visible(() -> this.mode.get() == Trigger.Mode.BelowY)
            .build()
      );
   }

   public Trigger.Mode mode() {
      return this.mode.get();
   }

   public int y() {
      return this.y.get();
   }

   public String waitingLine() {
      if (this.mode.get() == Trigger.Mode.BelowY) {
         return "On - it runs each time you go down to Y " + this.y.get() + ".";
      } else {
         return this.key.get().isSet() ? "On - press " + this.key.get() + " to run it." : "On, but no key is set - pick one in its settings.";
      }
   }

   public void reset() {
      this.armed = true;
      this.keyDown = true;
      this.cooldown = 0;
   }

   public void finished() {
      this.armed = false;
      this.cooldown = 400;
   }

   public boolean poll(Minecraft mc) {
      if (this.mode.get() != Trigger.Mode.Key) {
         if (mc.player == null || mc.level == null || mc.player.tickCount < 40) {
            return false;
         } else if (this.cooldown > 0) {
            this.cooldown--;
            return false;
         } else {
            int feet = mc.player.getBlockY();
            if (!this.armed) {
               if (feet >= this.y.get() + 2) {
                  this.armed = true;
               }

               return false;
            } else if (feet > this.y.get()) {
               return false;
            } else {
               this.armed = false;
               return true;
            }
         }
      } else {
         boolean down = this.key.get().isSet() && this.key.get().isPressed();
         boolean pressed = down && !this.keyDown && mc.gui.screen() == null;
         this.keyDown = down;
         return pressed && mc.player != null;
      }
   }

   public static enum Mode {
      Key("Key"),
      BelowY("Below Y");

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

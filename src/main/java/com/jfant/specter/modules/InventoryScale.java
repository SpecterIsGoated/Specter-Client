package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class InventoryScale extends Module {
   private static InventoryScale instance;
   public static boolean drawing;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> scale = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("scale")
            .description("GUI scale for the inventory, like the vanilla GUI Scale option. Capped at the largest scale your window fits.")
            .defaultValue(2)
            .range(1, 8)
            .sliderRange(1, 6)
            .onChanged(value -> this.refreshScreen())
            .build()
      );
   private final Setting<Boolean> containers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("containers")
            .description("Also use this scale for chests, shulkers, furnaces and every other container screen.")
            .defaultValue(false)
            .onChanged(value -> this.refreshScreen())
            .build()
      );

   public InventoryScale() {
      super(SpecterClientAddon.HUD, "inventory-scale", "Gives the inventory its own GUI scale, separate from the rest of the game.");
      instance = this;
   }

   @Override
   public void onActivate() {
      this.refreshScreen();
   }

   @Override
   public void onDeactivate() {
      this.refreshScreen();
   }

   private void refreshScreen() {
      if (this.mc != null && this.mc.screen != null) {
         Window window = this.mc.getWindow();
         this.mc.screen.resize(window.getGuiScaledWidth(), window.getGuiScaledHeight());
      }
   }

   public static boolean appliesTo(Screen screen) {
      if (instance == null || !instance.isActive() || screen == null) {
         return false;
      } else {
         return !(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)
            ? instance.containers.get() && screen instanceof AbstractContainerScreen
            : true;
      }
   }

   private static int targetScale() {
      Minecraft mc = Minecraft.getInstance();
      int max = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
      return Math.min(instance.scale.get(), max);
   }

   public static float ratio(Screen screen) {
      return !appliesTo(screen) ? 1.0F : (float)targetScale() / Minecraft.getInstance().getWindow().getGuiScale();
   }

   public static int width() {
      return divideUp(Minecraft.getInstance().getWindow().getWidth(), targetScale());
   }

   public static int height() {
      return divideUp(Minecraft.getInstance().getWindow().getHeight(), targetScale());
   }

   private static int divideUp(int size, int scale) {
      return (size + scale - 1) / scale;
   }
}

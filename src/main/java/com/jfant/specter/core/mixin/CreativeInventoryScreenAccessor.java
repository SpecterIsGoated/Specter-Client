package com.jfant.specter.core.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({CreativeModeInventoryScreen.class})
public interface CreativeInventoryScreenAccessor {
   @Accessor("selectedTab")
   static CreativeModeTab meteor$getSelectedTab() {
      return null;
   }
}

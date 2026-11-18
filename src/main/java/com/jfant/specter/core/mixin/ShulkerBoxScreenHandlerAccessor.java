package com.jfant.specter.core.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ShulkerBoxMenu.class})
public interface ShulkerBoxScreenHandlerAccessor {
   @Accessor("container")
   Container meteor$getInventory();
}

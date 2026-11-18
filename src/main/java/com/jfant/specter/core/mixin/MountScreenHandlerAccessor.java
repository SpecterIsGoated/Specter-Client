package com.jfant.specter.core.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractMountInventoryMenu.class})
public interface MountScreenHandlerAccessor {
   @Accessor("mount")
   LivingEntity meteor$getMount();
}

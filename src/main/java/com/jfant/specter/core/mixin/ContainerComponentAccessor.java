package com.jfant.specter.core.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ItemContainerContents.class})
public interface ContainerComponentAccessor {
   @Accessor("items")
   NonNullList<ItemStack> meteor$getStacks();
}

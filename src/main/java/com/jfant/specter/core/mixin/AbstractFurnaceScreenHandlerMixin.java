package com.jfant.specter.core.mixin;

import com.jfant.specter.core.mixininterface.IAbstractFurnaceScreenHandler;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({AbstractFurnaceMenu.class})
public abstract class AbstractFurnaceScreenHandlerMixin implements IAbstractFurnaceScreenHandler {
   @Shadow
   protected abstract boolean canSmelt(ItemStack var1);

   @Override
   public boolean meteor$isItemSmeltable(ItemStack itemStack) {
      return this.canSmelt(itemStack);
   }
}

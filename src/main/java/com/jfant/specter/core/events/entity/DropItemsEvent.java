package com.jfant.specter.core.events.entity;

import com.jfant.specter.core.events.Cancellable;
import net.minecraft.world.item.ItemStack;

public class DropItemsEvent extends Cancellable {
   private static final DropItemsEvent INSTANCE = new DropItemsEvent();
   public ItemStack itemStack;

   public static DropItemsEvent get(ItemStack itemStack) {
      INSTANCE.setCancelled(false);
      INSTANCE.itemStack = itemStack;
      return INSTANCE;
   }
}

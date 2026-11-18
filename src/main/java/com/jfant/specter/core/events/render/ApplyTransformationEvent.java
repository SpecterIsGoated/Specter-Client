package com.jfant.specter.core.events.render;

import com.jfant.specter.core.events.Cancellable;
import net.minecraft.client.renderer.block.model.ItemTransform;

public class ApplyTransformationEvent extends Cancellable {
   private static final ApplyTransformationEvent INSTANCE = new ApplyTransformationEvent();
   public ItemTransform transformation;
   public boolean leftHanded;

   public static ApplyTransformationEvent get(ItemTransform transformation, boolean leftHanded) {
      INSTANCE.setCancelled(false);
      INSTANCE.transformation = transformation;
      INSTANCE.leftHanded = leftHanded;
      return INSTANCE;
   }
}

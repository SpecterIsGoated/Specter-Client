package com.jfant.specter.core.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ItemStackRenderState.class})
public interface ItemRenderStateAccessor {
   @Accessor("activeLayerCount")
   int meteor$getLayerCount();

   @Accessor("layers")
   LayerRenderState[] meteor$getLayers();
}

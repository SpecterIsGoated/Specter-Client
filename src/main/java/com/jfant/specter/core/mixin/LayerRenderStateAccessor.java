package com.jfant.specter.core.mixin;

import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LayerRenderState.class})
public interface LayerRenderStateAccessor {
   @Accessor("transform")
   ItemTransform meteor$getTransform();
}

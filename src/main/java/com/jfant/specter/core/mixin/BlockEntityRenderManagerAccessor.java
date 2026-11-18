package com.jfant.specter.core.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.resources.model.MaterialSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({BlockEntityRenderDispatcher.class})
public interface BlockEntityRenderManagerAccessor {
   @Accessor("materials")
   MaterialSet getSpriteHolder();
}

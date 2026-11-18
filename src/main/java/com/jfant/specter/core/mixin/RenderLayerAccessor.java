package com.jfant.specter.core.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RenderType.class})
public interface RenderLayerAccessor {
   @Accessor("state")
   RenderSetup getRenderSetup();
}

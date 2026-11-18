package com.jfant.specter.core.mixin;

import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({CachedOrthoProjectionMatrixBuffer.class})
public interface ProjectionMatrix2Accessor {
   @Invoker("createProjectionMatrix")
   Matrix4f meteor$callGetMatrix(float var1, float var2);
}

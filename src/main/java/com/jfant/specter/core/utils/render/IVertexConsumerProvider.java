package com.jfant.specter.core.utils.render;

import net.minecraft.client.renderer.MultiBufferSource;

public interface IVertexConsumerProvider extends MultiBufferSource {
   void setOffset(int var1, int var2, int var3);
}

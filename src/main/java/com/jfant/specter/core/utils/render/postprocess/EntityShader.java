package com.jfant.specter.core.utils.render.postprocess;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.mixininterface.IWorldRenderer;
import com.jfant.specter.core.utils.render.CustomOutlineVertexConsumerProvider;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.world.entity.Entity;

public abstract class EntityShader extends PostProcessShader {
   public final CustomOutlineVertexConsumerProvider vertexConsumerProvider = new CustomOutlineVertexConsumerProvider();

   protected EntityShader(RenderPipeline pipeline) {
      super(pipeline);
   }

   public abstract boolean shouldDraw(Entity var1);

   @Override
   protected void preDraw() {
      ((IWorldRenderer)SpecterCore.mc.levelRenderer).meteor$pushEntityOutlineFramebuffer(this.framebuffer);
   }

   @Override
   protected void postDraw() {
      ((IWorldRenderer)SpecterCore.mc.levelRenderer).meteor$popEntityOutlineFramebuffer();
   }

   public void submitVertices() {
      this.submitVertices(this.vertexConsumerProvider::draw);
   }
}

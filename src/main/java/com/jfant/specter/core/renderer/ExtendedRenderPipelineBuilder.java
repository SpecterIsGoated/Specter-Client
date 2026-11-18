package com.jfant.specter.core.renderer;

import com.jfant.specter.core.mixininterface.IRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;

public class ExtendedRenderPipelineBuilder extends Builder {
   private boolean lineSmooth;

   public ExtendedRenderPipelineBuilder(Snippet... snippets) {
      for (Snippet snippet : snippets) {
         this.withSnippet(snippet);
      }
   }

   public ExtendedRenderPipelineBuilder withLineSmooth() {
      this.lineSmooth = true;
      return this;
   }

   @Override
   public RenderPipeline build() {
      RenderPipeline pipeline = super.build();
      ((IRenderPipeline)pipeline).meteor$setLineSmooth(this.lineSmooth);
      return pipeline;
   }
}

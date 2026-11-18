package com.jfant.specter.core.mixininterface;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface IWorldRenderer {
   void meteor$pushEntityOutlineFramebuffer(RenderTarget var1);

   void meteor$popEntityOutlineFramebuffer();
}

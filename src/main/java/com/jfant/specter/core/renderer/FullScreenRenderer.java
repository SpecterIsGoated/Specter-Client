package com.jfant.specter.core.renderer;

import com.jfant.specter.core.utils.PreInit;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

public class FullScreenRenderer {
   public static GpuBuffer vbo;
   public static GpuBuffer ibo;
   @Deprecated(
      forRemoval = true
   )
   public static MeshBuilder mesh;

   private FullScreenRenderer() {
   }

   @PreInit
   public static void init() {
      mesh = new MeshBuilder(MeteorVertexFormats.POS2, Mode.TRIANGLES, 4, 6);
      mesh.begin();
      mesh.quad(mesh.vec2(-1.0, -1.0).next(), mesh.vec2(-1.0, 1.0).next(), mesh.vec2(1.0, 1.0).next(), mesh.vec2(1.0, -1.0).next());
      mesh.end();
      vbo = mesh.getVertexBuffer();
      ibo = mesh.getIndexBuffer();
   }
}

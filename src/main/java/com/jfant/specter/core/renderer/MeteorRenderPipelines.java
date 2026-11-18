package com.jfant.specter.core.renderer;

import com.jfant.specter.core.SpecterCore;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.io.IOUtils;

public abstract class MeteorRenderPipelines {
   private static final List<RenderPipeline> PIPELINES = new ArrayList<>();
   private static final Snippet MESH_UNIFORMS = RenderPipeline.builder().withUniform("MeshData", UniformType.UNIFORM_BUFFER).buildSnippet();
   public static final RenderPipeline WORLD_COLORED = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/world_colored"))
         .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/pos_color.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline WORLD_COLORED_LINES = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLineSmooth()
         .withLocation(SpecterCore.identifier("pipeline/world_colored_lines"))
         .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.DEBUG_LINES)
         .withVertexShader(SpecterCore.identifier("shaders/pos_color.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline WORLD_COLORED_DEPTH = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/world_colored_depth"))
         .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/pos_color.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline WORLD_COLORED_LINES_DEPTH = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLineSmooth()
         .withLocation(SpecterCore.identifier("pipeline/world_colored_lines_depth"))
         .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.DEBUG_LINES)
         .withVertexShader(SpecterCore.identifier("shaders/pos_color.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline UI_COLORED = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/ui_colored"))
         .withVertexFormat(MeteorVertexFormats.POS2_COLOR, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/pos_color.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline UI_COLORED_LINES = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/ui_colored_lines"))
         .withVertexFormat(MeteorVertexFormats.POS2_COLOR, Mode.DEBUG_LINES)
         .withVertexShader(SpecterCore.identifier("shaders/pos_color.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/pos_color.frag"))
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline UI_TEXTURED = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/ui_textured"))
         .withVertexFormat(MeteorVertexFormats.POS2_TEXTURE_COLOR, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/pos_tex_color.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/pos_tex_color.frag"))
         .withSampler("u_Texture")
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline UI_TEXT = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/ui_text"))
         .withVertexFormat(MeteorVertexFormats.POS2_TEXTURE_COLOR, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/text.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/text.frag"))
         .withSampler("u_Texture")
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(true)
         .build()
   );
   public static final RenderPipeline POST_OUTLINE = add(
      new ExtendedRenderPipelineBuilder()
         .withLocation(SpecterCore.identifier("pipeline/post/outline"))
         .withVertexFormat(MeteorVertexFormats.POS2, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/post-process/base.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/post-process/outline.frag"))
         .withSampler("u_Texture")
         .withUniform("PostData", UniformType.UNIFORM_BUFFER)
         .withUniform("OutlineData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline POST_IMAGE = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/post/image"))
         .withVertexFormat(MeteorVertexFormats.POS2, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/post-process/base.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/post-process/image.frag"))
         .withSampler("u_Texture")
         .withSampler("u_TextureI")
         .withUniform("PostData", UniformType.UNIFORM_BUFFER)
         .withUniform("ImageData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline BLUR_DOWN = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/blur/down"))
         .withVertexFormat(MeteorVertexFormats.POS2, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/blur.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/blur_down.frag"))
         .withSampler("u_Texture")
         .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline BLUR_UP = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/blur/up"))
         .withVertexFormat(MeteorVertexFormats.POS2, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/blur.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/blur_up.frag"))
         .withSampler("u_Texture")
         .withUniform("BlurData", UniformType.UNIFORM_BUFFER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );
   public static final RenderPipeline BLUR_PASSTHROUGH = add(
      new ExtendedRenderPipelineBuilder(MESH_UNIFORMS)
         .withLocation(SpecterCore.identifier("pipeline/blur/up"))
         .withVertexFormat(MeteorVertexFormats.POS2, Mode.TRIANGLES)
         .withVertexShader(SpecterCore.identifier("shaders/passthrough.vert"))
         .withFragmentShader(SpecterCore.identifier("shaders/passthrough.frag"))
         .withSampler("u_Texture")
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withCull(false)
         .build()
   );

   private static RenderPipeline add(RenderPipeline pipeline) {
      PIPELINES.add(pipeline);
      return pipeline;
   }

   public static void precompile() {
      GpuDevice device = RenderSystem.getDevice();
      ResourceManager resources = Minecraft.getInstance().getResourceManager();

      for (RenderPipeline pipeline : PIPELINES) {
         device.precompilePipeline(pipeline, (identifier, shaderType) -> {
            Resource resource = resources.getResource(identifier).get();

            try {
               String var5;
               try (InputStream in = resource.open()) {
                  var5 = IOUtils.toString(in, StandardCharsets.UTF_8);
               }

               return var5;
            } catch (IOException var9) {
               throw new RuntimeException(var9);
            }
         });
      }
   }

   private MeteorRenderPipelines() {
   }
}

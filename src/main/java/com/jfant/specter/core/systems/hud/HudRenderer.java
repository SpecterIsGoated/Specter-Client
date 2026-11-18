package com.jfant.specter.core.systems.hud;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.meteor.CustomFontChangedEvent;
import com.jfant.specter.core.renderer.Fonts;
import com.jfant.specter.core.renderer.MeshBuilder;
import com.jfant.specter.core.renderer.MeshRenderer;
import com.jfant.specter.core.renderer.MeteorRenderPipelines;
import com.jfant.specter.core.renderer.Renderer2D;
import com.jfant.specter.core.renderer.text.CustomTextRenderer;
import com.jfant.specter.core.renderer.text.Font;
import com.jfant.specter.core.renderer.text.VanillaTextRenderer;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.render.RenderUtils;
import com.jfant.specter.core.utils.render.color.Color;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class HudRenderer {
   public static final HudRenderer INSTANCE = new HudRenderer();
   private static final double SCALE_TO_HEIGHT = 0.05555555555555555;
   private final Hud hud = Hud.get();
   private final List<Runnable> postTasks = new ArrayList<>();
   private final Int2ObjectMap<HudRenderer.FontHolder> fontsInUse = new Int2ObjectOpenHashMap();
   private final LoadingCache<Integer, HudRenderer.FontHolder> fontCache = CacheBuilder.newBuilder()
      .maximumSize(4L)
      .expireAfterAccess(Duration.ofMinutes(10L))
      .removalListener(notification -> {
         if (notification.wasEvicted()) {
            ((HudRenderer.FontHolder)notification.getValue()).destroy();
         }
      })
      .build(CacheLoader.from(HudRenderer::loadFont));
   public GuiGraphics drawContext;
   public double delta;

   private HudRenderer() {
      SpecterCore.EVENT_BUS.subscribe(this);
   }

   public void begin(GuiGraphics drawContext) {
      Renderer2D.COLOR.begin();
      this.drawContext = drawContext;
      this.delta = Utils.frameTime;
      drawContext.nextStratum();
      if (!this.hud.hasCustomFont()) {
         VanillaTextRenderer.INSTANCE.scaleIndividually = true;
         VanillaTextRenderer.INSTANCE.begin();
      }
   }

   public void end() {
      Renderer2D.COLOR.render();
      if (this.hud.hasCustomFont()) {
         Iterator<HudRenderer.FontHolder> it = this.fontsInUse.values().iterator();

         while (it.hasNext()) {
            HudRenderer.FontHolder fontHolder = it.next();
            if (fontHolder.visited) {
               MeshRenderer.begin()
                  .attachments(SpecterCore.mc.getMainRenderTarget())
                  .pipeline(MeteorRenderPipelines.UI_TEXT)
                  .mesh(fontHolder.getMesh())
                  .sampler("u_Texture", fontHolder.font.texture.getTextureView(), fontHolder.font.texture.getSampler())
                  .end();
            } else {
               it.remove();
               this.fontCache.put(fontHolder.font.getHeight(), fontHolder);
            }

            fontHolder.visited = false;
         }
      } else {
         VanillaTextRenderer.INSTANCE.end();
         VanillaTextRenderer.INSTANCE.scaleIndividually = false;
      }

      for (Runnable task : this.postTasks) {
         task.run();
      }

      this.postTasks.clear();
      this.drawContext.nextStratum();
      this.drawContext = null;
   }

   public void line(double x1, double y1, double x2, double y2, Color color) {
      Renderer2D.COLOR.line(x1, y1, x2, y2, color);
   }

   public void quad(double x, double y, double width, double height, Color color) {
      Renderer2D.COLOR.quad(x, y, width, height, color);
   }

   public void quad(double x, double y, double width, double height, Color cTopLeft, Color cTopRight, Color cBottomRight, Color cBottomLeft) {
      Renderer2D.COLOR.quad(x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
   }

   public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
      Renderer2D.COLOR.triangle(x1, y1, x2, y2, x3, y3, color);
   }

   public void texture(Identifier id, double x, double y, double width, double height, Color color) {
      Renderer2D.TEXTURE.begin();
      Renderer2D.TEXTURE.texQuad(x, y, width, height, color);
      Renderer2D.TEXTURE
         .render(SpecterCore.mc.getTextureManager().getTexture(id).getTextureView(), SpecterCore.mc.getTextureManager().getTexture(id).getSampler());
   }

   public double text(String text, double x, double y, Color color, boolean shadow, double scale) {
      if (scale == -1.0) {
         scale = this.hud.getTextScale();
      }

      if (!this.hud.hasCustomFont()) {
         VanillaTextRenderer.INSTANCE.scale = scale * 2.0;
         return VanillaTextRenderer.INSTANCE.render(text, x, y, color, shadow);
      } else {
         HudRenderer.FontHolder fontHolder = this.getFontHolder(scale, true);
         Font font = fontHolder.font;
         MeshBuilder mesh = fontHolder.getMesh();
         double width;
         if (shadow) {
            int preShadowA = CustomTextRenderer.SHADOW_COLOR.a;
            CustomTextRenderer.SHADOW_COLOR.a = (int)(color.a / 255.0 * preShadowA);
            width = font.render(mesh, text, x + 1.0, y + 1.0, CustomTextRenderer.SHADOW_COLOR, scale);
            font.render(mesh, text, x, y, color, scale);
            CustomTextRenderer.SHADOW_COLOR.a = preShadowA;
         } else {
            width = font.render(mesh, text, x, y, color, scale);
         }

         return width;
      }
   }

   public double text(String text, double x, double y, Color color, boolean shadow) {
      return this.text(text, x, y, color, shadow, -1.0);
   }

   public double textWidth(String text, boolean shadow, double scale) {
      if (text.isEmpty()) {
         return 0.0;
      } else if (this.hud.hasCustomFont()) {
         double width = this.getFont(scale).getWidth(text, text.length());
         return (width + (shadow ? 1 : 0)) * (scale == -1.0 ? this.hud.getTextScale() : scale) + (shadow ? 1 : 0);
      } else {
         VanillaTextRenderer.INSTANCE.scale = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return VanillaTextRenderer.INSTANCE.getWidth(text, shadow);
      }
   }

   public double textWidth(String text, boolean shadow) {
      return this.textWidth(text, shadow, -1.0);
   }

   public double textWidth(String text, double scale) {
      return this.textWidth(text, false, scale);
   }

   public double textWidth(String text) {
      return this.textWidth(text, false, -1.0);
   }

   public double textHeight(boolean shadow, double scale) {
      if (this.hud.hasCustomFont()) {
         double height = this.getFont(scale).getHeight() + 1;
         return (height + (shadow ? 1 : 0)) * (scale == -1.0 ? this.hud.getTextScale() : scale);
      } else {
         VanillaTextRenderer.INSTANCE.scale = (scale == -1.0 ? this.hud.getTextScale() : scale) * 2.0;
         return VanillaTextRenderer.INSTANCE.getHeight(shadow);
      }
   }

   public double textHeight(boolean shadow) {
      return this.textHeight(shadow, -1.0);
   }

   public double textHeight() {
      return this.textHeight(false, -1.0);
   }

   public void post(Runnable task) {
      this.postTasks.add(task);
   }

   public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay, String countOverlay) {
      RenderUtils.drawItem(this.drawContext, itemStack, x, y, scale, overlay, countOverlay, true);
   }

   public void item(ItemStack itemStack, int x, int y, float scale, boolean overlay) {
      RenderUtils.drawItem(this.drawContext, itemStack, x, y, scale, overlay);
   }

   public void entity(LivingEntity entity, int x, int y, int width, int height, float yaw, float pitch) {
      float previousBodyYaw = entity.yBodyRot;
      float previousYaw = entity.getYRot();
      float previousPitch = entity.getXRot();
      float lastLastHeadYaw = entity.yHeadRotO;
      float lastHeadYaw = entity.yHeadRot;
      float tanYaw = (float)Math.atan(yaw / 40.0F);
      float tanPitch = (float)Math.atan(pitch / 40.0F);
      entity.yBodyRot = 180.0F + tanYaw * 20.0F;
      entity.setYRot(180.0F + tanYaw * 40.0F);
      entity.setXRot(-tanPitch * 20.0F);
      entity.yHeadRot = entity.getYRot();
      entity.yHeadRotO = entity.getYRot();
      LivingEntityRenderState state = (LivingEntityRenderState)SpecterCore.mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1.0F);
      entity.yBodyRot = previousBodyYaw;
      entity.setYRot(previousYaw);
      entity.setXRot(previousPitch);
      entity.yHeadRotO = lastLastHeadYaw;
      entity.yHeadRot = lastHeadYaw;
      float s = 1.0F / SpecterCore.mc.getWindow().getGuiScale();
      int x1 = (int)(x * s);
      int y1 = (int)(y * s);
      int x2 = (int)((x + width) * s);
      int y2 = (int)((y + height) * s);
      float scale = Math.max(width, height) * s / 2.0F;
      Vector3f translation = new Vector3f(0.0F, 1.0F, 0.0F);
      Quaternionf rotation = new Quaternionf().rotateZ(3.1415927F);
      this.drawContext.submitEntityRenderState(state, scale, translation, rotation, null, x1, y1, x2, y2);
   }

   private HudRenderer.FontHolder getFontHolder(double scale, boolean render) {
      if (scale == -1.0) {
         scale = this.hud.getTextScale();
      }

      int height = (int)Math.round(scale / 0.05555555555555555);
      HudRenderer.FontHolder fontHolder = (HudRenderer.FontHolder)this.fontsInUse.get(height);
      if (fontHolder != null) {
         if (render) {
            fontHolder.visited = true;
         }

         return fontHolder;
      } else if (render) {
         fontHolder = this.fontCache.getIfPresent(height);
         if (fontHolder == null) {
            fontHolder = loadFont(height);
         } else {
            this.fontCache.invalidate(height);
         }

         this.fontsInUse.put(height, fontHolder);
         fontHolder.visited = true;
         return fontHolder;
      } else {
         return this.fontCache.getUnchecked(height);
      }
   }

   private Font getFont(double scale) {
      return this.getFontHolder(scale, false).font;
   }

   @EventHandler
   private void onCustomFontChanged(CustomFontChangedEvent event) {
      ObjectIterator var2 = this.fontsInUse.values().iterator();

      while (var2.hasNext()) {
         HudRenderer.FontHolder fontHolder = (HudRenderer.FontHolder)var2.next();
         fontHolder.destroy();
      }

      for (HudRenderer.FontHolder fontHolder : this.fontCache.asMap().values()) {
         fontHolder.destroy();
      }

      this.fontsInUse.clear();
      this.fontCache.invalidateAll();
   }

   private static HudRenderer.FontHolder loadFont(int height) {
      try {
         ByteBuffer buffer = Fonts.RENDERER.fontFace.readToDirectByteBuffer();
         return new HudRenderer.FontHolder(new Font(buffer, height));
      } catch (IOException var2) {
         throw new RuntimeException("Failed to load font: " + Fonts.RENDERER.fontFace, var2);
      }
   }

   private static class FontHolder {
      public final Font font;
      public boolean visited;
      private MeshBuilder mesh;

      public FontHolder(Font font) {
         this.font = font;
      }

      public MeshBuilder getMesh() {
         if (this.mesh == null) {
            this.mesh = new MeshBuilder(MeteorRenderPipelines.UI_TEXT);
         }

         if (!this.mesh.isBuilding()) {
            this.mesh.begin();
         }

         return this.mesh;
      }

      public void destroy() {
         this.font.texture.close();
      }
   }
}

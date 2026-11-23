package com.jfant.specter.phone.apps.maps;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.utils.render.color.Color;
import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.gfx.IconRaster;
import com.jfant.specter.phone.gfx.PhoneTextures;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.QuadBatch;
import com.jfant.specter.render.Shapes;
import java.util.function.Consumer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Turn-by-turn for Donut Maps: a light beam over the destination (kept in sight even past render distance), a pill
 * at the top of the screen with an arrow and the distance, and an "arrived" callback within a few blocks.
 */
public final class MapsNavigation {
   private static final MapsNavigation INSTANCE = new MapsNavigation();
   private static boolean subscribed;
   @Nullable
   private Waypoint target;
   private String targetWorld;
   private Consumer<Waypoint> arrived = w -> {
   };
   private final Color side = new Color();
   private final Color top = new Color();

   private MapsNavigation() {
   }

   public static MapsNavigation get() {
      if (!subscribed) {
         subscribed = true;
         SpecterCore.EVENT_BUS.subscribe(INSTANCE);
      }
      return INSTANCE;
   }

   public void start(Waypoint w, String world, Consumer<Waypoint> onArrive) {
      this.target = w;
      this.targetWorld = world;
      this.arrived = onArrive;
   }

   public void stop() {
      this.target = null;
   }

   @Nullable
   public Waypoint target() {
      return this.target;
   }

   private boolean here(Minecraft mc) {
      return this.target != null && mc.level != null && mc.player != null && MapsApp.worldKey().equals(this.targetWorld)
         && mc.level.dimension().identifier().getPath().equals(this.target.dim);
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (!this.here(mc)) return;
      Waypoint w = this.target;
      Vec3 eye = mc.gameRenderer.mainCamera().position();
      double x = w.x + 0.5, z = w.z + 0.5, y = w.y;
      double dx = x - eye.x, dz = z - eye.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      if (dist < 5) {
         this.target = null;
         this.arrived.accept(w);
         return;
      }
      double far = 140;
      if (dist > far) {
         x = eye.x + dx / dist * far;
         z = eye.z + dz / dist * far;
         y = eye.y - 20;
      }
      int c = w.category.color;
      this.side.set(c >> 16 & 255, c >> 8 & 255, c & 255, 150);
      this.top.set(c >> 16 & 255, c >> 8 & 255, c & 255, 0);
      double width = 0.35 + Math.min(dist, far) * 0.004;
      event.renderer.gradientQuadVertical(x - width, y, z, x + width, y + 220, z, this.top, this.side);
      event.renderer.gradientQuadVertical(x, y, z - width, x, y + 220, z + width, this.top, this.side);
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (!this.here(mc) || PhoneOS.get().isOut()) return;
      Waypoint w = this.target;
      double dx = w.x + 0.5 - mc.player.getX(), dz = w.z + 0.5 - mc.player.getZ();
      int dist = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
      // angle of the target relative to where you face (0 = straight ahead, clockwise)
      double bearing = Math.toDegrees(Math.atan2(-dx, dz));
      double rel = ((bearing - mc.player.getYRot()) % 360 + 540) % 360 - 180;
      GuiGraphicsExtractor ctx = event.graphics;
      String text = w.name + "  " + (dist >= 1000 ? String.format("%.1f km", dist / 1000.0) : dist + " m");
      CardText.pushSmooth();
      try {
         float tw = CardText.width(text);
         float pw = tw + 30, ph = 16, px = event.screenWidth / 2.0F - pw / 2, py = 6;
         Shapes.fill(ctx, px, py, px + pw, py + ph, 8, 15, 0xE6141019, 0xE6141019);
         int step = (int)Math.round((rel + 360) % 360 / 11.25) % 32;
         int size = Math.max(8, Math.round(12 * Minecraft.getInstance().getWindow().getGuiScale()));
         Identifier arrow = PhoneTextures.paint("nav/" + step, size, size, r -> paintArrow(r, step * 11.25));
         QuadBatch b = QuadBatch.textured(ctx, arrow);
         if (b != null) {
            b.textured(px + 4, py + 2, px + 16, py + 14, 0, 0, 1, 1, w.category.color, w.category.color);
            b.submit(ctx);
         }
         CardText.draw(ctx, text, px + 21, py + 4, 0xFFFFFFFF, false);
      } finally {
         CardText.popSmooth();
      }
   }

   private static void paintArrow(IconRaster r, double degrees) {
      double a = Math.toRadians(degrees);
      float c = (float)Math.cos(a), s = (float)Math.sin(a);
      float[] pts = {0.5F, 0.1F, 0.82F, 0.86F, 0.5F, 0.68F, 0.18F, 0.86F};
      float[] out = new float[8];
      for (int i = 0; i < 4; i++) {
         float x = pts[i * 2] - 0.5F, y = pts[i * 2 + 1] - 0.5F;
         out[i * 2] = 0.5F + x * c - y * s;
         out[i * 2 + 1] = 0.5F + x * s + y * c;
      }
      r.fill(IconRaster.polygon(out), 0xFFFFFFFF);
   }
}

package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class Crosshair extends Module {
   private static Crosshair instance;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgFeedback = this.settings.createGroup("Feedback");
   private final Setting<Crosshair.Style> style = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("style"))
                  .description("Cross: a + with a gap. Dot + ring: a dot in a ring that shows your attack cooldown. Chevron: a sleek V."))
               .defaultValue(Crosshair.Style.Cross))
            .build()
      );
   private final Setting<Integer> size = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("size")
            .description("Arm length (Cross, Chevron) or ring size, in GUI pixels.")
            .defaultValue(4)
            .range(1, 20)
            .sliderRange(1, 12)
            .build()
      );
   private final Setting<Integer> gap = this.sgGeneral
      .add(new IntSetting.Builder().name("gap").description("Space left in the middle, in GUI pixels.").defaultValue(2).range(0, 12).sliderRange(0, 8).build());
   private final Setting<Integer> thickness = this.sgGeneral
      .add(new IntSetting.Builder().name("thickness").description("Line thickness, in GUI pixels.").defaultValue(1).range(1, 4).sliderRange(1, 4).build());
   private final Setting<Boolean> dot = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("center-dot")
            .description("A dot in the middle of the Cross and Chevron too.")
            .defaultValue(false)
            .visible(() -> this.style.get() != Crosshair.Style.DotRing)
            .build()
      );
   private final Setting<Boolean> outline = this.sgGeneral
      .add(new BoolSetting.Builder().name("outline").description("A thin dark edge so it shows on snow and sky.").defaultValue(true).build());
   private final Setting<SettingColor> color = this.sgColors
      .add(new ColorSetting.Builder().name("color").description("The crosshair's colour.").defaultValue(new SettingColor(255, 255, 255, 235)).build());
   private final Setting<SettingColor> targetColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("target-color")
            .description("The colour while you aim at a player.")
            .defaultValue(new SettingColor(255, 110, 199, 255))
            .build()
      );
   private final Setting<SettingColor> hitColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("hit-color")
            .description("The colour it flashes when you hit something.")
            .defaultValue(new SettingColor(255, 59, 59, 255))
            .build()
      );
   private final Setting<Boolean> dynamic = this.sgFeedback
      .add(
         new BoolSetting.Builder()
            .name("dynamic")
            .description("Spread out while you move, jump or fall, and pull back in when you stand still.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> cooldown = this.sgFeedback
      .add(
         new BoolSetting.Builder()
            .name("attack-cooldown")
            .description("Show your attack cooldown: the ring fills (Dot + ring), or a thin bar under it (Cross, Chevron).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> hitMarker = this.sgFeedback
      .add(new BoolSetting.Builder().name("hit-marker").description("Flash the hit colour and an X when you hit something.").defaultValue(true).build());
   private final Setting<Integer> hitTime = this.sgFeedback
      .add(
         new IntSetting.Builder()
            .name("hit-time")
            .description("How long the hit flash lasts, in milliseconds.")
            .defaultValue(220)
            .range(50, 1000)
            .sliderRange(50, 600)
            .visible(this.hitMarker::get)
            .build()
      );
   private long hitAt;
   private double spread;

   public Crosshair() {
      super(SpecterClientAddon.VISUALS, "crosshair", "A clean custom crosshair: cross, dot + ring or chevron, with spread, attack cooldown and a hit flash.");
      instance = this;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (event.entity instanceof LivingEntity) {
         this.hitAt = System.currentTimeMillis();
      }
   }

   public static boolean replaces() {
      return instance != null && instance.isActive();
   }

   public static void draw(GuiGraphicsExtractor ctx, float tickProgress) {
      instance.render(ctx, tickProgress);
   }

   private void render(GuiGraphicsExtractor ctx, float tickProgress) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.gameMode != null && client.options.getCameraType().isFirstPerson()) {
         if (client.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            int cx = ctx.guiWidth() / 2;
            int cy = ctx.guiHeight() / 2;
            long sinceHit = System.currentTimeMillis() - this.hitAt;
            boolean hit = this.hitMarker.get() && sinceHit < this.hitTime.get().intValue();
            int c = argb(this.color.get());
            if (client.crosshairPickEntity instanceof Player) {
               c = argb(this.targetColor.get());
            }

            if (hit) {
               c = argb(this.hitColor.get());
            }

            int s = this.size.get();
            int g = this.gap.get() + this.spreadNow(client);
            int t = this.thickness.get();
            float charge = client.player.getAttackStrengthScale(0.0F);
            switch ((Crosshair.Style)this.style.get()) {
               case Cross: {
                  int half = t / 2;
                  this.rect(ctx, cx - g - s, cy - half, s, t, c);
                  this.rect(ctx, cx + g + (t & 1), cy - half, s, t, c);
                  this.rect(ctx, cx - half, cy - g - s, t, s, c);
                  this.rect(ctx, cx - half, cy + g + (t & 1), t, s, c);
                  if (this.dot.get()) {
                     this.rect(ctx, cx - half, cy - half, t, t, c);
                  }

                  if (this.cooldown.get()) {
                     this.cooldownBar(ctx, cx, cy + g + s + 3, charge);
                  }
                  break;
               }
               case DotRing: {
                  int half = t / 2;
                  this.rect(ctx, cx - half, cy - half, Math.max(1, t), Math.max(1, t), c);
                  int radius = g + s;
                  this.ring(ctx, cx, cy, radius, t, dim(c), 1.0F);
                  this.ring(ctx, cx, cy, radius, t, c, this.cooldown.get() ? charge : 1.0F);
                  break;
               }
               case Chevron:
                  this.line(ctx, cx - s, cy + g - s + 1 + s / 2, cx, cy + g + s / 2, t, c);
                  this.line(ctx, cx + s, cy + g - s + 1 + s / 2, cx, cy + g + s / 2, t, c);
                  if (this.dot.get()) {
                     this.rect(ctx, cx - t / 2, cy - t / 2, t, t, c);
                  }

                  if (this.cooldown.get()) {
                     this.cooldownBar(ctx, cx, cy + g + s + 4, charge);
                  }
            }

            if (hit) {
               int in = g + 2;
               int out = in + Math.max(2, s / 2 + 1);
               this.line(ctx, cx - in, cy - in, cx - out, cy - out, 1, c);
               this.line(ctx, cx + in, cy - in, cx + out, cy - out, 1, c);
               this.line(ctx, cx - in, cy + in, cx - out, cy + out, 1, c);
               this.line(ctx, cx + in, cy + in, cx + out, cy + out, 1, c);
            }
         }
      }
   }

   private int spreadNow(Minecraft client) {
      double want = 0.0;
      if (this.dynamic.get()) {
         Vec3 v = client.player.getDeltaMovement();
         double speed = Math.sqrt(v.x * v.x + v.z * v.z);
         want = Math.min(4.0, speed * 12.0);
         if (!client.player.onGround()) {
            want = Math.min(5.0, want + 1.5);
         }

         if (client.player.isShiftKeyDown()) {
            want *= 0.5;
         }
      }

      this.spread = this.spread + (want - this.spread) * 0.25;
      return (int)Math.round(this.spread);
   }

   private void cooldownBar(GuiGraphicsExtractor ctx, int cx, int y, float charge) {
      if (!(charge >= 1.0F)) {
         int width = 10;
         int filled = Math.round(width * charge);
         this.rect(ctx, cx - width / 2, y, width, 1, -2147483648);
         this.rect(ctx, cx - width / 2, y, filled, 1, argb(this.color.get()));
      }
   }

   private void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
      if (w > 0 && h > 0) {
         if (this.outline.get()) {
            ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, outlineColor(c));
         }

         ctx.fill(x, y, x + w, y + h, c);
      }
   }

   private void line(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int t, int c) {
      if (this.outline.get()) {
         plot(ctx, x0, y0, x1, y1, t + 2, outlineColor(c));
      }

      plot(ctx, x0, y0, x1, y1, t, c);
   }

   private static void plot(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int t, int c) {
      int dx = Math.abs(x1 - x0);
      int dy = -Math.abs(y1 - y0);
      int sx = x0 < x1 ? 1 : -1;
      int sy = y0 < y1 ? 1 : -1;
      int err = dx + dy;
      int lead = -(t / 2);

      while (true) {
         ctx.fill(x0 + lead, y0 + lead, x0 + lead + t, y0 + lead + t, c);
         if (x0 == x1 && y0 == y1) {
            return;
         }

         int e2 = 2 * err;
         if (e2 >= dy) {
            err += dy;
            x0 += sx;
         }

         if (e2 <= dx) {
            err += dx;
            y0 += sy;
         }
      }
   }

   private void ring(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int t, int c, float part) {
      int steps = Math.max(24, radius * 8);
      int drawn = Math.round(steps * Mth.clamp(part, 0.0F, 1.0F));
      int lastX = -2147483648;
      int lastY = -2147483648;

      for (int i = 0; i < drawn; i++) {
         double a = i * 3.141592653589793 * 2.0 / steps;
         int x = cx + (int)Math.round(Math.sin(a) * radius);
         int y = cy - (int)Math.round(Math.cos(a) * radius);
         if (x != lastX || y != lastY) {
            lastX = x;
            lastY = y;
            ctx.fill(x - t / 2, y - t / 2, x - t / 2 + t, y - t / 2 + t, c);
         }
      }
   }

   private static int argb(SettingColor c) {
      return c.a << 24 | c.r << 16 | c.g << 8 | c.b;
   }

   private static int dim(int c) {
      return (c >>> 24) / 3 << 24 | c & 16777215;
   }

   private static int outlineColor(int c) {
      return (c >>> 24) * 150 / 255 << 24;
   }

   public static enum Style {
      Cross,
      DotRing("Dot + ring"),
      Chevron;

      private final String title;

      private Style() {
         this.title = this.name();
      }

      private Style(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}

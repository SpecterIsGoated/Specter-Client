package com.jfant.specter.phone.apps;

import com.jfant.specter.phone.api.AppIcon;
import com.jfant.specter.phone.gfx.IconRaster;
import java.util.Random;

/**
 * The house style for app icons: a rounded square with a soft two-colour gradient, a faint top highlight and a white
 * glyph with a gentle shadow. New apps can use {@link #tile} with their own glyph to match.
 */
public final class AppIcons {
   private AppIcons() {
   }

   private static final IconRaster.Sdf TILE = IconRaster.box(0.5F, 0.5F, 0.5F, 0.5F, 0.225F);

   /** Background tile: gradient from {@code top} (upper left) to {@code bottom} (lower right). */
   public static void tile(IconRaster r, int top, int bottom) {
      r.fill(TILE, IconRaster.linear(0.15F, 0.0F, 0.85F, 1.0F, top, bottom));
      r.fill(IconRaster.intersect(TILE, IconRaster.circle(0.5F, -0.55F, 1.0F)), 0x1CFFFFFF);
   }

   /** A white glyph with a soft shadow under it. */
   public static void glyph(IconRaster r, IconRaster.Sdf shape) {
      r.shadow(shape, 0, 0.02F, 0.05F, 0x40000000);
      r.fill(shape, 0xFFFFFFFF);
   }

   public static final AppIcon DONUTGRAM = AppIcon.painted("donutgram", r -> {
      tile(r, 0xFFFF7AB6, 0xFFFF9A3C);
      r.shadow(IconRaster.ring(0.5F, 0.5F, 0.22F, 0.2F), 0, 0.025F, 0.06F, 0x50000000);
      r.fill(IconRaster.ring(0.5F, 0.5F, 0.22F, 0.2F), 0xFFFFF4E8);
      r.fill(IconRaster.ring(0.5F, 0.49F, 0.22F, 0.16F), IconRaster.linear(0.3F, 0.3F, 0.7F, 0.7F, 0xFFFF5FA2, 0xFFE0307E));
      Random rnd = new Random(42);
      int[] c = {0xFFFFFFFF, 0xFF7DD3FC, 0xFFFDE047, 0xFF86EFAC};
      for (int i = 0; i < 12; i++) {
         double a = Math.PI * 2 * i / 12 + rnd.nextDouble() * 0.3;
         float d = 0.22F + (rnd.nextFloat() - 0.5F) * 0.08F;
         float x = 0.5F + (float)Math.cos(a) * d, y = 0.49F + (float)Math.sin(a) * d;
         float t = rnd.nextFloat() * 3.14F, l = 0.022F;
         r.fill(IconRaster.capsule(x - (float)Math.cos(t) * l, y - (float)Math.sin(t) * l, x + (float)Math.cos(t) * l, y + (float)Math.sin(t) * l, 0.011F), c[i % c.length]);
      }
      r.fill(IconRaster.circle(0.74F, 0.27F, 0.045F), 0xFFFFFFFF);
   });

   public static final AppIcon TRAVEL = AppIcon.painted("travel", r -> {
      tile(r, 0xFF38BDF8, 0xFF0D9488);
      r.fill(IconRaster.circle(0.70F, 0.30F, 0.11F), 0x66FFF7C2);
      for (int i = 0; i < 5; i++) r.fill(IconRaster.circle(0.18F + i * 0.075F, 0.80F - i * 0.07F, 0.018F), 0xB0FFFFFF);
      glyph(r, IconRaster.polygon(0.22F, 0.52F, 0.80F, 0.26F, 0.56F, 0.78F, 0.47F, 0.58F));
      r.fill(IconRaster.polygon(0.47F, 0.58F, 0.80F, 0.26F, 0.50F, 0.62F), 0xFFCDEFF5);
   });

   public static final AppIcon MAPS = AppIcon.painted("maps", r -> {
      tile(r, 0xFF34D399, 0xFF0F766E);
      r.fill(IconRaster.rotatedBox(0.5F, 0.62F, 0.7F, 0.05F, 0, -0.5F), 0x40FFFFFF);
      r.fill(IconRaster.rotatedBox(0.4F, 0.5F, 0.05F, 0.7F, 0, 0.3F), 0x33FFFFFF);
      IconRaster.Sdf pin = IconRaster.subtract(IconRaster.union(IconRaster.circle(0.5F, 0.40F, 0.20F), IconRaster.polygon(0.33F, 0.50F, 0.67F, 0.50F, 0.5F, 0.80F)),
         IconRaster.circle(0.5F, 0.40F, 0.075F));
      r.shadow(pin, 0, 0.03F, 0.05F, 0x50000000);
      r.fill(pin, 0xFFFF4D5E);
      r.fill(IconRaster.circle(0.5F, 0.40F, 0.075F), 0xFFFFFFFF);
   });

   public static final AppIcon GAMES = AppIcon.painted("games", r -> {
      tile(r, 0xFF8B5CF6, 0xFF4338CA);
      IconRaster.Sdf pad = IconRaster.union(IconRaster.box(0.5F, 0.52F, 0.28F, 0.15F, 0.15F), IconRaster.circle(0.3F, 0.62F, 0.13F), IconRaster.circle(0.7F, 0.62F, 0.13F));
      r.shadow(pad, 0, 0.025F, 0.05F, 0x50000000);
      r.fill(pad, 0xFFFFFFFF);
      r.fill(IconRaster.union(IconRaster.box(0.33F, 0.55F, 0.075F, 0.025F, 0.01F), IconRaster.box(0.33F, 0.55F, 0.025F, 0.075F, 0.01F)), 0xFF4C3FD6);
      r.fill(IconRaster.circle(0.66F, 0.50F, 0.032F), 0xFFFF5C8A);
      r.fill(IconRaster.circle(0.73F, 0.58F, 0.032F), 0xFF34D399);
   });

   public static final AppIcon SPECTER = AppIcon.painted("specter", r -> {
      tile(r, 0xFF2E1065, 0xFF09060F);
      r.fill(IconRaster.circle(0.5F, 0.48F, 0.36F), IconRaster.radial(0.5F, 0.48F, 0.36F, 0x609B6BFF, 0x009B6BFF));
      IconRaster.Sdf body = IconRaster.union(IconRaster.circle(0.5F, 0.42F, 0.2F), IconRaster.rect(0.3F, 0.42F, 0.7F, 0.72F, 0));
      IconRaster.Sdf waves = IconRaster.union(IconRaster.circle(0.37F, 0.75F, 0.066F), IconRaster.circle(0.5F, 0.75F, 0.066F), IconRaster.circle(0.63F, 0.75F, 0.066F));
      IconRaster.Sdf ghost = IconRaster.union(IconRaster.subtract(body, IconRaster.rect(0.2F, 0.70F, 0.8F, 0.9F, 0)), waves);
      glyph(r, ghost);
      r.fill(IconRaster.box(0.43F, 0.44F, 0.03F, 0.05F, 0.03F), 0xFF2A0E5C);
      r.fill(IconRaster.box(0.57F, 0.44F, 0.03F, 0.05F, 0.03F), 0xFF2A0E5C);
   });

   public static final AppIcon PROFILE = AppIcon.painted("profile", r -> {
      tile(r, 0xFF64748B, 0xFF1E3A8A);
      glyph(r, IconRaster.union(IconRaster.circle(0.5F, 0.38F, 0.14F), IconRaster.intersect(IconRaster.circle(0.5F, 0.86F, 0.28F), IconRaster.rect(0, 0, 1, 0.78F, 0))));
   });

   public static final AppIcon SETTINGS = AppIcon.painted("settings", r -> {
      tile(r, 0xFF9CA3AF, 0xFF374151);
      IconRaster.Sdf[] teeth = new IconRaster.Sdf[9];
      teeth[0] = IconRaster.circle(0.5F, 0.5F, 0.21F);
      for (int i = 0; i < 8; i++) {
         double a = Math.PI / 4 * i;
         teeth[i + 1] = IconRaster.rotatedBox(0.5F + (float)Math.cos(a) * 0.235F, 0.5F + (float)Math.sin(a) * 0.235F, 0.065F, 0.055F, 0.015F, (float)a);
      }
      glyph(r, IconRaster.subtract(IconRaster.union(teeth), IconRaster.circle(0.5F, 0.5F, 0.085F)));
   });

   public static final AppIcon WALLET = AppIcon.painted("wallet", r -> {
      tile(r, 0xFF22C55E, 0xFF047857);
      IconRaster.Sdf body = IconRaster.box(0.5F, 0.54F, 0.28F, 0.2F, 0.07F);
      r.shadow(body, 0, 0.025F, 0.05F, 0x50000000);
      r.fill(IconRaster.box(0.46F, 0.36F, 0.22F, 0.07F, 0.05F), 0xFFBBF7D0);
      r.fill(body, 0xFFFFFFFF);
      r.fill(IconRaster.box(0.70F, 0.54F, 0.09F, 0.065F, 0.04F), 0xFF16A34A);
      r.fill(IconRaster.circle(0.70F, 0.54F, 0.025F), 0xFFFFFFFF);
   });

   public static final AppIcon CHAT = AppIcon.painted("chat", r -> {
      tile(r, 0xFF4ADE80, 0xFF15803D);
      glyph(r, IconRaster.union(IconRaster.box(0.5F, 0.46F, 0.28F, 0.2F, 0.18F), IconRaster.polygon(0.32F, 0.58F, 0.46F, 0.64F, 0.28F, 0.80F)));
   });
}

package com.jfant.specter.phone.ui;

import com.jfant.specter.phone.gfx.IconRaster;
import java.util.function.Supplier;

/**
 * Line icons for the interface, painted in white at the exact pixel size they are shown and tinted when drawn.
 * Shapes use a 0..1 square.
 */
public enum Glyph {
   BACK(() -> IconRaster.union(cap(0.62F, 0.20F, 0.32F, 0.50F), cap(0.32F, 0.50F, 0.62F, 0.80F))),
   CHEVRON(() -> IconRaster.union(cap(0.38F, 0.20F, 0.68F, 0.50F), cap(0.68F, 0.50F, 0.38F, 0.80F))),
   DOWN(() -> IconRaster.union(cap(0.20F, 0.38F, 0.50F, 0.68F), cap(0.50F, 0.68F, 0.80F, 0.38F))),
   UP(() -> IconRaster.union(cap(0.20F, 0.62F, 0.50F, 0.32F), cap(0.50F, 0.32F, 0.80F, 0.62F))),
   CLOSE(() -> IconRaster.union(cap(0.24F, 0.24F, 0.76F, 0.76F), cap(0.76F, 0.24F, 0.24F, 0.76F))),
   PLUS(() -> IconRaster.union(cap(0.5F, 0.18F, 0.5F, 0.82F), cap(0.18F, 0.5F, 0.82F, 0.5F))),
   MINUS(() -> cap(0.18F, 0.5F, 0.82F, 0.5F)),
   CHECK(() -> IconRaster.union(cap(0.20F, 0.52F, 0.41F, 0.73F), cap(0.41F, 0.73F, 0.80F, 0.30F))),
   HEART(Glyph::heart),
   HEART_OUTLINE(() -> outline(heart(), 0.075F)),
   HOME(() -> IconRaster.subtract(IconRaster.union(
      IconRaster.polygon(0.5F, 0.12F, 0.88F, 0.46F, 0.12F, 0.46F),
      IconRaster.rect(0.22F, 0.42F, 0.78F, 0.86F, 0.05F)), IconRaster.rect(0.42F, 0.60F, 0.58F, 0.90F, 0.04F))),
   GRID(() -> IconRaster.union(IconRaster.rect(0.16F, 0.16F, 0.46F, 0.46F, 0.07F), IconRaster.rect(0.54F, 0.16F, 0.84F, 0.46F, 0.07F),
      IconRaster.rect(0.16F, 0.54F, 0.46F, 0.84F, 0.07F), IconRaster.rect(0.54F, 0.54F, 0.84F, 0.84F, 0.07F))),
   BELL(() -> IconRaster.union(IconRaster.subtract(IconRaster.union(IconRaster.circle(0.5F, 0.42F, 0.25F), IconRaster.rect(0.25F, 0.42F, 0.75F, 0.72F, 0.02F),
      IconRaster.rect(0.15F, 0.66F, 0.85F, 0.74F, 0.04F)), IconRaster.rect(0, 0.74F, 1, 1, 0)), IconRaster.circle(0.5F, 0.82F, 0.08F),
      IconRaster.circle(0.5F, 0.15F, 0.06F))),
   GEAR(Glyph::gear),
   SEARCH(() -> IconRaster.union(IconRaster.ring(0.43F, 0.43F, 0.24F, 0.10F), cap(0.61F, 0.61F, 0.82F, 0.82F))),
   CAMERA(() -> IconRaster.subtract(IconRaster.union(IconRaster.rect(0.10F, 0.28F, 0.90F, 0.82F, 0.10F), IconRaster.rect(0.34F, 0.18F, 0.66F, 0.32F, 0.05F)),
      IconRaster.ring(0.5F, 0.55F, 0.15F, 0.08F))),
   IMAGE(() -> IconRaster.union(outline(IconRaster.rect(0.12F, 0.18F, 0.88F, 0.82F, 0.08F), 0.07F),
      IconRaster.intersect(IconRaster.polygon(0.15F, 0.80F, 0.42F, 0.46F, 0.62F, 0.66F, 0.72F, 0.56F, 0.86F, 0.80F), IconRaster.rect(0.12F, 0.18F, 0.88F, 0.82F, 0.08F)),
      IconRaster.circle(0.68F, 0.36F, 0.07F))),
   SEND(() -> IconRaster.subtract(IconRaster.polygon(0.10F, 0.46F, 0.90F, 0.12F, 0.56F, 0.90F, 0.44F, 0.58F), IconRaster.polygon(0.44F, 0.58F, 0.90F, 0.12F, 0.46F, 0.62F))),
   PIN(() -> IconRaster.subtract(IconRaster.union(IconRaster.circle(0.5F, 0.40F, 0.27F), IconRaster.polygon(0.27F, 0.52F, 0.73F, 0.52F, 0.5F, 0.92F)),
      IconRaster.circle(0.5F, 0.40F, 0.10F))),
   NAV(() -> IconRaster.polygon(0.5F, 0.10F, 0.82F, 0.88F, 0.5F, 0.70F, 0.18F, 0.88F)),
   STAR(Glyph::star),
   STAR_OUTLINE(() -> outline(star(), 0.07F)),
   TRASH(() -> IconRaster.union(IconRaster.subtract(IconRaster.rect(0.25F, 0.30F, 0.75F, 0.88F, 0.06F), IconRaster.union(
      IconRaster.rect(0.36F, 0.40F, 0.44F, 0.78F, 0.03F), IconRaster.rect(0.56F, 0.40F, 0.64F, 0.78F, 0.03F))),
      cap(0.16F, 0.22F, 0.84F, 0.22F), IconRaster.rect(0.40F, 0.10F, 0.60F, 0.20F, 0.03F))),
   SHARE(() -> IconRaster.union(IconRaster.circle(0.72F, 0.22F, 0.12F), IconRaster.circle(0.28F, 0.50F, 0.12F), IconRaster.circle(0.72F, 0.78F, 0.12F),
      cap(0.72F, 0.22F, 0.28F, 0.50F, 0.045F), cap(0.28F, 0.50F, 0.72F, 0.78F, 0.045F))),
   REFRESH(() -> IconRaster.union(IconRaster.arc(0.5F, 0.5F, 0.30F, 0.10F, -0.6F, 4.2F), IconRaster.polygon(0.66F, 0.12F, 0.86F, 0.38F, 0.56F, 0.40F))),
   TROPHY(() -> IconRaster.union(IconRaster.subtract(IconRaster.union(IconRaster.rect(0.28F, 0.14F, 0.72F, 0.34F, 0.0F), IconRaster.circle(0.5F, 0.34F, 0.22F)),
      IconRaster.rect(0, 0.50F, 1, 1, 0)), IconRaster.union(IconRaster.intersect(IconRaster.circle(0.5F, 0.34F, 0.22F), IconRaster.rect(0, 0.3F, 1, 0.6F, 0))),
      IconRaster.ring(0.24F, 0.32F, 0.10F, 0.06F), IconRaster.ring(0.76F, 0.32F, 0.10F, 0.06F), IconRaster.rect(0.45F, 0.52F, 0.55F, 0.74F, 0),
      IconRaster.rect(0.30F, 0.74F, 0.70F, 0.86F, 0.04F))),
   USER(() -> IconRaster.union(IconRaster.circle(0.5F, 0.33F, 0.17F),
      IconRaster.intersect(IconRaster.circle(0.5F, 0.98F, 0.36F), IconRaster.rect(0, 0, 1, 0.88F, 0)))),
   PLAY(() -> IconRaster.grow(IconRaster.polygon(0.32F, 0.20F, 0.80F, 0.50F, 0.32F, 0.80F), 0.03F)),
   FLAG(() -> IconRaster.union(cap(0.24F, 0.14F, 0.24F, 0.88F, 0.05F), IconRaster.polygon(0.26F, 0.14F, 0.82F, 0.30F, 0.26F, 0.52F))),
   COPY(() -> IconRaster.union(outline(IconRaster.rect(0.30F, 0.12F, 0.86F, 0.68F, 0.08F), 0.065F),
      IconRaster.subtract(outline(IconRaster.rect(0.14F, 0.32F, 0.70F, 0.88F, 0.08F), 0.065F), IconRaster.rect(0.28F, 0.10F, 0.88F, 0.70F, 0.1F)))),
   EDIT(() -> IconRaster.union(IconRaster.rotatedBox(0.54F, 0.46F, 0.09F, 0.30F, 0.02F, 0.785F), IconRaster.polygon(0.18F, 0.82F, 0.24F, 0.62F, 0.38F, 0.76F))),
   GLOBE(() -> IconRaster.union(IconRaster.ring(0.5F, 0.5F, 0.34F, 0.075F), outline(IconRaster.box(0.5F, 0.5F, 0.14F, 0.34F, 0.14F), 0.07F),
      cap(0.18F, 0.5F, 0.82F, 0.5F, 0.035F))),
   LOCK(() -> IconRaster.union(IconRaster.arc(0.5F, 0.42F, 0.17F, 0.08F, 3.14159F, 6.28318F), cap(0.33F, 0.42F, 0.33F, 0.50F, 0.04F), cap(0.67F, 0.42F, 0.67F, 0.50F, 0.04F),
      IconRaster.subtract(IconRaster.rect(0.22F, 0.46F, 0.78F, 0.88F, 0.08F), IconRaster.circle(0.5F, 0.66F, 0.06F)))),
   CHAT(() -> IconRaster.union(IconRaster.rect(0.12F, 0.16F, 0.88F, 0.70F, 0.16F), IconRaster.polygon(0.26F, 0.62F, 0.46F, 0.66F, 0.22F, 0.88F))),
   MORE(() -> IconRaster.union(IconRaster.circle(0.2F, 0.5F, 0.08F), IconRaster.circle(0.5F, 0.5F, 0.08F), IconRaster.circle(0.8F, 0.5F, 0.08F))),
   SUN(() -> {
      IconRaster.Sdf[] parts = new IconRaster.Sdf[9];
      parts[0] = IconRaster.circle(0.5F, 0.5F, 0.17F);
      for (int i = 0; i < 8; i++) {
         double a = Math.PI / 4 * i;
         float c = (float)Math.cos(a), s = (float)Math.sin(a);
         parts[i + 1] = cap(0.5F + c * 0.29F, 0.5F + s * 0.29F, 0.5F + c * 0.38F, 0.5F + s * 0.38F, 0.045F);
      }
      return IconRaster.union(parts);
   }),
   MOON(() -> IconRaster.subtract(IconRaster.circle(0.48F, 0.52F, 0.32F), IconRaster.circle(0.66F, 0.38F, 0.27F))),
   SPARK(() -> IconRaster.union(
      IconRaster.polygon(0.42F, 0.08F, 0.50F, 0.40F, 0.80F, 0.48F, 0.50F, 0.56F, 0.42F, 0.88F, 0.34F, 0.56F, 0.04F, 0.48F, 0.34F, 0.40F),
      IconRaster.polygon(0.78F, 0.08F, 0.82F, 0.20F, 0.94F, 0.24F, 0.82F, 0.28F, 0.78F, 0.40F, 0.74F, 0.28F, 0.62F, 0.24F, 0.74F, 0.20F))),
   PLANE(() -> IconRaster.union(IconRaster.polygon(0.50F, 0.06F, 0.58F, 0.20F, 0.58F, 0.40F, 0.92F, 0.60F, 0.92F, 0.68F, 0.58F, 0.56F, 0.56F, 0.78F, 0.68F, 0.88F,
      0.68F, 0.94F, 0.50F, 0.88F, 0.32F, 0.94F, 0.32F, 0.88F, 0.44F, 0.78F, 0.42F, 0.56F, 0.08F, 0.68F, 0.08F, 0.60F, 0.42F, 0.40F, 0.42F, 0.20F))),
   CROSSHAIR(() -> IconRaster.union(IconRaster.ring(0.5F, 0.5F, 0.27F, 0.07F), cap(0.5F, 0.08F, 0.5F, 0.30F, 0.04F), cap(0.5F, 0.70F, 0.5F, 0.92F, 0.04F),
      cap(0.08F, 0.5F, 0.30F, 0.5F, 0.04F), cap(0.70F, 0.5F, 0.92F, 0.5F, 0.04F), IconRaster.circle(0.5F, 0.5F, 0.06F))),
   POWER(() -> IconRaster.union(IconRaster.arc(0.5F, 0.54F, 0.28F, 0.09F, -1.05F, 4.19F), cap(0.5F, 0.12F, 0.5F, 0.46F, 0.045F))),
   INFO(() -> IconRaster.subtract(IconRaster.circle(0.5F, 0.5F, 0.40F), IconRaster.union(IconRaster.circle(0.5F, 0.30F, 0.06F), cap(0.5F, 0.46F, 0.5F, 0.72F, 0.05F))));

   private final Supplier<IconRaster.Sdf> shape;
   private IconRaster.Sdf built;

   Glyph(Supplier<IconRaster.Sdf> shape) {
      this.shape = shape;
   }

   void paint(IconRaster r) {
      if (this.built == null) this.built = this.shape.get();
      r.fill(this.built, 0xFFFFFFFF);
   }

   private static IconRaster.Sdf cap(float ax, float ay, float bx, float by) {
      return IconRaster.capsule(ax, ay, bx, by, 0.065F);
   }

   private static IconRaster.Sdf cap(float ax, float ay, float bx, float by, float r) {
      return IconRaster.capsule(ax, ay, bx, by, r);
   }

   private static IconRaster.Sdf outline(IconRaster.Sdf shape, float width) {
      return IconRaster.subtract(shape, IconRaster.grow(shape, -width));
   }

   static IconRaster.Sdf heart() {
      return IconRaster.union(IconRaster.circle(0.32F, 0.38F, 0.20F), IconRaster.circle(0.68F, 0.38F, 0.20F),
         IconRaster.polygon(0.125F, 0.44F, 0.875F, 0.44F, 0.5F, 0.86F));
   }

   static IconRaster.Sdf star() {
      float[] pts = new float[20];
      for (int i = 0; i < 10; i++) {
         double a = -Math.PI / 2 + Math.PI / 5 * i;
         float rr = i % 2 == 0 ? 0.42F : 0.18F;
         pts[i * 2] = 0.5F + (float)Math.cos(a) * rr;
         pts[i * 2 + 1] = 0.54F + (float)Math.sin(a) * rr;
      }
      return IconRaster.grow(IconRaster.polygon(pts), 0.02F);
   }

   static IconRaster.Sdf gear() {
      IconRaster.Sdf[] teeth = new IconRaster.Sdf[9];
      teeth[0] = IconRaster.circle(0.5F, 0.5F, 0.27F);
      for (int i = 0; i < 8; i++) {
         double a = Math.PI / 4 * i;
         teeth[i + 1] = IconRaster.rotatedBox(0.5F + (float)Math.cos(a) * 0.30F, 0.5F + (float)Math.sin(a) * 0.30F, 0.08F, 0.07F, 0.02F, (float)a);
      }
      return IconRaster.subtract(IconRaster.union(teeth), IconRaster.circle(0.5F, 0.5F, 0.11F));
   }
}

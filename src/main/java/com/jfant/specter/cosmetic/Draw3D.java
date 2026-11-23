package com.jfant.specter.cosmetic;

import com.jfant.specter.core.renderer.MeshBuilder;
import com.jfant.specter.core.renderer.Renderer3D;

/**
 * Immediate-mode triangles into a {@link Renderer3D} with packed ARGB colours: shapes oriented in any plane, boxes,
 * rings and soft glows. Nothing here allocates; everything takes plain doubles.
 *
 * <p>Which renderer to pass decides how it blends: {@code solidRenderer} (opaque, writes depth), {@code depthRenderer}
 * (translucent) or {@code glowRenderer} (additive light).
 */
public final class Draw3D {
   private Draw3D() {
   }

   public static void tri(Renderer3D r, double x1, double y1, double z1, int c1, double x2, double y2, double z2, int c2, double x3, double y3, double z3, int c3) {
      MeshBuilder m = r.triangles;
      m.ensureTriCapacity();
      m.triangle(m.vec3(x1, y1, z1).color(c1).next(), m.vec3(x2, y2, z2).color(c2).next(), m.vec3(x3, y3, z3).color(c3).next());
   }

   public static void quad(
      Renderer3D r, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int c1, int c2, int c3, int c4
   ) {
      MeshBuilder m = r.triangles;
      m.ensureQuadCapacity();
      m.quad(m.vec3(x1, y1, z1).color(c1).next(), m.vec3(x2, y2, z2).color(c2).next(), m.vec3(x3, y3, z3).color(c3).next(), m.vec3(x4, y4, z4).color(c4).next());
   }

   /**
    * A {@link Shapes} outline as a triangle fan, in the plane spanned by {@code right} and {@code up} (both already scaled
    * to the size wanted), centred on cx/cy/cz. {@code centre} colours the middle, {@code edge} the rim.
    */
   public static void shape(
      Renderer3D r, float[] outline, double cx, double cy, double cz, double rx, double ry, double rz, double ux, double uy, double uz, int centre, int edge
   ) {
      MeshBuilder m = r.triangles;
      int n = outline.length / 2;
      m.ensureCapacity(n + 1, n * 3);
      int mid = m.vec3(cx, cy, cz).color(centre).next();
      int first = -1;
      int prev = -1;
      for (int i = 0; i < n; i++) {
         double px = outline[i * 2];
         double py = outline[i * 2 + 1];
         int v = m.vec3(cx + rx * px + ux * py, cy + ry * px + uy * py, cz + rz * px + uz * py).color(edge).next();
         if (prev >= 0) m.triangle(mid, prev, v);
         else first = v;
         prev = v;
      }
      m.triangle(mid, prev, first);
   }

   /** A shape offset by {@code ox, oy} (in shape units) and scaled by {@code s} inside the plane, e.g. a pip on a card. */
   public static void shapeAt(
      Renderer3D r, float[] outline, double cx, double cy, double cz, double rx, double ry, double rz, double ux, double uy, double uz, double ox, double oy,
      double s, int centre, int edge
   ) {
      shape(r, outline, cx + rx * ox + ux * oy, cy + ry * ox + uy * oy, cz + rz * ox + uz * oy, rx * s, ry * s, rz * s, ux * s, uy * s, uz * s, centre, edge);
   }

   /** A soft round glow: bright in the middle, fading to nothing at the rim. Use the glow renderer. */
   public static void glow(Renderer3D r, double cx, double cy, double cz, double rx, double ry, double rz, double ux, double uy, double uz, int argb) {
      shape(r, Shapes.CIRCLE_24, cx, cy, cz, rx, ry, rz, ux, uy, uz, argb, argb & 0x00FFFFFF);
   }

   /** A flat band between two radii around an axis-aligned (horizontal) circle: halo glows and rings. */
   public static void ring(Renderer3D r, double cx, double cy, double cz, double inner, double outer, int segments, int innerColor, int outerColor, double angle) {
      MeshBuilder m = r.triangles;
      m.ensureCapacity((segments + 1) * 2, segments * 6);
      int pi = -1;
      int po = -1;
      for (int i = 0; i <= segments; i++) {
         double a = angle + Math.PI * 2.0 * i / segments;
         double c = Math.cos(a);
         double s = Math.sin(a);
         int vi = m.vec3(cx + c * inner, cy, cz + s * inner).color(innerColor).next();
         int vo = m.vec3(cx + c * outer, cy, cz + s * outer).color(outerColor).next();
         if (pi >= 0) m.quad(pi, po, vo, vi);
         pi = vi;
         po = vo;
      }
   }

   /** An axis-aligned box; each face shaded by {@code shade[face]} (down, up, north, south, west, east). */
   public static void box(Renderer3D r, double x1, double y1, double z1, double x2, double y2, double z2, int argb, float[] shade) {
      int down = Fx.shade(argb, shade[0]);
      int up = Fx.shade(argb, shade[1]);
      int north = Fx.shade(argb, shade[2]);
      int south = Fx.shade(argb, shade[3]);
      int west = Fx.shade(argb, shade[4]);
      int east = Fx.shade(argb, shade[5]);
      quad(r, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, down, down, down, down);
      quad(r, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, up, up, up, up);
      quad(r, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, north, north, north, north);
      quad(r, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, south, south, south, south);
      quad(r, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, west, west, west, west);
      quad(r, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, east, east, east, east);
   }

   /** A box only with the faces that point at the camera (exact for a single box, half the triangles). */
   public static void boxFacing(
      Renderer3D r, double x1, double y1, double z1, double x2, double y2, double z2, int argb, float[] shade, double camX, double camY, double camZ
   ) {
      if (camY < y1) {
         int c = Fx.shade(argb, shade[0]);
         quad(r, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, c, c, c, c);
      } else if (camY > y2) {
         int c = Fx.shade(argb, shade[1]);
         quad(r, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, c, c, c, c);
      }
      if (camZ < z1) {
         int c = Fx.shade(argb, shade[2]);
         quad(r, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, c, c, c, c);
      } else if (camZ > z2) {
         int c = Fx.shade(argb, shade[3]);
         quad(r, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, c, c, c, c);
      }
      if (camX < x1) {
         int c = Fx.shade(argb, shade[4]);
         quad(r, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, c, c, c, c);
      } else if (camX > x2) {
         int c = Fx.shade(argb, shade[5]);
         quad(r, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, c, c, c, c);
      }
   }
}

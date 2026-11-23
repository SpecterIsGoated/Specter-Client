package com.jfant.specter.phone.render;

import static com.jfant.specter.phone.core.PhoneAnimationController.BODY_D;
import static com.jfant.specter.phone.core.PhoneAnimationController.BODY_H;
import static com.jfant.specter.phone.core.PhoneAnimationController.BODY_R;
import static com.jfant.specter.phone.core.PhoneAnimationController.BODY_W;
import static com.jfant.specter.phone.core.PhoneAnimationController.SCREEN_H;
import static com.jfant.specter.phone.core.PhoneAnimationController.SCREEN_R;
import static com.jfant.specter.phone.core.PhoneAnimationController.SCREEN_W;

import java.util.Arrays;

/**
 * The phone's geometry in millimetres, built once: a rounded slab with chamfered glass edges, side buttons, a camera
 * island on the back, and the screen (with UVs that match the 2D interface exactly). Front faces +Z, Y up.
 *
 * <p>Quads are stored flat (4 vertices each); triangles are quads with a repeated last vertex. Each body quad carries
 * a material id so colours can follow the theme without rebuilding.
 */
public final class PhoneMesh {
   public static final int FRAME = 0;
   public static final int GLASS = 1;
   public static final int BACK = 2;
   public static final int LENS = 3;
   public static final int BUTTON = 4;
   public static final int ISLAND = 5;
   public static final int LENS_RING = 6;

   private static final int CORNER_STEPS = 7;
   private static final float BEVEL = 1.0F;

   /** Body: 4 vertices per quad, xyz. */
   public final float[] bodyPos;
   public final float[] bodyNrm;
   /** Per vertex brightness (baked sheen on the frame). */
   public final float[] bodyShade;
   public final int[] bodyMat;
   public final int bodyQuads;
   /** Screen: 4 vertices per quad, xyz + uv. */
   public final float[] screenPos;
   public final float[] screenUv;
   public final int screenQuads;
   /** The punch-hole camera, a small disc just above the glass. */
   public final float[] holePos;
   public final int holeQuads;
   public static final float FRONT_Z = BODY_D / 2;
   public static final float HOLE_Y = SCREEN_H / 2 - 4.2F;
   public static final float HOLE_R = 1.55F;

   private static PhoneMesh instance;

   public static PhoneMesh get() {
      if (instance == null) instance = new PhoneMesh();
      return instance;
   }

   private float[] pos = new float[4096];
   private float[] nrm = new float[4096];
   private float[] shade = new float[2048];
   private int[] mat = new int[512];
   private int quads;

   private PhoneMesh() {
      float zF = BODY_D / 2, zB = -BODY_D / 2;
      float[][] outer = outline(BODY_W / 2, BODY_H / 2, BODY_R);
      float[][] inner = outline(BODY_W / 2 - BEVEL, BODY_H / 2 - BEVEL, BODY_R - BEVEL);
      int n = outer.length;
      // front glass (fan) and back panel
      for (int i = 0; i < n; i++) {
         float[] a = inner[i], b = inner[(i + 1) % n];
         this.tri(0, 0, zF, a[0], a[1], zF, b[0], b[1], zF, 0, 0, 1, GLASS, 1);
         this.tri(0, 0, zB, b[0], b[1], zB, a[0], a[1], zB, 0, 0, -1, BACK, 1);
      }
      // chamfers and side wall
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         float[] oi = outer[i], oj = outer[j], ii = inner[i], ij = inner[j];
         float nx = oi[2], ny = oi[3], nx2 = oj[2], ny2 = oj[3];
         float sheenI = 0.82F + 0.30F * Math.max(0, ny * 0.7F + nx * 0.3F), sheenJ = 0.82F + 0.30F * Math.max(0, ny2 * 0.7F + nx2 * 0.3F);
         // side wall
         this.quad(new float[]{oi[0], oi[1], zB + BEVEL, oj[0], oj[1], zB + BEVEL, oj[0], oj[1], zF - BEVEL, oi[0], oi[1], zF - BEVEL},
            new float[]{nx, ny, 0, nx2, ny2, 0, nx2, ny2, 0, nx, ny, 0}, new float[]{sheenI, sheenJ, sheenJ, sheenI}, FRAME);
         // front chamfer
         float c = 0.7071F;
         this.quad(new float[]{oi[0], oi[1], zF - BEVEL, oj[0], oj[1], zF - BEVEL, ij[0], ij[1], zF, ii[0], ii[1], zF},
            new float[]{nx * c, ny * c, c, nx2 * c, ny2 * c, c, nx2 * c, ny2 * c, c, nx * c, ny * c, c},
            new float[]{sheenI + 0.15F, sheenJ + 0.15F, sheenJ + 0.15F, sheenI + 0.15F}, FRAME);
         // back chamfer
         this.quad(new float[]{ii[0], ii[1], zB, ij[0], ij[1], zB, oj[0], oj[1], zB + BEVEL, oi[0], oi[1], zB + BEVEL},
            new float[]{nx * c, ny * c, -c, nx2 * c, ny2 * c, -c, nx2 * c, ny2 * c, -c, nx * c, ny * c, -c},
            new float[]{sheenI * 0.9F, sheenJ * 0.9F, sheenJ * 0.9F, sheenI * 0.9F}, FRAME);
      }
      // side buttons: power on the right, volume on the left
      this.box(BODY_W / 2 - 0.2F, 24.0F, 0, BODY_W / 2 + 0.7F, 40.0F, 1.6F, BUTTON);
      this.box(-BODY_W / 2 - 0.7F, 36.0F, 0, -BODY_W / 2 + 0.2F, 48.0F, 1.6F, BUTTON);
      this.box(-BODY_W / 2 - 0.7F, 20.0F, 0, -BODY_W / 2 + 0.2F, 32.0F, 1.6F, BUTTON);
      // camera island on the back (seen from behind it sits top left)
      float ix0 = 8.0F, ix1 = 31.0F, iy0 = BODY_H / 2 - 31.0F, iy1 = BODY_H / 2 - 8.0F;
      this.slab(ix0, iy0, ix1, iy1, 5.5F, zB, zB - 1.1F, ISLAND);
      this.lens((ix0 + ix1) / 2 - 5.2F, iy1 - 6.6F, 4.4F, zB - 1.1F, zB - 1.9F);
      this.lens((ix0 + ix1) / 2 - 5.2F, iy0 + 6.6F, 4.4F, zB - 1.1F, zB - 1.9F);
      this.lens((ix0 + ix1) / 2 + 5.6F, (iy0 + iy1) / 2, 3.3F, zB - 1.1F, zB - 1.6F);

      this.bodyQuads = this.quads;
      this.bodyPos = Arrays.copyOf(this.pos, this.quads * 12);
      this.bodyNrm = Arrays.copyOf(this.nrm, this.quads * 12);
      this.bodyShade = Arrays.copyOf(this.shade, this.quads * 4);
      this.bodyMat = Arrays.copyOf(this.mat, this.quads);

      // screen: fan of the rounded rectangle, uv from position
      float[][] scr = outline(SCREEN_W / 2, SCREEN_H / 2, SCREEN_R);
      int m = scr.length;
      this.screenQuads = m;
      this.screenPos = new float[m * 12];
      this.screenUv = new float[m * 8];
      float z = zF + 0.02F;
      for (int i = 0; i < m; i++) {
         float[] a = scr[i], b = scr[(i + 1) % m];
         float[] p = {0, 0, z, a[0], a[1], z, b[0], b[1], z, b[0], b[1], z};
         System.arraycopy(p, 0, this.screenPos, i * 12, 12);
         for (int k = 0; k < 4; k++) {
            this.screenUv[i * 8 + k * 2] = (p[k * 3] + SCREEN_W / 2) / SCREEN_W;
            this.screenUv[i * 8 + k * 2 + 1] = (SCREEN_H / 2 - p[k * 3 + 1]) / SCREEN_H;
         }
      }
      // punch hole disc
      int segs = 16;
      this.holeQuads = segs;
      this.holePos = new float[segs * 12];
      float hz = zF + 0.05F;
      for (int i = 0; i < segs; i++) {
         double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
         float[] p = {0, HOLE_Y, hz, (float)Math.cos(a0) * HOLE_R, HOLE_Y + (float)Math.sin(a0) * HOLE_R, hz,
            (float)Math.cos(a1) * HOLE_R, HOLE_Y + (float)Math.sin(a1) * HOLE_R, hz, (float)Math.cos(a1) * HOLE_R, HOLE_Y + (float)Math.sin(a1) * HOLE_R, hz};
         System.arraycopy(p, 0, this.holePos, i * 12, 12);
      }
      this.pos = null;
      this.nrm = null;
      this.shade = null;
      this.mat = null;
   }

   /** Counter-clockwise outline (seen from +Z) of a rounded rectangle: x, y, outward normal x, y. */
   static float[][] outline(float hw, float hh, float r) {
      float[][] pts = new float[4 * (CORNER_STEPS + 1)][];
      int k = 0;
      float[][] centres = {{hw - r, hh - r}, {-hw + r, hh - r}, {-hw + r, -hh + r}, {hw - r, -hh + r}};
      for (int c = 0; c < 4; c++) {
         double start = Math.PI / 2 * c;
         for (int s = 0; s <= CORNER_STEPS; s++) {
            double a = start + Math.PI / 2 * s / CORNER_STEPS;
            float nx = (float)Math.cos(a), ny = (float)Math.sin(a);
            pts[k++] = new float[]{centres[c][0] + nx * r, centres[c][1] + ny * r, nx, ny};
         }
      }
      return pts;
   }

   private void tri(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float nx, float ny, float nz, int material, float sh) {
      this.quad(new float[]{ax, ay, az, bx, by, bz, cx, cy, cz, cx, cy, cz}, new float[]{nx, ny, nz, nx, ny, nz, nx, ny, nz, nx, ny, nz},
         new float[]{sh, sh, sh, sh}, material);
   }

   private void quad(float[] p, float[] normals, float[] sheen, int material) {
      if ((this.quads + 1) * 12 > this.pos.length) {
         this.pos = Arrays.copyOf(this.pos, this.pos.length * 2);
         this.nrm = Arrays.copyOf(this.nrm, this.nrm.length * 2);
         this.shade = Arrays.copyOf(this.shade, this.shade.length * 2);
         this.mat = Arrays.copyOf(this.mat, this.mat.length * 2);
      }
      System.arraycopy(p, 0, this.pos, this.quads * 12, 12);
      System.arraycopy(normals, 0, this.nrm, this.quads * 12, 12);
      System.arraycopy(sheen, 0, this.shade, this.quads * 4, 4);
      this.mat[this.quads] = material;
      this.quads++;
   }

   /** Axis-aligned box (x0,y0)-(x1,y1), depth +-halfDepth around z. */
   private void box(float x0, float y0, float zc, float x1, float y1, float halfDepth, int material) {
      float z0 = zc - halfDepth, z1 = zc + halfDepth;
      this.face(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, material);
      this.face(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, material);
      this.face(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, material);
      this.face(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, material);
      this.face(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, material);
      this.face(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, material);
   }

   private void face(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny,
                     float nz, int material) {
      this.quad(new float[]{ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz}, new float[]{nx, ny, nz, nx, ny, nz, nx, ny, nz, nx, ny, nz},
         new float[]{1, 1, 1, 1}, material);
   }

   /** A rounded slab on the back (facing -Z), from z0 out to z1 (z1 < z0). */
   private void slab(float x0, float y0, float x1, float y1, float r, float z0, float z1, int material) {
      float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
      float[][] o = outline((x1 - x0) / 2, (y1 - y0) / 2, r);
      int n = o.length;
      for (int i = 0; i < n; i++) {
         float[] a = o[i], b = o[(i + 1) % n];
         this.tri(cx, cy, z1, cx + b[0], cy + b[1], z1, cx + a[0], cy + a[1], z1, 0, 0, -1, material, 1);
         this.quad(new float[]{cx + a[0], cy + a[1], z0, cx + a[0], cy + a[1], z1, cx + b[0], cy + b[1], z1, cx + b[0], cy + b[1], z0},
            new float[]{a[2], a[3], 0, a[2], a[3], 0, b[2], b[3], 0, b[2], b[3], 0}, new float[]{1, 1, 1, 1}, material);
      }
   }

   private void lens(float cx, float cy, float r, float z0, float z1) {
      int segs = 14;
      for (int i = 0; i < segs; i++) {
         double a0 = -Math.PI * 2 * i / segs, a1 = -Math.PI * 2 * (i + 1) / segs;
         float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0), c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
         this.tri(cx, cy, z1, cx + c0 * r * 0.72F, cy + s0 * r * 0.72F, z1, cx + c1 * r * 0.72F, cy + s1 * r * 0.72F, z1, 0, 0, -1, LENS, 1);
         this.quad(new float[]{cx + c0 * r, cy + s0 * r, z1 + 0.05F, cx + c1 * r, cy + s1 * r, z1 + 0.05F, cx + c1 * r * 0.72F, cy + s1 * r * 0.72F, z1,
            cx + c0 * r * 0.72F, cy + s0 * r * 0.72F, z1}, new float[]{0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1}, new float[]{1.2F, 1.2F, 1.2F, 1.2F}, LENS_RING);
         this.quad(new float[]{cx + c1 * r, cy + s1 * r, z0, cx + c1 * r, cy + s1 * r, z1 + 0.05F, cx + c0 * r, cy + s0 * r, z1 + 0.05F, cx + c0 * r, cy + s0 * r, z0},
            new float[]{c1, s1, 0, c1, s1, 0, c0, s0, 0, c0, s0, 0}, new float[]{1, 1, 1, 1}, LENS_RING);
      }
   }
}

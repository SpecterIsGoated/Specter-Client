package com.jfant.specter.mech;

import java.util.Arrays;

/**
 * Quad geometry for one mech part, in the part's own frame (blocks, pivot at the origin).
 *
 * <p>Built once at start-up by {@link Builder}; afterwards it is read-only flat arrays, so drawing never allocates.
 * Every closed primitive fixes its own winding against its centre, so the faces always point outwards no matter how
 * the corners were listed or whether the primitive was mirrored - the entity pipeline culls back faces.
 */
public final class MechMesh {
   /** 4 vertices x (x, y, z) per quad. */
   public final float[] pos;
   /** 4 vertices x (u, v) per quad. */
   public final float[] uv;
   /** 4 vertices x brightness factor per quad (baked soft gradient, gives the metal some depth). */
   public final float[] shade;
   /** One normal (x, y, z) per quad. */
   public final float[] normal;
   /** 4 vertices x normal (x, y, z): smooth shading for imported surfaces, the face normal everywhere else. */
   public final float[] vnormal;
   /** One material id per quad, see {@link MechPalette}. */
   public final byte[] material;
   public final int quads;

   private MechMesh(float[] pos, float[] uv, float[] shade, float[] normal, float[] vnormal, byte[] material, int quads) {
      this.pos = pos;
      this.uv = uv;
      this.shade = shade;
      this.normal = normal;
      this.vnormal = vnormal;
      this.material = material;
      this.quads = quads;
   }

   public static Builder builder() {
      return new Builder();
   }

   /** Number of quads using a glowing material (drawn in the emissive pass). */
   public int glowQuads() {
      int n = 0;
      for (int i = 0; i < this.quads; i++) {
         if (MechPalette.glows(this.material[i])) n++;
      }
      return n;
   }

   public static final class Builder {
      /** Texture repeats per block: the panel texture shows a seam roughly every half block. */
      private static final float UV_SCALE = 2.0F;
      private float[] pos = new float[4 * 3 * 64];
      private float[] uv = new float[4 * 2 * 64];
      private float[] shade = new float[4 * 64];
      private float[] normal = new float[3 * 64];
      private float[] vnormal = new float[4 * 3 * 64];
      private byte[] material = new byte[64];
      private int quads;
      private boolean mirror;
      private int seed = 1;

      /** Everything added after this is mirrored across x = 0 (right-hand copy of a left-hand piece). */
      public Builder mirror(boolean mirror) {
         this.mirror = mirror;
         return this;
      }

      public boolean mirrored() {
         return this.mirror;
      }

      private float mx(float x) {
         return this.mirror ? -x : x;
      }

      // ------------------------------------------------------------------------------------------------ primitives

      /** Axis aligned box. */
      public Builder box(int mat, float x0, float y0, float z0, float x1, float y1, float z1) {
         return this.hexa(mat, new float[]{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1});
      }

      /**
       * Any six-sided solid with quad faces. Corners in the order of a box: 0 (x0 y0 z0), 1 (x1 y0 z0), 2 (x1 y1 z0),
       * 3 (x0 y1 z0), 4 (x0 y0 z1), 5 (x1 y0 z1), 6 (x1 y1 z1), 7 (x0 y1 z1).
       */
      public Builder hexa(int mat, float[] c) {
         float[] p = new float[24];
         for (int i = 0; i < 8; i++) {
            p[i * 3] = this.mx(c[i * 3]);
            p[i * 3 + 1] = c[i * 3 + 1];
            p[i * 3 + 2] = c[i * 3 + 2];
         }
         float cx = 0, cy = 0, cz = 0;
         for (int i = 0; i < 8; i++) {
            cx += p[i * 3];
            cy += p[i * 3 + 1];
            cz += p[i * 3 + 2];
         }
         cx /= 8;
         cy /= 8;
         cz /= 8;
         int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {0, 1, 5, 4}, {3, 7, 6, 2}};
         for (int[] f : faces) {
            this.quadFixed(mat, p, f[0], f[1], f[2], f[3], cx, cy, cz);
         }
         return this;
      }

      /**
       * A block running along Y from y0 to y1 whose cross-section is a rectangle centred on (cx, cz): half sizes hx0/hz0
       * at the bottom, hx1/hz1 at the top, and the top centre shifted by (ox, oz). Good for limbs and tapered armour.
       */
      public Builder taperY(int mat, float cx, float cz, float y0, float y1, float hx0, float hz0, float hx1, float hz1, float ox, float oz) {
         float tx = cx + ox, tz = cz + oz;
         return this.hexa(
            mat,
            new float[]{
               cx - hx0, y0, cz - hz0, cx + hx0, y0, cz - hz0, tx + hx1, y1, tz - hz1, tx - hx1, y1, tz - hz1,
               cx - hx0, y0, cz + hz0, cx + hx0, y0, cz + hz0, tx + hx1, y1, tz + hz1, tx - hx1, y1, tz + hz1
            }
         );
      }

      /** Same as {@link #taperY} but running along Z (cross-section in X/Y). */
      public Builder taperZ(int mat, float cx, float cy, float z0, float z1, float hx0, float hy0, float hx1, float hy1, float ox, float oy) {
         float tx = cx + ox, ty = cy + oy;
         return this.hexa(
            mat,
            new float[]{
               cx - hx0, cy - hy0, z0, cx + hx0, cy - hy0, z0, cx + hx0, cy + hy0, z0, cx - hx0, cy + hy0, z0,
               tx - hx1, ty - hy1, z1, tx + hx1, ty - hy1, z1, tx + hx1, ty + hy1, z1, tx - hx1, ty + hy1, z1
            }
         );
      }

      /** Same as {@link #taperY} but running along X (cross-section in Y/Z). */
      public Builder taperX(int mat, float cy, float cz, float x0, float x1, float hy0, float hz0, float hy1, float hz1, float oy, float oz) {
         float ty = cy + oy, tz = cz + oz;
         return this.hexa(
            mat,
            new float[]{
               x0, cy - hy0, cz - hz0, x1, ty - hy1, tz - hz1, x1, ty + hy1, tz - hz1, x0, cy + hy0, cz - hz0,
               x0, cy - hy0, cz + hz0, x1, ty - hy1, tz + hz1, x1, ty + hy1, tz + hz1, x0, cy + hy0, cz + hz0
            }
         );
      }

      /**
       * A closed n-sided prism (cylinder look-alike) along an axis: 0 = X, 1 = Y, 2 = Z. Radius r0 at a0, r1 at a1,
       * centred on (u, v) in the two other axes (in X/Y/Z order). Caps are fans of quads.
       */
      public Builder prism(int mat, int axis, int sides, float u, float v, float a0, float a1, float r0, float r1) {
         return this.prism(mat, axis, sides, u, v, a0, a1, r0, r1, 0.0F);
      }

      public Builder prism(int mat, int axis, int sides, float u, float v, float a0, float a1, float r0, float r1, float spin) {
         float[] ring0 = new float[sides * 3];
         float[] ring1 = new float[sides * 3];
         for (int i = 0; i < sides; i++) {
            double ang = Math.PI * 2.0 * i / sides + spin;
            float cu = (float)Math.cos(ang), sv = (float)Math.sin(ang);
            put(ring0, i, axis, a0, u + cu * r0, v + sv * r0);
            put(ring1, i, axis, a1, u + cu * r1, v + sv * r1);
         }
         float[] c0 = new float[3], c1 = new float[3];
         put(c0, 0, axis, a0, u, v);
         put(c1, 0, axis, a1, u, v);
         if (this.mirror) {
            for (int i = 0; i < sides; i++) {
               ring0[i * 3] = -ring0[i * 3];
               ring1[i * 3] = -ring1[i * 3];
            }
            c0[0] = -c0[0];
            c1[0] = -c1[0];
         }
         float cx = (c0[0] + c1[0]) / 2, cy = (c0[1] + c1[1]) / 2, cz = (c0[2] + c1[2]) / 2;
         for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            this.quadAbs(mat, ring0, i, ring0, j, ring1, j, ring1, i, cx, cy, cz);
         }
         // caps: quads (centre, i, i+1, i+1) - a triangle drawn as a quad with a repeated corner
         for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            this.quadAbs(mat, c0, 0, ring0, i, ring0, j, ring0, j, cx, cy, cz);
            this.quadAbs(mat, c1, 0, ring1, i, ring1, j, ring1, j, cx, cy, cz);
         }
         return this;
      }

      private static void put(float[] arr, int i, int axis, float a, float u, float v) {
         switch (axis) {
            case 0 -> {
               arr[i * 3] = a;
               arr[i * 3 + 1] = u;
               arr[i * 3 + 2] = v;
            }
            case 1 -> {
               arr[i * 3] = u;
               arr[i * 3 + 1] = a;
               arr[i * 3 + 2] = v;
            }
            default -> {
               arr[i * 3] = u;
               arr[i * 3 + 1] = v;
               arr[i * 3 + 2] = a;
            }
         }
      }

      /**
       * A thin flying surface (wing, fin, stabiliser) in the X/Z plane, with a faceted diamond airfoil: sharp leading
       * and trailing edges, thickest along a ridge at {@code ridge} of the chord. Planform corners in X/Z: leading edge
       * root (p0), leading edge tip (p1), trailing edge tip (p2), trailing edge root (p3). Thickness t0 at the root and
       * t1 at the tip. The top uses {@code topMat}, the underside {@code mat}.
       */
      public Builder airfoil(int mat, int topMat, float[] p0, float[] p1, float[] p2, float[] p3, float t0, float t1, float ridge) {
         float edge = 0.004F;
         float[] r0 = lerp2(p0, p3, ridge), r1 = lerp2(p1, p2, ridge);
         // front half: leading edge -> ridge
         this.slab(mat, topMat, p0, p1, r1, r0, edge, edge, t1, t0);
         // back half: ridge -> trailing edge
         this.slab(mat, topMat, r0, r1, p2, p3, t0, t1, edge, edge);
         return this;
      }

      private static float[] lerp2(float[] a, float[] b, float t) {
         return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
      }

      /** Quad planform a-b-c-d (X/Z) with a half thickness at each corner; top face gets topMat. */
      private void slab(int mat, int topMat, float[] a, float[] b, float[] c, float[] d, float ta, float tb, float tc, float td) {
         float[] p = {
            a[0], -ta / 2, a[1], b[0], -tb / 2, b[1], c[0], -tc / 2, c[1], d[0], -td / 2, d[1],
            a[0], ta / 2, a[1], b[0], tb / 2, b[1], c[0], tc / 2, c[1], d[0], td / 2, d[1]
         };
         for (int i = 0; i < 8; i++) p[i * 3] = this.mx(p[i * 3]);
         float cx = 0, cy = 0, cz = 0;
         for (int i = 0; i < 8; i++) {
            cx += p[i * 3];
            cy += p[i * 3 + 1];
            cz += p[i * 3 + 2];
         }
         cx /= 8;
         cy /= 8;
         cz /= 8;
         this.quadFixed(mat, p, 0, 1, 2, 3, cx, cy, cz); // bottom
         this.quadFixed(topMat, p, 4, 5, 6, 7, cx, cy, cz); // top
         this.quadFixed(mat, p, 0, 1, 5, 4, cx, cy, cz);
         this.quadFixed(mat, p, 1, 2, 6, 5, cx, cy, cz);
         this.quadFixed(mat, p, 2, 3, 7, 6, cx, cy, cz);
         this.quadFixed(mat, p, 3, 0, 4, 7, cx, cy, cz);
      }

      /**
       * A single flat glowing quad (visor, lights). Corners in any order around the edge; {@code nx/ny/nz} is the side
       * it should face (mirrored along with the geometry).
       */
      public Builder glow(int mat, float[] c, float nx, float ny, float nz) {
         float[] p = c.clone();
         for (int i = 0; i < 4; i++) p[i * 3] = this.mx(p[i * 3]);
         // a point behind the quad acts as the "centre" for the winding fix
         float cx = 0, cy = 0, cz = 0;
         for (int i = 0; i < 4; i++) {
            cx += p[i * 3];
            cy += p[i * 3 + 1];
            cz += p[i * 3 + 2];
         }
         cx = cx / 4 - this.mx(nx);
         cy = cy / 4 - ny;
         cz = cz / 4 - nz;
         this.quadFixed(mat, p, 0, 1, 2, 3, cx, cy, cz);
         return this;
      }

      /**
       * One imported triangle (drawn as a quad with its last corner doubled), with a smooth normal per corner. Points
       * and normals are read from {@code p}/{@code n} at offset {@code o} (3 corners x 3 floats), already in the
       * part's frame. {@code backface} also adds the inside, darker, so a cut-open shell does not look hollow.
       */
      public Builder tri(int mat, float[] p, float[] n, int o, boolean backface) {
         float ax = p[o], ay = p[o + 1], az = p[o + 2];
         float bx = p[o + 3], by = p[o + 4], bz = p[o + 5];
         float cx = p[o + 6], cy = p[o + 7], cz = p[o + 8];
         float fx = (by - ay) * (cz - az) - (bz - az) * (cy - ay);
         float fy = (bz - az) * (cx - ax) - (bx - ax) * (cz - az);
         float fz = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
         float len = (float)Math.sqrt(fx * fx + fy * fy + fz * fz);
         if (len < 1.0E-10F) return this;
         fx /= len;
         fy /= len;
         fz /= len;
         // the source winding should agree with its normals; if not, turn the triangle round
         float avg = fx * (n[o] + n[o + 3] + n[o + 6]) + fy * (n[o + 1] + n[o + 4] + n[o + 7]) + fz * (n[o + 2] + n[o + 5] + n[o + 8]);
         boolean flip = avg < 0;
         this.addTri(mat, p, n, o, flip, flip ? -fx : fx, flip ? -fy : fy, flip ? -fz : fz, false);
         if (backface) this.addTri(mat, p, n, o, !flip, flip ? fx : -fx, flip ? fy : -fy, flip ? fz : -fz, true);
         return this;
      }

      private void addTri(int mat, float[] p, float[] n, int o, boolean reverse, float fx, float fy, float fz, boolean inside) {
         this.ensure();
         int q = this.quads++;
         int[] order = reverse ? new int[]{0, 2, 1, 1} : new int[]{0, 1, 2, 2};
         float anx = Math.abs(fx), any = Math.abs(fy), anz = Math.abs(fz);
         for (int i = 0; i < 4; i++) {
            int c = o + order[i] * 3;
            float x = p[c], y = p[c + 1], z = p[c + 2];
            this.pos[q * 12 + i * 3] = x;
            this.pos[q * 12 + i * 3 + 1] = y;
            this.pos[q * 12 + i * 3 + 2] = z;
            float sgn = inside ? -1 : 1;
            float nx = n[c] * sgn, ny = n[c + 1] * sgn, nz = n[c + 2] * sgn;
            this.vnormal[q * 12 + i * 3] = nx;
            this.vnormal[q * 12 + i * 3 + 1] = ny;
            this.vnormal[q * 12 + i * 3 + 2] = nz;
            float u, w;
            if (anx >= any && anx >= anz) {
               u = z;
               w = y;
            } else if (any >= anz) {
               u = x;
               w = z;
            } else {
               u = x;
               w = y;
            }
            this.uv[q * 8 + i * 2] = u * UV_SCALE;
            this.uv[q * 8 + i * 2 + 1] = -w * UV_SCALE;
            this.shade[q * 4 + i] = inside ? 0.45F : Math.min(1.0F, 0.93F + 0.07F * ny);
         }
         this.normal[q * 3] = fx;
         this.normal[q * 3 + 1] = fy;
         this.normal[q * 3 + 2] = fz;
         this.material[q] = (byte)mat;
      }

      // ------------------------------------------------------------------------------------------------ internals

      private void quadFixed(int mat, float[] p, int a, int b, int c, int d, float cx, float cy, float cz) {
         this.quadAbs(mat, p, a, p, b, p, c, p, d, cx, cy, cz);
      }

      /** Adds a quad, flipping it if its normal points towards (cx, cy, cz). */
      private void quadAbs(int mat, float[] pa, int a, float[] pb, int b, float[] pc, int c, float[] pd, int d, float cx, float cy, float cz) {
         float ax = pa[a * 3], ay = pa[a * 3 + 1], az = pa[a * 3 + 2];
         float bx = pb[b * 3], by = pb[b * 3 + 1], bz = pb[b * 3 + 2];
         float qx = pc[c * 3], qy = pc[c * 3 + 1], qz = pc[c * 3 + 2];
         float dx = pd[d * 3], dy = pd[d * 3 + 1], dz = pd[d * 3 + 2];
         // Newell normal: robust for slightly non-planar quads and for triangles drawn as quads
         float nx = (ay - by) * (az + bz) + (by - qy) * (bz + qz) + (qy - dy) * (qz + dz) + (dy - ay) * (dz + az);
         float ny = (az - bz) * (ax + bx) + (bz - qz) * (bx + qx) + (qz - dz) * (qx + dx) + (dz - az) * (dx + ax);
         float nz = (ax - bx) * (ay + by) + (bx - qx) * (by + qy) + (qx - dx) * (qy + dy) + (dx - ax) * (dy + ay);
         float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (len < 1.0E-9F) return; // degenerate
         nx /= len;
         ny /= len;
         nz /= len;
         float mx = (ax + bx + qx + dx) / 4 - cx, my = (ay + by + qy + dy) / 4 - cy, mz = (az + bz + qz + dz) / 4 - cz;
         if (nx * mx + ny * my + nz * mz < 0) {
            // reverse: a d c b
            float tx = bx, ty = by, tz = bz;
            bx = dx;
            by = dy;
            bz = dz;
            dx = tx;
            dy = ty;
            dz = tz;
            nx = -nx;
            ny = -ny;
            nz = -nz;
         }
         this.ensure();
         int q = this.quads++;
         float[] v = {ax, ay, az, bx, by, bz, qx, qy, qz, dx, dy, dz};
         System.arraycopy(v, 0, this.pos, q * 12, 12);
         this.normal[q * 3] = nx;
         this.normal[q * 3 + 1] = ny;
         this.normal[q * 3 + 2] = nz;
         for (int i = 0; i < 4; i++) {
            this.vnormal[q * 12 + i * 3] = nx;
            this.vnormal[q * 12 + i * 3 + 1] = ny;
            this.vnormal[q * 12 + i * 3 + 2] = nz;
         }
         this.material[q] = (byte)mat;
         // planar UVs on the two axes the face is most spread along
         float anx = Math.abs(nx), any = Math.abs(ny), anz = Math.abs(nz);
         float jitter = hash(this.seed++) * 0.06F - 0.03F;
         for (int i = 0; i < 4; i++) {
            float x = v[i * 3], y = v[i * 3 + 1], z = v[i * 3 + 2];
            float u, w;
            if (anx >= any && anx >= anz) {
               u = z;
               w = y;
            } else if (any >= anz) {
               u = x;
               w = z;
            } else {
               u = x;
               w = y;
            }
            this.uv[q * 8 + i * 2] = u * UV_SCALE;
            this.uv[q * 8 + i * 2 + 1] = -w * UV_SCALE;
            // upward-facing faces catch a little more light, and every face gets a tiny tint of its own
            this.shade[q * 4 + i] = Math.min(1.0F, 0.9F + 0.1F * ny + jitter + 0.04F * (y * 4.0F % 1.0F));
         }
      }

      private static float hash(int i) {
         int h = i * 0x9E3779B1;
         h ^= h >>> 15;
         h *= 0x85EBCA77;
         h ^= h >>> 13;
         return (h & 0xFFFF) / 65535.0F;
      }

      private void ensure() {
         if (this.quads < this.material.length) return;
         int n = this.material.length * 2;
         this.pos = Arrays.copyOf(this.pos, n * 12);
         this.uv = Arrays.copyOf(this.uv, n * 8);
         this.shade = Arrays.copyOf(this.shade, n * 4);
         this.normal = Arrays.copyOf(this.normal, n * 3);
         this.vnormal = Arrays.copyOf(this.vnormal, n * 12);
         this.material = Arrays.copyOf(this.material, n);
      }

      public MechMesh build() {
         int n = this.quads;
         return new MechMesh(
            Arrays.copyOf(this.pos, n * 12),
            Arrays.copyOf(this.uv, n * 8),
            Arrays.copyOf(this.shade, n * 4),
            Arrays.copyOf(this.normal, n * 3),
            Arrays.copyOf(this.vnormal, n * 12),
            Arrays.copyOf(this.material, n),
            n
         );
      }
   }
}

package com.jfant.specter.cosmetic;

/**
 * Vector "sprites": closed outlines in a unit square (x right, y up, centred on 0,0, about -0.5..0.5), drawn as a
 * triangle fan from the centre so the centre and the rim can have different colours (soft gradients for free).
 *
 * <p>Adding a new particle or cosmetic look means adding an outline here and an id in {@link #ALL}; nothing else changes.
 */
public final class Shapes {
   public static final float[] CIRCLE_12 = circle(12);
   public static final float[] CIRCLE_24 = circle(24);
   public static final float[] CIRCLE_40 = circle(40);
   /** Sakura petal: wide body, a notch at the tip. */
   public static final float[] PETAL = petal();
   /** Playing card, rounded corners, 0.7 : 1. */
   public static final float[] CARD = roundedRect(0.35F, 0.5F, 0.07F, 4);
   public static final float[] HEART = heart();
   public static final float[] DIAMOND = new float[]{0.0F, 0.5F, 0.32F, 0.0F, 0.0F, -0.5F, -0.32F, 0.0F};
   public static final float[] SPADE = spade();
   public static final float[] CLUB = club();
   /** Water drop: round bottom, pointed top. */
   public static final float[] DROPLET = droplet();
   /** Ice/glass shard: an uneven sliver. */
   public static final float[] SHARD = new float[]{0.0F, 0.5F, 0.14F, 0.1F, 0.1F, -0.5F, -0.12F, -0.2F, -0.16F, 0.15F};
   /** Four-point star. */
   public static final float[] SPARK = star(4, 0.5F, 0.12F);
   /** A feather vane: narrow at the quill, widest two thirds up, rounded tip. */
   public static final float[] FEATHER = feather();

   public static final int ID_DOT = 0;
   public static final int ID_PETAL = 1;
   public static final int ID_CARD = 2;
   public static final int ID_DROPLET = 3;
   public static final int ID_SHARD = 4;
   public static final int ID_SPARK = 5;
   public static final int ID_FEATHER = 6;
   public static final int ID_HEART = 7;
   public static final int ID_DIAMOND = 8;
   public static final float[][] ALL = new float[][]{CIRCLE_12, PETAL, CARD, DROPLET, SHARD, SPARK, FEATHER, HEART, DIAMOND};

   private Shapes() {
   }

   public static float[] circle(int n) {
      float[] out = new float[n * 2];
      for (int i = 0; i < n; i++) {
         double a = Math.PI * 2.0 * i / n;
         out[i * 2] = (float)(Math.cos(a) * 0.5);
         out[i * 2 + 1] = (float)(Math.sin(a) * 0.5);
      }
      return out;
   }

   public static float[] star(int points, float outer, float inner) {
      float[] out = new float[points * 4];
      for (int i = 0; i < points * 2; i++) {
         double a = Math.PI * i / points + Math.PI / 2.0;
         float r = (i & 1) == 0 ? outer : inner;
         out[i * 2] = (float)(Math.cos(a) * r);
         out[i * 2 + 1] = (float)(Math.sin(a) * r);
      }
      return out;
   }

   public static float[] roundedRect(float hw, float hh, float r, int cornerSteps) {
      float[] out = new float[4 * (cornerSteps + 1) * 2];
      int k = 0;
      float[][] centres = {{hw - r, hh - r}, {-hw + r, hh - r}, {-hw + r, -hh + r}, {hw - r, -hh + r}};
      for (int c = 0; c < 4; c++) {
         for (int i = 0; i <= cornerSteps; i++) {
            double a = Math.PI / 2.0 * c + Math.PI / 2.0 * i / cornerSteps;
            out[k++] = centres[c][0] + (float)Math.cos(a) * r;
            out[k++] = centres[c][1] + (float)Math.sin(a) * r;
         }
      }
      return out;
   }

   private static float[] petal() {
      int n = 12;
      float[] out = new float[(n * 2 + 1) * 2];
      int k = 0;
      for (int i = 0; i < n; i++) {
         double u = (double)i / (n - 1);
         out[k++] = (float)petalWidth(u);
         out[k++] = (float)(u - 0.5);
      }
      out[k++] = 0.0F;
      out[k++] = 0.38F;
      for (int i = n - 1; i >= 0; i--) {
         double u = (double)i / (n - 1);
         out[k++] = (float)-petalWidth(u);
         out[k++] = (float)(u - 0.5);
      }
      return out;
   }

   private static double petalWidth(double u) {
      return 0.3 * Math.pow(Math.sin(Math.PI * u * 0.92 + 0.04), 0.65);
   }

   private static float[] heart() {
      int n = 28;
      float[] out = new float[n * 2];
      for (int i = 0; i < n; i++) {
         double t = Math.PI * 2.0 * i / n;
         double x = 16.0 * Math.pow(Math.sin(t), 3.0);
         double y = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
         out[i * 2] = (float)(x / 34.0);
         out[i * 2 + 1] = (float)(y / 34.0 + 0.05);
      }
      return out;
   }

   private static float[] spade() {
      float[] h = heart();
      float[] out = new float[h.length + 6];
      for (int i = 0; i < h.length; i += 2) {
         out[i] = h[i];
         out[i + 1] = -h[i + 1] + 0.1F;
      }
      // stem
      out[h.length] = 0.08F;
      out[h.length + 1] = -0.5F;
      out[h.length + 2] = -0.08F;
      out[h.length + 3] = -0.5F;
      out[h.length + 4] = 0.0F;
      out[h.length + 5] = -0.25F;
      return out;
   }

   private static float[] club() {
      // three lobes approximated by a scalloped outline
      int n = 36;
      float[] out = new float[n * 2];
      for (int i = 0; i < n; i++) {
         double a = Math.PI * 2.0 * i / n;
         double r = 0.26 + 0.1 * Math.max(0.0, Math.cos(3.0 * (a - Math.PI / 2.0)));
         out[i * 2] = (float)(Math.cos(a) * r);
         out[i * 2 + 1] = (float)(Math.sin(a) * r + 0.06);
      }
      return out;
   }

   private static float[] droplet() {
      int n = 20;
      float[] out = new float[n * 2];
      for (int i = 0; i < n; i++) {
         double a = Math.PI * 2.0 * i / n;
         double x = Math.cos(a) * 0.32;
         double y = Math.sin(a) * 0.32 - 0.12;
         if (y > -0.12) x *= 1.0 - (y + 0.12) / 0.62 * 0.9;
         if (Math.sin(a) > 0.0) y = -0.12 + Math.sin(a) * 0.62;
         out[i * 2] = (float)x;
         out[i * 2 + 1] = (float)y;
      }
      return out;
   }

   private static float[] feather() {
      int n = 14;
      float[] out = new float[n * 4];
      int k = 0;
      for (int i = 0; i < n; i++) {
         double t = (double)i / (n - 1);
         double y = -0.5 + t;
         double w = 0.18 * Math.sin(Math.min(1.0, t * 1.35) * Math.PI * 0.62) * (t > 0.85 ? Math.sqrt(Math.max(0.0, (1.0 - t) / 0.15)) : 1.0) + 0.012;
         out[k++] = (float)w;
         out[k++] = (float)y;
      }
      for (int i = n - 1; i >= 0; i--) {
         double t = (double)i / (n - 1);
         double y = -0.5 + t;
         double w = 0.13 * Math.sin(Math.min(1.0, t * 1.35) * Math.PI * 0.62) * (t > 0.85 ? Math.sqrt(Math.max(0.0, (1.0 - t) / 0.15)) : 1.0) + 0.012;
         out[k++] = (float)-w;
         out[k++] = (float)y;
      }
      return out;
   }
}

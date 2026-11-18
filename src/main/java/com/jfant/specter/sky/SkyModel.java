package com.jfant.specter.sky;

public final class SkyModel {
   private static final double[] RAYLEIGH = new double[]{0.0464, 0.108, 0.265};
   private static final double[] OZONE = new double[]{0.0083, 0.0241, 0.0011};
   private static final double MIE = 0.0252;

   private SkyModel() {
   }

   private static double airMass(double mu) {
      double elevation = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, mu))));
      return 1.0 / (Math.max(mu, 0.0) + 0.50572 * Math.pow(Math.max(elevation + 6.07995, 0.05), -1.6364));
   }

   private static double smoothstep(double a, double b, double x) {
      double t = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
      return t * t * (3.0 - 2.0 * t);
   }

   private static double miePhase(double c, double g) {
      double g2 = g * g;
      return 0.0795775 * (1.0 - g2) / Math.pow(Math.max(1.0 + g2 - 2.0 * g * c, 1.0E-4), 1.5);
   }

   private static void sunlight(double lx, double ly, double lz, double haze, double[] out) {
      double mass = airMass(ly);
      double fade = smoothstep(-0.1, 0.03, ly);

      for (int i = 0; i < 3; i++) {
         out[i] = Math.exp(-(RAYLEIGH[i] + 0.0252 * haze + OZONE[i]) * mass) * fade;
      }
   }

   private static void scatter(double dx, double dy, double dz, double lx, double ly, double lz, double haze, double[] out) {
      double c = dx * lx + dy * ly + dz * lz;
      double[] lit = new double[3];
      sunlight(lx, ly, lz, haze, lit);
      double twilight = smoothstep(-0.28, -0.02, ly) * (1.0 - smoothstep(-0.02, 0.12, ly));
      lit[0] += 0.04000000000000001 * twilight;
      lit[1] += 0.021 * twilight;
      lit[2] += 0.034999999999999996 * twilight;
      double mie = 0.0252 * haze;
      double mass = airMass(Math.max(dy, 0.0));
      mass /= 1.0 + mass * 0.16;
      double rp = 0.0596831 * (1.0 + c * c);
      double mp = miePhase(c, 0.76);

      for (int i = 0; i < 3; i++) {
         double tau = RAYLEIGH[i] + mie + OZONE[i];
         double phase = (RAYLEIGH[i] * rp + mie * mp) / tau;
         out[i] = phase * (1.0 - Math.exp(-tau * mass)) * lit[i] * 14.0;
      }
   }

   private static double tonemap(double x) {
      return Math.max(0.0, Math.min(1.0, x * (2.51 * x + 0.03) / (x * (2.43 * x + 0.59) + 0.14)));
   }

   private static double autoExposure(double sunY) {
      double dusk = (1.0 - smoothstep(-0.04, 0.3, sunY)) * smoothstep(-0.32, -0.04, sunY);
      return 1.15 + 1.0 * dusk;
   }

   public static int horizonColor(SkyParams p, double hx, double hz) {
      double len = Math.max(1.0E-6, Math.hypot(hx, hz));
      double dy = 0.035;
      double dx = hx / len * Math.sqrt(1.0 - dy * dy);
      double dz = hz / len * Math.sqrt(1.0 - dy * dy);
      double haze = p.haze * (1.0 + p.rain * 2.5);
      double[] col = new double[3];
      double[] moon = new double[3];
      scatter(dx, dy, dz, p.sunX, p.sunY, p.sunZ, haze, col);
      scatter(dx, dy, dz, p.moonX, p.moonY, p.moonZ, haze, moon);
      double[] moonTint = new double[]{0.7, 0.82, 1.2};

      for (int i = 0; i < 3; i++) {
         col[i] += moon[i] * p.moonBright * 0.004 * moonTint[i];
      }

      double up = Math.sqrt(dy);
      double[] nightLow = new double[]{0.0034, 0.0021, 0.0085};
      double[] nightHigh = new double[]{7.0E-4, 5.0E-4, 0.0021};

      for (int i = 0; i < 3; i++) {
         col[i] += nightLow[i] + (nightHigh[i] - nightLow[i]) * up;
      }

      double c = dx * p.sunX + dy * p.sunY + dz * p.sunZ;
      double[] sunLight = new double[3];
      sunlight(p.sunX, p.sunY, p.sunZ, haze, sunLight);
      double glow = (miePhase(c, 0.97) * 0.02 + miePhase(c, 0.8) * 0.01) * p.sunGlow;

      for (int i = 0; i < 3; i++) {
         col[i] += sunLight[i] * glow;
      }

      double low = smoothstep(-0.2, -0.02, p.sunY) * (1.0 - smoothstep(0.02, 0.42, p.sunY));
      if (low > 0.0 && p.sunsetStrength > 0.0F) {
         double sl = Math.max(1.0E-5, Math.hypot(p.sunX, p.sunZ));
         double toward = 0.5 + 0.5 * (dx * p.sunX + dz * p.sunZ) / (Math.hypot(dx, dz) * sl);
         double band = Math.pow(1.0 - dy, 4.0);
         double[] warm = new double[3];
         double m1 = smoothstep(0.55, 1.0, toward);
         double m2 = smoothstep(0.15, 0.6, toward);
         double[] pink = new double[]{0.95, 0.36, 0.55};
         double[] gold = new double[]{1.0, 0.55, 0.16};
         double[] violet = new double[]{0.42, 0.24, 0.8};

         for (int i = 0; i < 3; i++) {
            double w = pink[i] + (gold[i] - pink[i]) * m1;
            warm[i] = violet[i] + (w - violet[i]) * m2;
         }

         double strength = band * (0.25 + 0.75 * toward * toward) * low;

         for (int i = 0; i < 3; i++) {
            col[i] += warm[i] * warm[i] * strength * 0.3 * p.sunsetStrength;
            col[i] += warm[i] * Math.pow(1.0 - dy, 1.5) * toward * low * 0.06 * p.sunsetStrength;
         }
      }

      if (p.customMix > 0.0F) {
         double day = p.daylight;
         double near = Math.pow(Math.max(c, 0.0), 3.0) * low;
         double sunsetMix = Math.max(0.0, Math.min(1.0, near + low * Math.pow(1.0 - dy, 3.0) * 0.6));
         double gradient = Math.pow(dy, 0.6);

         for (int i = 0; i < 3; i++) {
            double custom = (p.horizon[i] + (p.zenith[i] - p.horizon[i]) * gradient) * (0.05 + 0.95 * day);
            custom += (p.sunset[i] - custom) * sunsetMix;
            col[i] += (custom * 0.9 - col[i]) * p.customMix;
         }
      }

      double storm = Math.max(0.0, Math.min(1.0, p.rain * 0.75 + p.thunder * 0.25));
      double l = 0.2126 * col[0] + 0.7152 * col[1] + 0.0722 * col[2];
      double[] grey = new double[]{0.92, 0.95, 1.0};
      double dim = 1.0 - storm * 0.55 - p.thunder * 0.2;

      for (int i = 0; i < 3; i++) {
         col[i] = (col[i] + (l * grey[i] - col[i]) * storm * 0.75) * dim + new double[]{0.55, 0.58, 0.75}[i] * p.flash * 0.6;
      }

      double exposure = p.exposure * autoExposure(p.sunY);
      double[] shown = new double[3];

      for (int i = 0; i < 3; i++) {
         shown[i] = Math.pow(tonemap(col[i] * exposure), 0.45454545454545453);
      }

      double luma = 0.2126 * shown[0] + 0.7152 * shown[1] + 0.0722 * shown[2];
      int rgb = 0;

      for (int i = 0; i < 3; i++) {
         double v = Math.max(0.0, luma + (shown[i] - luma) * p.saturation) * p.brightness;
         rgb = rgb << 8 | (int)Math.round(Math.max(0.0, Math.min(1.0, v)) * 255.0);
      }

      return rgb;
   }
}

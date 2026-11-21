package com.jfant.specter.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RealisticRainTest {
   @Test
   void groundSoaksInRainAndDriesSlowlyAfter() {
      double wet = 0.0;
      for (int i = 0; i < 25 * 20; i++) {
         wet = RealisticRain.stepWetness(wet, 1.0, 0.05, 25.0, 150.0);
      }
      assertEquals(1.0, wet, 1.0e-9, "fully wet after 25 s of rain");

      for (int i = 0; i < 75 * 20; i++) {
         wet = RealisticRain.stepWetness(wet, 0.0, 0.05, 25.0, 150.0);
      }
      assertEquals(0.5, wet, 1.0e-6, "half dry after half the dry time");

      for (int i = 0; i < 80 * 20; i++) {
         wet = RealisticRain.stepWetness(wet, 0.0, 0.05, 25.0, 150.0);
      }
      assertEquals(0.0, wet, 1.0e-9, "dry again");
   }

   @Test
   void lightRainOnlyWetsPartway() {
      double wet = 0.0;
      for (int i = 0; i < 2000; i++) {
         wet = RealisticRain.stepWetness(wet, 0.4, 0.05, 25.0, 150.0);
      }
      assertEquals(0.4, wet, 1.0e-9);
   }

@Test   void lessRainKeepsAboutThatShareOfColumnsEverywhere() {      int kept = 0;      for (int x = -100; x < 100; x++) {         for (int z = -100; z < 100; z++) {            if (RealisticRain.keepColumn(x, z, 0.45)) kept++;            assertEquals(RealisticRain.keepColumn(x, z, 0.45), RealisticRain.keepColumn(x, z, 0.45), "same column, same answer (no flicker)");         }      }      double share = kept / 40000.0;      assertTrue(Math.abs(share - 0.45) < 0.02, "kept share " + share);      assertTrue(RealisticRain.keepColumn(5, 7, 1.0), "1.0 keeps everything");   }
   @Test
   void whitenedRainKeepsItsShapeButLosesTheBlue() {
      // ARGB: a blue-grey streak texel (r 120, g 140, b 200, a 160).
      int texel = 160 << 24 | 120 << 16 | 140 << 8 | 200;
      int white = RealisticRain.whiten(texel, 1.0);
      int a = white >>> 24, r = white >> 16 & 0xFF, g = white >> 8 & 0xFF, b = white & 0xFF;
      assertEquals(160, a, "same opacity at 1.0");
      assertTrue(r >= 215 && g >= 215 && b >= 215, "near white");
      assertTrue(Math.abs(b - r) <= 8, "no blue cast");
      assertEquals(0, RealisticRain.whiten(0, 1.5) >>> 24, "transparent stays transparent");
      assertEquals(240, RealisticRain.whiten(texel, 1.5) >>> 24, "opacity scales alpha");
   }
}

package com.jfant.specter.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PingMathTest {
   @Test
   void fadeHoldsThenFallsToZero() {
      assertEquals(1.0F, PingMath.fade(0, 10_000, 600));
      assertEquals(1.0F, PingMath.fade(9_400, 10_000, 600));
      assertEquals(0.5F, PingMath.fade(9_700, 10_000, 600), 1.0E-4);
      assertEquals(0.0F, PingMath.fade(10_000, 10_000, 600));
      assertEquals(0.0F, PingMath.fade(12_000, 10_000, 600));
   }

   @Test
   void easingStartsAtZeroAndEndsAtOne() {
      assertEquals(0.0F, PingMath.easeOutCubic(0), 1.0E-6);
      assertEquals(1.0F, PingMath.easeOutCubic(1), 1.0E-6);
      assertEquals(0.0F, PingMath.easeOutBack(0), 1.0E-6);
      assertEquals(1.0F, PingMath.easeOutBack(1), 1.0E-6);
      assertTrue(PingMath.easeOutBack(0.7F) > 1.0F, "pop overshoots");
   }

   @Test
   void offScreenPointsArePinnedToTheEdgeOnTheirSide() {
      double[] out = new double[2];
      // Far to the right of a 1000x600 screen.
      PingMath.clampToEdge(5000, 300, 1000, 600, 20, out);
      assertEquals(980, out[0], 1.0E-6);
      assertEquals(300, out[1], 1.0E-6);
      // Far above and to the left.
      PingMath.clampToEdge(-1000, -1000, 1000, 600, 20, out);
      assertEquals(20, out[1], 1.0E-6);
      assertTrue(out[0] >= 20 && out[0] < 500);
      assertTrue(PingMath.inside(out[0], out[1], 1000, 600, 20));
      // Dead behind the camera (projects onto the centre).
      PingMath.clampToEdge(500, 300, 1000, 600, 20, out);
      assertEquals(500, out[0], 1.0E-6);
      assertEquals(580, out[1], 1.0E-6);
   }

   @Test
   void insideRespectsTheMargin() {
      assertTrue(PingMath.inside(500, 300, 1000, 600, 20));
      assertFalse(PingMath.inside(10, 300, 1000, 600, 20));
      assertFalse(PingMath.inside(500, 590, 1000, 600, 20));
   }

   @Test
   void gemShrinksOnScreenWithDistanceButStaysVisible() {
      // Angular size ~ world size / distance.
      double near = PingMath.gemSize(10) / 10;
      double mid = PingMath.gemSize(60) / 60;
      double far = PingMath.gemSize(300) / 300;
      assertTrue(near > mid && mid > far, "farther looks smaller");
      assertTrue(far > 0.015, "still visible at 300 blocks: " + far);
      assertTrue(PingMath.gemSize(300) > PingMath.gemSize(10), "world size grows with distance");
   }

   @Test
   void facesTowardTheLightAreBrighter() {
      float up = PingMath.shade(0, 1, 0, 0.35, 0.85, 0.4);
      float side = PingMath.shade(1, 0, 0, 0.35, 0.85, 0.4);
      float down = PingMath.shade(0, -1, 0, 0.35, 0.85, 0.4);
      assertTrue(up > side && side > down);
      assertEquals(0.42F, down, 1.0E-6, "turned away = ambient only, never black");
   }

   @Test
   void argbPacksAndClampsAlpha() {
      assertEquals(0xFFFF8000, PingMath.argb(255, 255, 128, 0));
      assertEquals(0x00FF8000, PingMath.argb(-5, 255, 128, 0));
      assertEquals(0xFF000000, PingMath.argb(999, 0, 0, 0));
   }

   @Test
   void nearUsesARadius() {
      assertTrue(PingMath.near(0, 0, 0, 1, 1, 0, 1.5));
      assertFalse(PingMath.near(0, 0, 0, 2, 0, 0, 1.5));
   }
}

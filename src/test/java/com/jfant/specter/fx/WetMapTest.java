package com.jfant.specter.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WetMapTest {
   /** What the shader does: r + g * 256 - 64 = height, b = kind (pixels are ARGB). */
   private static int[] decode(int abgr) {
      int r = abgr >> 16 & 0xFF, g = abgr >> 8 & 0xFF, b = abgr & 0xFF;
      return new int[] {r + g * 256 - 64, b};
   }

   @Test
   void heightsRoundTripAcrossTheWholeWorld() {
      for (int y : new int[] {-64, -1, 0, 63, 64, 200, 319, 320}) {
         int[] back = decode(WetMap.encode(y, WetMap.KIND_GROUND));
         assertEquals(y, back[0], "height " + y);
         assertEquals(WetMap.KIND_GROUND, back[1]);
      }
      assertEquals(WetMap.KIND_WATER, decode(WetMap.encode(62, WetMap.KIND_WATER))[1]);
      assertEquals(0xFF, WetMap.encode(10, WetMap.KIND_DRY) >>> 24, "opaque");
   }
}

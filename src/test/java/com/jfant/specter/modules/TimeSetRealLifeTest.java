package com.jfant.specter.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeSetRealLifeTest {
   @Test
   void wallClockMapsToMinecraftTicks() {
      assertEquals(0, TimeSet.ticksFor(6, 0, 0), "6 AM = sunrise tick 0");
      assertEquals(6000, TimeSet.ticksFor(12, 0, 0), "noon");
      assertEquals(7000, TimeSet.ticksFor(13, 0, 0), "1 PM real = 1 PM in game");
      assertEquals(12000, TimeSet.ticksFor(18, 0, 0), "6 PM");
      assertEquals(18000, TimeSet.ticksFor(0, 0, 0), "midnight");
      assertEquals(23500, TimeSet.ticksFor(5, 30, 0), "5:30 AM");
      assertEquals(6500, TimeSet.ticksFor(12, 30, 0));
   }

   @Test
   void clockReadsBackTheSameTime() {
      assertEquals("13:00", TimeSet.clock(TimeSet.ticksFor(13, 0, 0)));
      assertEquals("00:00", TimeSet.clock(TimeSet.ticksFor(0, 0, 0)));
      assertEquals("05:30", TimeSet.clock(TimeSet.ticksFor(5, 30, 0)));
   }
}

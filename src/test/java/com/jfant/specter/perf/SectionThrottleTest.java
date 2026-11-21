package com.jfant.specter.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

class SectionThrottleTest {
   private static final long A = 11L;
   private static final long B = 22L;
   private static final long INTERVAL = 200L;

   @Test
   void firstChangeInAQuietSectionShowsAtOnce() {
      SectionThrottle t = new SectionThrottle();
      assertTrue(t.allow(A, 1000, 1, INTERVAL));
      assertEquals(0, t.pendingCount());
   }

   @Test
   void moreChangesInTheSameFrameAreFree() {
      SectionThrottle t = new SectionThrottle();
      assertTrue(t.allow(A, 1000, 1, INTERVAL));
      assertTrue(t.allow(A, 1001, 1, INTERVAL), "section is already dirty this frame");
      assertEquals(0, t.pendingCount());
   }

   @Test
   void aClockCollapsesIntoOneRenderPerInterval() {
      SectionThrottle t = new SectionThrottle();
      LongArrayList rendered = new LongArrayList();
      int rebuilds = 0;

      // 60 fps for 2 seconds. A clock changes 3 blocks of section A every game tick (50 ms); the client
      // ticks (and flushes) every 50 ms too. Per frame, like Minecraft: packets, then ticks, then render.
      long nextTick = 0;
      for (long frame = 1; frame <= 120; frame++) {
         long now = frame * 1000 / 60;
         boolean dirty = false;
         if (now >= nextTick) {
            for (int block = 0; block < 3; block++) {
               dirty |= t.allow(A, now, frame, INTERVAL);
            }

            dirty |= t.flush(now, frame, rendered::add) > 0;
            nextTick += 50;
         }

         if (dirty) {
            rebuilds++;
         }
      }

      // Vanilla: 40 re-renders (one per clock tick). Throttled: one per 200 ms window = about 10.
      assertTrue(rebuilds <= 11, "re-renders: " + rebuilds);
      assertTrue(rebuilds >= 9, "re-renders: " + rebuilds);
   }

   @Test
   void theLastChangeIsAlwaysShown() {
      SectionThrottle t = new SectionThrottle();
      LongArrayList rendered = new LongArrayList();
      assertTrue(t.allow(A, 0, 1, INTERVAL));
      assertFalse(t.allow(A, 50, 2, INTERVAL), "held back");
      assertFalse(t.allow(A, 100, 3, INTERVAL), "still one pending");
      assertEquals(1, t.pendingCount());
      assertEquals(0, t.flush(150, 4, rendered::add), "not due yet");
      assertEquals(1, t.flush(200, 5, rendered::add));
      assertEquals(A, rendered.getLong(0));
      assertEquals(0, t.pendingCount());
   }

   @Test
   void sectionsAreIndependent() {
      SectionThrottle t = new SectionThrottle();
      assertTrue(t.allow(A, 0, 1, INTERVAL));
      assertFalse(t.allow(A, 50, 2, INTERVAL));
      assertTrue(t.allow(B, 50, 2, INTERVAL), "other section is quiet");
   }

   @Test
   void flushAllShowsEverythingHeldBack() {
      SectionThrottle t = new SectionThrottle();
      LongArrayList rendered = new LongArrayList();
      t.allow(A, 0, 1, INTERVAL);
      t.allow(B, 0, 1, INTERVAL);
      t.allow(A, 50, 2, INTERVAL);
      t.allow(B, 50, 2, INTERVAL);
      assertEquals(2, t.flushAll(rendered::add));
      assertEquals(2, rendered.size());
      assertEquals(0, t.pendingCount());
      assertTrue(t.allow(A, 60, 3, INTERVAL), "fresh after turning off and on");
   }

   @Test
   void quietSectionsAreForgotten() {
      SectionThrottle t = new SectionThrottle();
      t.allow(A, 0, 1, INTERVAL);
      t.allow(B, 0, 1, INTERVAL);
      t.allow(B, 50, 2, INTERVAL);
      assertEquals(2, t.trackedCount());
      t.prune(10_000, 2000);
      assertEquals(1, t.trackedCount(), "A is forgotten, B still has a pending render");
   }
}

package com.jfant.specter.perf;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.function.LongConsumer;

/**
 * Leading + trailing edge rate limit for chunk-section re-renders, keyed by a packed section position.
 *
 * <p>The first change in a quiet section is let through at once, so a single button press or lever flip
 * shows instantly. Further changes inside {@code interval} are held back and collapsed into ONE re-render
 * at the end of the interval, which then shows the newest state. A redstone clock that changes a section
 * 10-20 times a second therefore costs at most {@code 1000 / interval} re-renders a second, and the final
 * picture is always right.
 *
 * <p>Pure (no Minecraft classes): the caller passes the clock in, so it is unit tested directly.
 */
public final class SectionThrottle {
   private static final long NEVER = Long.MIN_VALUE / 4;

   /** Section -> time its last re-render was let through or flushed. */
   private final Long2LongOpenHashMap lastRender = new Long2LongOpenHashMap();
   /** Section -> frame its last re-render was let through or flushed (more changes in that frame are free). */
   private final Long2LongOpenHashMap lastRenderFrame = new Long2LongOpenHashMap();
   /** Section -> time the held-back re-render is due. */
   private final Long2LongOpenHashMap pending = new Long2LongOpenHashMap();
   private long lastPrune;

   public SectionThrottle() {
      this.lastRender.defaultReturnValue(NEVER);
      this.lastRenderFrame.defaultReturnValue(NEVER);
   }

   /**
    * A redstone-only change happened in {@code section}.
    *
    * @return true if vanilla may re-render now, false if the re-render was held back (it will come out of
    *     {@link #flush}).
    */
   public boolean allow(long section, long nowMs, long frame, long intervalMs) {
      if (this.lastRenderFrame.get(section) == frame) {
         // Already marked dirty for this frame; one more change costs nothing extra.
         return true;
      }

      if (!this.pending.containsKey(section) && nowMs - this.lastRender.get(section) >= intervalMs) {
         this.lastRender.put(section, nowMs);
         this.lastRenderFrame.put(section, frame);
         return true;
      }

      if (!this.pending.containsKey(section)) {
         this.pending.put(section, this.lastRender.get(section) + intervalMs);
      }

      return false;
   }

   /** Re-renders every held-back section whose time has come. */
   public int flush(long nowMs, long frame, LongConsumer render) {
      int flushed = 0;
      if (!this.pending.isEmpty()) {
         ObjectIterator<Long2LongMap.Entry> it = this.pending.long2LongEntrySet().fastIterator();

         while (it.hasNext()) {
            Long2LongMap.Entry e = it.next();
            if (e.getLongValue() <= nowMs) {
               long section = e.getLongKey();
               it.remove();
               this.lastRender.put(section, nowMs);
               this.lastRenderFrame.put(section, frame);
               render.accept(section);
               flushed++;
            }
         }
      }

      return flushed;
   }

   /** Re-renders every held-back section now (module turned off). */
   public int flushAll(LongConsumer render) {
      int flushed = this.pending.size();
      if (flushed > 0) {
         ObjectIterator<Long2LongMap.Entry> it = this.pending.long2LongEntrySet().fastIterator();

         while (it.hasNext()) {
            render.accept(it.next().getLongKey());
         }

         this.pending.clear();
      }

      this.lastRender.clear();
      this.lastRenderFrame.clear();
      return flushed;
   }

   /** Forgets quiet sections so the maps stay small. Cheap to call every tick. */
   public void prune(long nowMs, long keepMs) {
      if (nowMs - this.lastPrune >= 5000L) {
         this.lastPrune = nowMs;
         ObjectIterator<Long2LongMap.Entry> it = this.lastRender.long2LongEntrySet().fastIterator();

         while (it.hasNext()) {
            Long2LongMap.Entry e = it.next();
            long section = e.getLongKey();
            if (nowMs - e.getLongValue() > keepMs && !this.pending.containsKey(section)) {
               it.remove();
               this.lastRenderFrame.remove(section);
            }
         }
      }
   }

   /** World changed: everything held back belongs to chunks that are gone. */
   public void clear() {
      this.lastRender.clear();
      this.lastRenderFrame.clear();
      this.pending.clear();
   }

   public int pendingCount() {
      return this.pending.size();
   }

   public int trackedCount() {
      return this.lastRender.size();
   }
}

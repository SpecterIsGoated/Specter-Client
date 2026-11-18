package com.jfant.specter.render;

import com.jfant.specter.SpecterClientAddon;

public final class GuiProfile {
   private static final long REPORT_NANOS = 5000000000L;
   private static long since;
   private static long lastFrameStart;
   private static int frames;
   private static long frameNanos;
   private static long renderNanos;
   private static long textNanos;
   private static long shapeNanos;
   private static long rowNanos;
   private static long glyphs;
   private static long texts;
   private static long shapes;
   private static long rows;
   private static long renderStart;
   private static int depth;

   private GuiProfile() {
   }

   public static void beginFrame() {
      long now = System.nanoTime();
      if (since == 0L) {
         since = now;
         rowNanos = 0L;
         shapeNanos = 0L;
         textNanos = 0L;
         rows = 0L;
         shapes = 0L;
         texts = 0L;
         glyphs = 0L;
      }

      if (lastFrameStart != 0L && now - lastFrameStart < 1000000000L) {
         frameNanos = frameNanos + (now - lastFrameStart);
      }

      lastFrameStart = now;
      renderStart = now;
   }

   public static void endFrame(String theme) {
      long now = System.nanoTime();
      renderNanos = renderNanos + (now - renderStart);
      frames++;
      if (now - since >= 5000000000L && frames >= 2) {
         double f = frames;
         if (f * 1.0E9 / Math.max(1L, frameNanos) < 120.0) {
            SpecterClientAddon.LOG
               .info(
                  String.format(
                     "Specter GUI profile (%s): %.0f fps | frame %.2f ms, GUI render %.2f ms (text %.2f ms for %.0f texts / %.0f letters, shapes %.2f ms for %.0f, rows %.2f ms for %.0f) per frame",
                     theme,
                     f * 1.0E9 / Math.max(1L, frameNanos),
                     frameNanos / f / 1000000.0,
                     renderNanos / f / 1000000.0,
                     textNanos / f / 1000000.0,
                     texts / f,
                     glyphs / f,
                     shapeNanos / f / 1000000.0,
                     shapes / f,
                     rowNanos / f / 1000000.0,
                     rows / f
                  )
               );
         }

         since = now;
         frames = 0;
         rowNanos = 0L;
         shapeNanos = 0L;
         textNanos = 0L;
         renderNanos = 0L;
         frameNanos = 0L;
         rows = 0L;
         shapes = 0L;
         texts = 0L;
         glyphs = 0L;
      }
   }

   public static void closed() {
      since = 0L;
      lastFrameStart = 0L;
      frames = 0;
      rowNanos = 0L;
      shapeNanos = 0L;
      textNanos = 0L;
      renderNanos = 0L;
      frameNanos = 0L;
      rows = 0L;
      shapes = 0L;
      texts = 0L;
      glyphs = 0L;
   }

   public static boolean active() {
      return renderStart != 0L && lastFrameStart != 0L;
   }

   static void text(long nanos, int letters) {
      textNanos += nanos;
      texts++;
      glyphs += letters;
   }

   static long shapeStart() {
      return depth++ == 0 ? System.nanoTime() : 0L;
   }

   static void shapeEnd(long started) {
      depth--;
      if (started != 0L) {
         shapeNanos = shapeNanos + (System.nanoTime() - started);
         shapes++;
      }
   }

   public static void row(long nanos) {
      rowNanos += nanos;
      rows++;
   }
}

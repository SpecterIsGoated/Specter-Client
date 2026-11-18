package com.jfant.specter.render;

import com.jfant.specter.fx.WorldFx;
import com.jfant.specter.modules.Bioluminescence;
import com.jfant.specter.modules.SkyLyrics;
import com.jfant.specter.sky.SkyRenderer;

/**
 * What Specter does once the solid world (opaque terrain and entities) has been drawn and before anything see-through
 * (water, glass, clouds): copy the depth buffer for Sky Lyrics and Bioluminescence, and draw Atmosphere's cloud
 * shadows and clouds.
 *
 * In 1.21.11 this ran from the water-mask render type lookup; 26.x builds the frame differently, so
 * {@code LevelRendererSolidMixin} calls it at the real boundary inside LevelRenderer's main pass.
 */
public final class SolidWorldHooks {
   /** Whether anything needs the boundary this frame (if not, vanilla's single main pass is left alone). */
   public static boolean wanted() {
      return SkyLyrics.wantsSolidDepth() || SkyRenderer.wantsAfterSolid() || Bioluminescence.on();
   }

   public static void run() {
      SkyLyrics.captureSolidDepth();
      SkyRenderer.afterSolidTerrain();
      WorldFx.captureSolidDepth();
   }

   private SolidWorldHooks() {
   }
}

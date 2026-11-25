package com.jfant.specter.core.mixin;

import com.jfant.specter.render.QuadBatch;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps {@link QuadBatch#beginGroup()} exact: before anything else joins the GUI, the Specter shapes merged so far
 * are added first, so everything still stacks in the order it was drawn.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
   @Inject(
      method = {"addItem", "addText", "addPicturesInPictureState", "addGuiElement", "addBlitToCurrentLayer", "addGlyphToCurrentLayer", "nextStratum", "up"},
      at = @At("HEAD")
   )
   private void specter$flushMerged(CallbackInfo ci) {
      QuadBatch.flushGroup();
   }
}

package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.mixininterface.IGameRenderer;
import com.jfant.specter.core.utils.Utils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Gui.class})
public abstract class InGameHudMixin {
   @Shadow
   public abstract void onDisconnected();

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void onRender(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
      ((IGameRenderer)SpecterCore.mc.gameRenderer).meteor$flushGuiState();
      context.nextStratum();
      Profiler.get().push("specter_render_2d");
      Utils.unscaledProjection();
      SpecterCore.EVENT_BUS.post(Render2DEvent.get(context, context.guiWidth(), context.guiWidth(), tickCounter.getGameTimeDeltaPartialTick(true)));
      context.nextStratum();
      Utils.scaledProjection();
      Profiler.get().pop();
   }
}

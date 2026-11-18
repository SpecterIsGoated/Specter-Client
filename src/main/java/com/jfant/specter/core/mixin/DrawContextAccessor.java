package com.jfant.specter.core.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphics.ScissorStack;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({GuiGraphics.class})
public interface DrawContextAccessor {
   @Accessor("guiRenderState")
   GuiRenderState getState();

   @Accessor("scissorStack")
   ScissorStack getScissorStack();
}

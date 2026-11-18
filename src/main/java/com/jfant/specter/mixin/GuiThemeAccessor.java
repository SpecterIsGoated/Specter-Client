package com.jfant.specter.mixin;

import com.jfant.specter.core.gui.GuiTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   value = {GuiTheme.class},
   remap = false
)
public interface GuiThemeAccessor {
   @Mutable
   @Accessor("name")
   void specter$setName(String var1);
}

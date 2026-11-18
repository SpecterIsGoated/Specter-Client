package com.jfant.specter.core.mixin;

import com.jfant.specter.core.mixininterface.IText;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({Component.class})
public interface TextMixin extends IText {
   @Override
   default void meteor$invalidateCache() {
   }
}

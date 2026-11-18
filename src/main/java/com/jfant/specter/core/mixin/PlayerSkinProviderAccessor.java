package com.jfant.specter.core.mixin;

import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.SkinManager.TextureCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({SkinManager.class})
public interface PlayerSkinProviderAccessor {
   @Accessor("skinTextures")
   TextureCache meteor$getSkinCache();
}

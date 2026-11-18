/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
    @Shadow
    protected abstract boolean shouldInstantlyReplaceVisibleItem(ItemStack currentlyVisibleItem, ItemStack expectedItem, LocalPlayer player);

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float offHandHeight;

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 3), index = 0)
    private float modifyEquipProgressOffhand(float value, @Local(argsOnly = true, name = "player") LocalPlayer player) {
        return (shouldInstantlyReplaceVisibleItem(offHandItem, player.getOffhandItem(), player) ? 1 : 0) - offHandHeight;
    }
}

/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void onReleaseUsingHead(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime, CallbackInfoReturnable<Boolean> cir) {
        if (entity == mc.player) Utils.isReleasingTrident = true;
    }

    @Inject(method = "releaseUsing", at = @At("TAIL"))
    private void onReleaseUsingTail(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime, CallbackInfoReturnable<Boolean> cir) {
        if (entity == mc.player) Utils.isReleasingTrident = false;
    }

}

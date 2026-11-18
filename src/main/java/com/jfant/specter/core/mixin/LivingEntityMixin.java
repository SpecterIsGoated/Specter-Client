/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.DropItemsEvent;
import com.jfant.specter.core.events.entity.player.CanWalkOnFluidEvent;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
    private boolean onCanWalkOnFluid(boolean original, FluidState fluid) {
        if ((Object) this != mc.player) return original;
        CanWalkOnFluidEvent event = SpecterCore.EVENT_BUS.post(CanWalkOnFluidEvent.get(fluid));

        return event.walkOnFluid;
    }

    @Unique
    private boolean previousElytra = false;

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void onDropItem(ItemStack itemStack, boolean thrownFromHand, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
        if (level().isClientSide() && !itemStack.isEmpty()) {
            if (SpecterCore.EVENT_BUS.post(DropItemsEvent.get(itemStack)).isCancelled()) cir.setReturnValue(null);
        }
    }
}

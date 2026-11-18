/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.player.PlayerTickMovementEvent;
import com.jfant.specter.core.events.entity.player.SendMovementPacketsEvent;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.entity.fakeplayer.FakePlayerEntity;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    @Shadow
    public ClientInput input;

    public LocalPlayerMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void preAiStep(CallbackInfo ci) {
        SpecterCore.EVENT_BUS.post(PlayerTickMovementEvent.get());
    }

    // Sprint

    // Rotations

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void onSendPositionHead(CallbackInfo ci) {
        SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
    }

    @Inject(method = "sendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 1))
    private void onTickHasVehicleBeforeSendPackets(CallbackInfo ci) {
        SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Pre.get());
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void onSendPositionTail(CallbackInfo ci) {
        SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
    }

    @Inject(method = "sendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void onTickHasVehicleAfterSendPackets(CallbackInfo ci) {
        SpecterCore.EVENT_BUS.post(SendMovementPacketsEvent.Post.get());
    }

    @ModifyReturnValue(method = "pick", at = @At("RETURN"))
    private static HitResult onUpdateTargetedEntity(HitResult original, @Local(name = "blockHitResult") HitResult blockHitResult) {
        if (original instanceof EntityHitResult ehr && ehr.getEntity() instanceof FakePlayerEntity fakePlayer && fakePlayer.noHit) {
            return blockHitResult;
        }

        return original;
    }
}

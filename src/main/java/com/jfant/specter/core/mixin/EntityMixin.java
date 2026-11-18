/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.EntityMoveEvent;
import com.jfant.specter.core.events.entity.player.JumpVelocityMultiplierEvent;
import com.jfant.specter.core.events.entity.player.PlayerMoveEvent;
import com.jfant.specter.core.mixininterface.ICamera;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.entity.fakeplayer.FakePlayerEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @ModifyArgs(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void onPushAwayFrom(Args args, Entity entity) {
        // FakePlayerEntity
        if (entity instanceof FakePlayerEntity player && player.doNotPush) {
            args.set(0, 0.0);
            args.set(2, 0.0);
        }
    }

    @ModifyReturnValue(method = "getBlockJumpFactor", at = @At("RETURN"))
    private float onGetBlockJumpFactor(float original) {
        if ((Object) this == mc.player) {
            JumpVelocityMultiplierEvent event = SpecterCore.EVENT_BUS.post(JumpVelocityMultiplierEvent.get());
            return (original * event.multiplier);
        }

        return original;
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void onMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        if ((Object) this == mc.player) {
            SpecterCore.EVENT_BUS.post(PlayerMoveEvent.get(moverType, delta));
        } else {
            SpecterCore.EVENT_BUS.post(EntityMoveEvent.get((Entity) (Object) this, delta));
        }
    }

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void onIsInvisibleTo(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null) cir.setReturnValue(false);
    }

    @ModifyReturnValue(method = "getPose", at = @At("RETURN"))
    private Pose modifyGetPose(Pose original) {
        if ((Object) this != mc.player) return original;

        if (original == Pose.CROUCHING && !mc.player.isShiftKeyDown() && ((PlayerAccessor) mc.player).meteor$canChangeIntoPose(Pose.STANDING))
            return Pose.STANDING;
        return original;
    }

}

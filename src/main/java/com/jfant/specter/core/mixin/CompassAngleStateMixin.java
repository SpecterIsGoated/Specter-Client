/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.jfant.specter.core.SpecterCore.mc;

@Mixin(CompassAngleState.class)
public abstract class CompassAngleStateMixin {

}

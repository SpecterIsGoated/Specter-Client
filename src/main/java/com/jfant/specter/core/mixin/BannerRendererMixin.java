/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.jfant.specter.core.systems.modules.Modules;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.BannerBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
public abstract class BannerRendererMixin {
    @Shadow
    @Final
    private SpriteGetter sprites;

    @Shadow
    protected abstract BannerModel bannerModel(final BannerBlock.AttachmentType type);

    @Unique
    private static void renderPillar(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, BannerModel model, SpriteGetter sprites, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        SpriteId sprite = Sheets.BANNER_BASE;
        submitNodeCollector.submitModel(
            model,
            Unit.INSTANCE,
            poseStack,
            light,
            OverlayTexture.NO_OVERLAY,
            -1,
            sprite,
            sprites,
            0
        );
    }
}

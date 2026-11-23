/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.jfant.specter.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.MixinPlugin;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.render.RenderAfterWorldEvent;
import com.jfant.specter.core.renderer.MeteorRenderPipelines;
import com.jfant.specter.core.renderer.Renderer3D;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.render.CustomBannerGuiElementRenderer;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.profiling.Profiler;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Camera mainCamera;

    @Unique
    private Renderer3D renderer;

    @Unique
    private Renderer3D depthRenderer;

    @Unique
    private Renderer3D solidRenderer;

    @Unique
    private Renderer3D glowRenderer;

    @Unique
    private final PoseStack matrices = new PoseStack();

    /** Reused every frame (was a new Matrix4f per frame). */
    @Unique
    private final Matrix4f specter$inverseBob = new Matrix4f();

    @Shadow
    protected abstract void bobView(final CameraRenderState cameraState, final PoseStack poseStack);

    @Shadow
    protected abstract void bobHurt(final CameraRenderState cameraState, final PoseStack poseStack);

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>(Lnet/minecraft/client/renderer/state/gui/GuiRenderState;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;Ljava/util/List;)V"))
    private List<PictureInPictureRenderer<?>> meteor$addSpecialRenderers(List<PictureInPictureRenderer<?>> list) {
        List<PictureInPictureRenderer<?>> result = new ArrayList<>(list.size() + 1);
        result.addAll(list);
        result.add(new CustomBannerGuiElementRenderer(minecraft.getAtlasManager()));
        return result;
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
    private void onRenderLevel(CallbackInfo ci, @Local(name = "projectionMatrix") Matrix4f projectionMatrix, @Local(name = "cameraState") CameraRenderState cameraState, @Local(name = "worldPartialTicks") float worldPartialTicks, @Local(name = "bobStack") PoseStack bobStack) {
        if (!Utils.canUpdate()) return;

        Profiler.get().push(SpecterCore.MOD_ID + "_render");

        // Create renderer and event

        if (renderer == null)
            renderer = new Renderer3D(MeteorRenderPipelines.WORLD_COLORED_LINES, MeteorRenderPipelines.WORLD_COLORED);
        if (depthRenderer == null)
            depthRenderer = new Renderer3D(MeteorRenderPipelines.WORLD_COLORED_LINES_DEPTH, MeteorRenderPipelines.WORLD_COLORED_DEPTH);
        if (solidRenderer == null)
            solidRenderer = new Renderer3D(MeteorRenderPipelines.WORLD_COLORED_LINES_DEPTH, MeteorRenderPipelines.WORLD_COLORED_SOLID);
        if (glowRenderer == null)
            glowRenderer = new Renderer3D(MeteorRenderPipelines.WORLD_COLORED_LINES_DEPTH, MeteorRenderPipelines.WORLD_COLORED_GLOW);
        Render3DEvent event = Render3DEvent.get(bobStack, renderer, depthRenderer, worldPartialTicks, mainCamera.position().x, mainCamera.position().y, mainCamera.position().z);

        // Update model view matrix
        Matrix4fc modelViewMatrix = cameraState.viewRotationMatrix;
        RenderSystem.getModelViewStack().pushMatrix().mul(modelViewMatrix);

        matrices.pushPose();
        bobHurt(this.gameRenderState.levelRenderState.cameraRenderState, matrices);
        if (minecraft.options.bobView().get()) {
            bobView(this.gameRenderState.levelRenderState.cameraRenderState, matrices);
        }

        Matrix4f inverseBob = this.specter$inverseBob.set(matrices.last().pose()).invert();
        RenderSystem.getModelViewStack().mul(inverseBob);
        matrices.popPose();

        // Call utility classes (apply bob correction when Iris shaders are active)

        Matrix4fc correctedPosition = MixinPlugin.isIrisPresent && RenderUtils.isShaderPackInUse() ? new Matrix4f(modelViewMatrix).mul(inverseBob) : modelViewMatrix;
        RenderUtils.updateScreenCenter(projectionMatrix, correctedPosition);
        NametagUtils.onRender(modelViewMatrix);

        // Render

        event.solidRenderer = solidRenderer;
        event.glowRenderer = glowRenderer;
        renderer.begin();
        depthRenderer.begin();
        solidRenderer.begin();
        glowRenderer.begin();
        SpecterCore.EVENT_BUS.post(event);
        solidRenderer.render(bobStack);
        renderer.render(bobStack);
        depthRenderer.render(bobStack);
        glowRenderer.render(bobStack);

        // Revert model view matrix

        RenderSystem.getModelViewStack().popMatrix();

        Profiler.get().pop();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRenderLevelTail(CallbackInfo ci) {
        SpecterCore.EVENT_BUS.post(RenderAfterWorldEvent.get());
    }

}

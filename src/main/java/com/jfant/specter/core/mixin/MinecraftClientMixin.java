package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.entity.player.DoAttackEvent;
import com.jfant.specter.core.events.entity.player.DoItemUseEvent;
import com.jfant.specter.core.events.entity.player.ItemUseCrosshairTargetEvent;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.game.OpenScreenEvent;
import com.jfant.specter.core.events.game.ResolutionChangedEvent;
import com.jfant.specter.core.events.game.ResourcePacksReloadedEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.gui.WidgetScreen;
import com.jfant.specter.core.mixininterface.IMinecraftClient;
import com.jfant.specter.core.systems.config.Config;
import com.jfant.specter.core.utils.Utils;
import com.jfant.specter.core.utils.misc.CPSUtils;
import com.jfant.specter.core.utils.misc.MeteorStarscript;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.meteordev.starscript.Script;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {Minecraft.class},
   priority = 1001
)
public abstract class MinecraftClientMixin implements IMinecraftClient {
   @Unique
   private boolean doItemUseCalled;
   @Unique
   private boolean rightClick;
   @Unique
   private long lastTime;
   @Unique
   private boolean firstFrame;
   @Shadow
   public ClientLevel level;
   @Shadow
   @Final
   public MouseHandler mouseHandler;
   @Shadow
   @Final
   private Window window;
   @Shadow
   public Screen screen;
   @Shadow
   @Final
   public Options options;
   @Shadow
   @Nullable
   public MultiPlayerGameMode gameMode;
   @Shadow
   private int rightClickDelay;
   @Shadow
   @Nullable
   public LocalPlayer player;
   @Shadow
   @Final
   @Mutable
   private RenderTarget mainRenderTarget;
   @Unique
   private boolean isBreaking = false;

   @Shadow
   protected abstract void startUseItem();

   @Shadow
   protected abstract void continueAttack(boolean var1);

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      SpecterCore.INSTANCE.onInitializeClient();
      this.firstFrame = true;
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"tick"}
   )
   private void onPreTick(CallbackInfo info) {
      this.doItemUseCalled = false;
      Profiler.get().push("specter_pre_update");
      SpecterCore.EVENT_BUS.post(TickEvent.Pre.get());
      Profiler.get().pop();
      if (this.rightClick && !this.doItemUseCalled && this.gameMode != null) {
         this.startUseItem();
      }

      this.rightClick = false;
   }

   @Inject(
      at = {@At("TAIL")},
      method = {"tick"}
   )
   private void onTick(CallbackInfo info) {
      Profiler.get().push("specter_post_update");
      SpecterCore.EVENT_BUS.post(TickEvent.Post.get());
      Profiler.get().pop();
   }

   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAttack(CallbackInfoReturnable<Boolean> cir) {
      CPSUtils.onAttack();
      if (((DoAttackEvent)SpecterCore.EVENT_BUS.post(DoAttackEvent.get())).isCancelled()) {
         cir.cancel();
      }
   }

   @Inject(
      method = {"startUseItem"},
      at = {@At("HEAD")}
   )
   private void onDoItemUse(CallbackInfo info) {
      this.doItemUseCalled = true;
   }

   @Inject(
      method = {"disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V"},
      at = {@At("HEAD")}
   )
   private void onDisconnect(Screen screen, boolean transferring, boolean stopSound, CallbackInfo info) {
      if (this.level != null) {
         SpecterCore.EVENT_BUS.post(GameLeftEvent.get());
      }
   }

   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSetScreen(Screen screen, CallbackInfo info) {
      if (screen instanceof WidgetScreen) {
         screen.mouseMoved(this.mouseHandler.xpos() * this.window.getGuiScale(), this.mouseHandler.ypos() * this.window.getGuiScale());
      }

      OpenScreenEvent event = OpenScreenEvent.get(screen);
      SpecterCore.EVENT_BUS.post(event);
      if (event.isCancelled()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"startUseItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"
      )},
      cancellable = true
   )
   private void onDoItemUseBeforeHands(CallbackInfo ci) {
      if (((DoItemUseEvent)SpecterCore.EVENT_BUS.post(DoItemUseEvent.get())).isCancelled()) {
         ci.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"startUseItem"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;",
         ordinal = 1
      )}
   )
   private HitResult doItemUseMinecraftClientCrosshairTargetProxy(HitResult original) {
      return ((ItemUseCrosshairTargetEvent)SpecterCore.EVENT_BUS.post(ItemUseCrosshairTargetEvent.get(original))).target;
   }

   @ModifyReturnValue(
      method = {"reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;"},
      at = {@At("RETURN")}
   )
   private CompletableFuture<Void> onReloadResourcesNewCompletableFuture(CompletableFuture<Void> original) {
      return original.thenRun(() -> SpecterCore.EVENT_BUS.post(ResourcePacksReloadedEvent.get()));
   }

   @ModifyArg(
      method = {"updateTitle"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"
      )
   )
   private String setTitle(String original) {
      if (Config.get() != null && Config.get().customWindowTitle.get()) {
         String customTitle = Config.get().customWindowTitleText.get();
         Script script = MeteorStarscript.compile(customTitle);
         if (script != null) {
            String title = MeteorStarscript.run(script);
            if (title != null) {
               customTitle = title;
            }
         }

         return customTitle;
      } else {
         return original;
      }
   }

   @Inject(
      method = {"resizeDisplay"},
      at = {@At("TAIL")}
   )
   private void onResolutionChanged(CallbackInfo info) {
      SpecterCore.EVENT_BUS.post(ResolutionChangedEvent.get());
   }

   @Inject(
      method = {"runTick"},
      at = {@At("HEAD")}
   )
   private void onRender(CallbackInfo info) {
      long time = System.currentTimeMillis();
      if (this.firstFrame) {
         this.lastTime = time;
         this.firstFrame = false;
      }

      Utils.frameTime = (time - this.lastTime) / 1000.0;
      this.lastTime = time;
   }

   @Override
   public void meteor$rightClick() {
      this.rightClick = true;
   }

   @Override
   public void meteor$setFramebuffer(RenderTarget framebuffer) {
      this.mainRenderTarget = framebuffer;
   }
}

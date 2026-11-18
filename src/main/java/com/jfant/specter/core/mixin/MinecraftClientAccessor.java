package com.jfant.specter.core.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ResourceLoadStateTracker;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Minecraft.class})
public interface MinecraftClientAccessor {
   @Accessor("fps")
   static int meteor$getFps() {
      return 0;
   }

   @Mutable
   @Accessor("user")
   void meteor$setSession(User var1);

   @Accessor("reloadStateTracker")
   ResourceLoadStateTracker meteor$getResourceReloadLogger();

   @Accessor("missTime")
   int meteor$getAttackCooldown();

   @Accessor("missTime")
   void meteor$setAttackCooldown(int var1);

   @Invoker("startAttack")
   boolean meteor$leftClick();

   @Mutable
   @Accessor("profileKeyPairManager")
   void meteor$setProfileKeys(ProfileKeyPairManager var1);

   @Mutable
   @Accessor("userApiService")
   void meteor$setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("skinManager")
   void meteor$setSkinProvider(SkinManager var1);

   @Mutable
   @Accessor("playerSocialManager")
   void meteor$setSocialInteractionsManager(PlayerSocialManager var1);

   @Mutable
   @Accessor("reportingContext")
   void meteor$setAbuseReportContext(ReportingContext var1);

   @Mutable
   @Accessor("profileFuture")
   void meteor$setGameProfileFuture(CompletableFuture<ProfileResult> var1);

   @Mutable
   @Accessor("services")
   void meteor$setApiServices(Services var1);

   @Invoker("handleKeybinds")
   void meteor$handleInputEvents();
}

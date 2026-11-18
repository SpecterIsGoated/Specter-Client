package com.jfant.specter.core.systems.accounts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.mixin.FileCacheAccessor;
import com.jfant.specter.core.mixin.MinecraftClientAccessor;
import com.jfant.specter.core.mixin.PlayerSkinProviderAccessor;
import com.jfant.specter.core.utils.misc.ISerializable;
import com.jfant.specter.core.utils.misc.NbtException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ServicesKeyType;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.resources.SkinManager.TextureCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Services;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Util;

public abstract class Account<T extends Account<?>> implements ISerializable<T> {
   protected AccountType type;
   protected String name;
   protected final AccountCache cache;

   protected Account(AccountType type, String name) {
      this.type = type;
      this.name = name;
      this.cache = new AccountCache();
   }

   public abstract boolean fetchInfo();

   public boolean login() {
      YggdrasilAuthenticationService authenticationService = new YggdrasilAuthenticationService(SpecterCore.mc.getProxy());
      applyLoginEnvironment(authenticationService);
      return true;
   }

   public String getUsername() {
      return this.cache.username.isEmpty() ? this.name : this.cache.username;
   }

   public AccountType getType() {
      return this.type;
   }

   public AccountCache getCache() {
      return this.cache;
   }

   public static void setSession(User session) {
      MinecraftClientAccessor mca = (MinecraftClientAccessor)SpecterCore.mc;
      mca.meteor$setSession(session);
      YggdrasilAuthenticationService yggdrasilAuthenticationService = new YggdrasilAuthenticationService(SpecterCore.mc.getProxy());
      UserApiService apiService = yggdrasilAuthenticationService.createUserApiService(session.getAccessToken());
      mca.meteor$setUserApiService(apiService);
      mca.meteor$setSocialInteractionsManager(new PlayerSocialManager(SpecterCore.mc, apiService));
      mca.meteor$setProfileKeys(ProfileKeyPairManager.create(apiService, session, SpecterCore.mc.gameDirectory.toPath()));
      mca.meteor$setAbuseReportContext(ReportingContext.create(ReportEnvironment.local(), apiService));
      mca.meteor$setGameProfileFuture(
         CompletableFuture.supplyAsync(
            () -> SpecterCore.mc.services().sessionService().fetchProfile(SpecterCore.mc.getUser().getProfileId(), true), Util.ioPool()
         )
      );
   }

   public static void applyLoginEnvironment(YggdrasilAuthenticationService authService) {
      MinecraftClientAccessor mca = (MinecraftClientAccessor)SpecterCore.mc;
      SignatureValidator.from(authService.getServicesKeySet(), ServicesKeyType.PROFILE_KEY);
      TextureCache skinCache = ((PlayerSkinProviderAccessor)SpecterCore.mc.getSkinManager()).meteor$getSkinCache();
      Path skinCachePath = ((FileCacheAccessor)skinCache).meteor$getDirectory();
      mca.meteor$setApiServices(Services.create(authService, SpecterCore.mc.gameDirectory));
      mca.meteor$setSkinProvider(
         new SkinManager(
            skinCachePath,
            SpecterCore.mc.services(),
            new SkinTextureDownloader(SpecterCore.mc.getProxy(), SpecterCore.mc.getTextureManager(), SpecterCore.mc),
            SpecterCore.mc
         )
      );
   }

   @Override
   public CompoundTag toTag() {
      CompoundTag tag = new CompoundTag();
      tag.putString("type", this.type.name());
      tag.putString("name", this.name);
      tag.put("cache", this.cache.toTag());
      return tag;
   }

   public T fromTag(CompoundTag tag) {
      if (!tag.getString("name").isEmpty() && !tag.getCompound("cache").isEmpty()) {
         this.name = tag.getString("name").get();
         this.cache.fromTag(tag.getCompound("cache").get());
         return (T)this;
      } else {
         throw new NbtException();
      }
   }
}

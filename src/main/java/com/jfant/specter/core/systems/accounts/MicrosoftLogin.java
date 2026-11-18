package com.jfant.specter.core.systems.accounts;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.util.Util;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.jspecify.annotations.Nullable;

public class MicrosoftLogin {
   private static final MsaApplicationConfig APPLICATION_CONFIG = new MsaApplicationConfig("00000000402b5328", "service::user.auth.xboxlive.com::MBI_SSL");
   private static final AtomicReference<Future<?>> loginTask = new AtomicReference<>();
   private static final AtomicBoolean cancelled = new AtomicBoolean();

   private MicrosoftLogin() {
   }

   public static String getRefreshToken(Consumer<String> callback) {
      cancelLogin();
      cancelled.set(false);
      CompletableFuture<String> urlFuture = new CompletableFuture<>();
      loginTask.set(
         MeteorExecutor.executor
            .submit(
               () -> {
                  try {
                     JavaAuthManager authManager = JavaAuthManager.create(MinecraftAuth.createHttpClient())
                        .msaApplicationConfig(APPLICATION_CONFIG)
                        .login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>)deviceCode -> {
                           String urlString = deviceCode.getDirectVerificationUri();
                           urlFuture.complete(urlString);
                           Util.getPlatform().openUri(urlString);
                        });
                     MsaToken msaToken = (MsaToken)authManager.getMsaToken().getUpToDate();
                     if (!cancelled.get()) {
                        callback.accept(msaToken.getRefreshToken());
                     }
                  } catch (InterruptedException var4x) {
                     Thread.currentThread().interrupt();
                     if (!urlFuture.isDone()) {
                        urlFuture.completeExceptionally(var4x);
                     }

                     if (cancelled.get()) {
                        return;
                     }

                     SpecterCore.LOG.error("Error logging into Microsoft account", var4x);
                     callback.accept(null);
                  } catch (Exception var5) {
                     if (!urlFuture.isDone()) {
                        urlFuture.completeExceptionally(var5);
                     }

                     if (cancelled.get()) {
                        return;
                     }

                     SpecterCore.LOG.error("Error logging into Microsoft account", var5);
                     callback.accept(null);
                  }
               }
            )
      );

      try {
         return urlFuture.get();
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         throw new RuntimeException("Interrupted while starting Microsoft login", var3);
      } catch (ExecutionException var4) {
         throw new RuntimeException("Failed to start Microsoft login", var4.getCause());
      }
   }

   @Nullable
   public static MicrosoftLogin.LoginData login(String refreshToken) {
      try {
         JavaAuthManager authManager = JavaAuthManager.create(MinecraftAuth.createHttpClient()).msaApplicationConfig(APPLICATION_CONFIG).login(refreshToken);
         MsaToken msaToken = (MsaToken)authManager.getMsaToken().getUpToDate();
         MinecraftToken minecraftToken = (MinecraftToken)authManager.getMinecraftToken().getUpToDate();
         MinecraftProfile profile = (MinecraftProfile)authManager.getMinecraftProfile().getUpToDate();
         return new MicrosoftLogin.LoginData(minecraftToken.getToken(), msaToken.getRefreshToken(), profile.getId().toString(), profile.getName());
      } catch (Exception var5) {
         SpecterCore.LOG.error("Error logging into Microsoft account", var5);
         return null;
      }
   }

   public static void cancelLogin() {
      cancelled.set(true);
      Future<?> task = loginTask.getAndSet(null);
      if (task != null) {
         task.cancel(true);
      }
   }

   public record LoginData(String mcToken, String newRefreshToken, String uuid, String username) {
   }
}

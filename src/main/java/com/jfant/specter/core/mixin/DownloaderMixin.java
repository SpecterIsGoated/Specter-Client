package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({DownloadQueue.class})
public class DownloaderMixin {
   @Shadow
   @Final
   private Path cacheDir;

   @ModifyExpressionValue(
      method = {"method_55485"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"
      )}
   )
   private Path hookResolve(Path original, @Local(argsOnly = true) UUID id) {
      UUID accountId = SpecterCore.mc.getUser().getProfileId();
      if (accountId == null) {
         SpecterCore.LOG.warn("Failed to change resource pack download directory because the account id is null.");
         return original;
      } else {
         return this.cacheDir.resolve(accountId.toString()).resolve(id.toString());
      }
   }
}

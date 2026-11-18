package com.jfant.specter.core.utils.render;

import com.google.gson.Gson;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.systems.accounts.TexturesJson;
import com.jfant.specter.core.systems.accounts.UuidToProfileResponse;
import com.jfant.specter.core.utils.PostInit;
import com.jfant.specter.core.utils.network.Http;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public class PlayerHeadUtils {
   public static PlayerHeadTexture STEVE_HEAD;

   private PlayerHeadUtils() {
   }

   @PostInit
   public static void init() {
      STEVE_HEAD = new PlayerHeadTexture();
   }

   public static byte[] fetchHead(UUID id) {
      if (id == null) {
         return null;
      } else {
         String url = getSkinUrl(id);
         if (url == null) {
            return null;
         } else {
            try {
               return PlayerHeadTexture.downloadHead(url);
            } catch (IOException var3) {
               SpecterCore.LOG.error("Could not fetch player head for {}.", id, var3);
               return null;
            }
         }
      }
   }

   public static String getSkinUrl(UUID id) {
      UuidToProfileResponse res2 = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
         .exceptionHandler(e -> SpecterCore.LOG.error("Could not contact mojang session servers.", e))
         .sendJson(UuidToProfileResponse.class);
      if (res2 == null) {
         return null;
      } else {
         String base64Textures = res2.getPropertyValue("textures");
         if (base64Textures == null) {
            return null;
         } else {
            TexturesJson textures = new Gson().fromJson(new String(Base64.getDecoder().decode(base64Textures)), TexturesJson.class);
            return textures.textures.SKIN == null ? null : textures.textures.SKIN.url;
         }
      }
   }
}

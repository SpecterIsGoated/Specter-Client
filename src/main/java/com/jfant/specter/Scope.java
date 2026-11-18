package com.jfant.specter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class Scope {
   private Scope() {
   }

   public static String current(Minecraft mc) {
      ServerData server = mc.getCurrentServer();
      String host = server != null ? server.ip : (mc.isLocalServer() ? "singleplayer" : "unknown");
      String dimension = mc.level != null ? mc.level.dimension().identifier().toString() : "unknown";
      return sanitise(host) + "_" + sanitise(dimension);
   }

   public static String sanitise(String value) {
      return value.replaceAll("[^a-zA-Z0-9._-]", "_");
   }
}

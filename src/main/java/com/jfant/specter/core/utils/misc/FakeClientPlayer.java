package com.jfant.specter.core.utils.misc;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.PreInit;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;

public class FakeClientPlayer {
   private static ClientLevel world;
   private static Player player;
   private static PlayerInfo playerListEntry;
   private static UUID lastId;
   private static boolean needsNewEntry;

   private FakeClientPlayer() {
   }

   @PreInit
   public static void init() {
      SpecterCore.EVENT_BUS.subscribe(FakeClientPlayer.class);
   }

   public static Player getPlayer() {
      UUID id = SpecterCore.mc.getUser().getProfileId();
      if (player == null || !id.equals(lastId)) {
         if (world == null) {
            world = new ClientLevel(
               new ClientPacketListener(
                  SpecterCore.mc,
                  new Connection(PacketFlow.CLIENTBOUND),
                  new CommonListenerCookie(
                     null,
                     new GameProfile(SpecterCore.mc.getUser().getProfileId(), SpecterCore.mc.getUser().getName()),
                     null,
                     null,
                     null,
                     null,
                     SpecterCore.mc.getCurrentServer(),
                     null,
                     null,
                     null,
                     null,
                     null,
                     null,
                     false
                  )
               ),
               new ClientLevelData(Difficulty.NORMAL, false, false),
               world.dimension(),
               world.dimensionTypeRegistration(),
               1,
               1,
               null,
               false,
               0L,
               world.getSeaLevel()
            );
         }

         player = new RemotePlayer(world, new GameProfile(id, SpecterCore.mc.getUser().getName()));
         lastId = id;
         needsNewEntry = true;
      }

      return player;
   }

   public static PlayerInfo getPlayerListEntry() {
      if (playerListEntry == null || needsNewEntry) {
         playerListEntry = new PlayerInfo(new GameProfile(lastId, SpecterCore.mc.getUser().getName()), false);
         needsNewEntry = false;
      }

      return playerListEntry;
   }
}

package com.jfant.specter.core.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.SignedMessageChain.Encoder;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket.NodeBuilder;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientPacketListener.class})
public interface ClientPlayNetworkHandlerAccessor {
   @Accessor("serverChunkRadius")
   int meteor$getChunkLoadDistance();

   @Accessor("signedMessageEncoder")
   Encoder meteor$getMessagePacker();

   @Accessor("lastSeenMessages")
   LastSeenMessagesTracker meteor$getLastSeenMessagesCollector();

   @Accessor("registryAccess")
   Frozen meteor$getCombinedDynamicRegistries();

   @Accessor("enabledFeatures")
   FeatureFlagSet meteor$getEnabledFeatures();

   @Accessor("COMMAND_NODE_BUILDER")
   static NodeBuilder<ClientSuggestionProvider> meteor$getCommandNodeFactory() {
      return null;
   }
}

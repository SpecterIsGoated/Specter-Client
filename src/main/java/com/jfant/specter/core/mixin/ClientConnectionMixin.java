package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.world.ServerConnectEndEvent;
import com.jfant.specter.core.systems.proxies.Proxies;
import com.jfant.specter.core.systems.proxies.Proxy;
import com.jfant.specter.core.systems.proxies.ProxyType;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetSocketAddress;
import java.util.Iterator;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Connection.class})
public abstract class ClientConnectionMixin {
   @Inject(
      method = {"channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V",
         shift = Shift.BEFORE
      )},
      cancellable = true
   )
   private void onHandlePacket(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         Iterator<Packet<? super ClientGamePacketListener>> it = bundle.subPackets().iterator();

         while (it.hasNext()) {
            if (((PacketEvent.Receive)SpecterCore.EVENT_BUS.post(new PacketEvent.Receive(it.next(), (Connection)this))).isCancelled()) {
               it.remove();
            }
         }
      } else if (((PacketEvent.Receive)SpecterCore.EVENT_BUS.post(new PacketEvent.Receive(packet, (Connection)this))).isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"},
      at = {@At("HEAD")}
   )
   private static void onConnect(InetSocketAddress address, EventLoopGroupHolder backend, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
      SpecterCore.EVENT_BUS.post(ServerConnectEndEvent.get(address));
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      cancellable = true
   )
   private void onSendPacketHead(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci) {
      if (((PacketEvent.Send)SpecterCore.EVENT_BUS.post(new PacketEvent.Send(packet, (Connection)this))).isCancelled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = {@At("TAIL")}
   )
   private void onSendPacketTail(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci) {
      SpecterCore.EVENT_BUS.post(new PacketEvent.Sent(packet, (Connection)this));
   }

   @Inject(
      method = {"configureSerialization"},
      at = {@At("RETURN")}
   )
   private static void onAddHandlers(ChannelPipeline pipeline, PacketFlow side, boolean local, BandwidthDebugMonitor packetSizeLogger, CallbackInfo ci) {
      if (side == PacketFlow.CLIENTBOUND && !local) {
         Proxy proxy = Proxies.get().getEnabled();
         if (proxy != null) {
            switch ((ProxyType)proxy.type.get()) {
               case Socks4:
                  pipeline.addFirst(
                     new ChannelHandler[]{new Socks4ProxyHandler(new InetSocketAddress(proxy.address.get(), proxy.port.get()), proxy.username.get())}
                  );
                  break;
               case Socks5:
                  pipeline.addFirst(
                     new ChannelHandler[]{
                        new Socks5ProxyHandler(new InetSocketAddress(proxy.address.get(), proxy.port.get()), proxy.username.get(), proxy.password.get())
                     }
                  );
            }
         }
      }
   }
}

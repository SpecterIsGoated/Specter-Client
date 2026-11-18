package com.jfant.specter.core.mixin;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.ReceiveMessageEvent;
import com.jfant.specter.core.mixininterface.IChatHud;
import com.jfant.specter.core.mixininterface.IChatHudLine;
import com.jfant.specter.core.mixininterface.IChatHudLineVisible;
import com.jfant.specter.core.mixininterface.IMessageHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage.Line;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ChatComponent.class})
public abstract class ChatHudMixin implements IChatHud {
   @Shadow
   @Final
   Minecraft minecraft;
   @Shadow
   @Final
   private List<Line> trimmedMessages;
   @Shadow
   @Final
   private List<GuiMessage> allMessages;
   @Unique
   private int nextId;

   @Shadow
   public abstract void addMessage(Component var1);

   @Override
   public void meteor$add(Component message, int id) {
      this.nextId = id;
      this.addMessage(message);
      this.nextId = 0;
   }

   @Inject(
      method = {"addMessageToDisplayQueue"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V",
         shift = Shift.AFTER
      )}
   )
   private void onAddMessageAfterNewChatHudLineVisible(GuiMessage message, CallbackInfo ci) {
      ((IChatHudLine)this.trimmedMessages.getFirst()).meteor$setId(this.nextId);
   }

   @Inject(
      method = {"addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V",
         shift = Shift.AFTER
      )}
   )
   private void onAddMessageAfterNewChatHudLine(GuiMessage message, CallbackInfo ci) {
      ((IChatHudLine)this.allMessages.getFirst()).meteor$setId(this.nextId);
   }

   @ModifyExpressionValue(
      method = {"addMessageToDisplayQueue"},
      at = {@At(
         value = "NEW",
         target = "(ILnet/minecraft/util/FormattedCharSequence;Lnet/minecraft/client/GuiMessageTag;Z)Lnet/minecraft/client/GuiMessage$Line;"
      )}
   )
   private Line onAddMessage_modifyChatHudLineVisible(Line line, @Local(ordinal = 1) int j) {
      IMessageHandler handler = (IMessageHandler)this.minecraft.getChatListener();
      if (handler == null) {
         return line;
      } else {
         IChatHudLineVisible meteorLine = (IChatHudLineVisible)line;
         meteorLine.meteor$setSender(handler.meteor$getSender());
         meteorLine.meteor$setStartOfEntry(j == 0);
         return line;
      }
   }

   @ModifyExpressionValue(
      method = {"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"},
      at = {@At(
         value = "NEW",
         target = "(ILnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)Lnet/minecraft/client/GuiMessage;"
      )}
   )
   private GuiMessage onAddMessage_modifyChatHudLine(GuiMessage line) {
      IMessageHandler handler = (IMessageHandler)this.minecraft.getChatListener();
      if (handler == null) {
         return line;
      } else {
         ((IChatHudLine)line).meteor$setSender(handler.meteor$getSender());
         return line;
      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V"},
      cancellable = true
   )
   private void onAddMessage(
      Component message,
      MessageSignature signatureData,
      GuiMessageTag indicator,
      CallbackInfo ci,
      @Local(argsOnly = true) LocalRef<Component> messageRef,
      @Local(argsOnly = true) LocalRef<GuiMessageTag> indicatorRef
   ) {
      ReceiveMessageEvent event = (ReceiveMessageEvent)SpecterCore.EVENT_BUS.post(ReceiveMessageEvent.get(message, indicator, this.nextId));
      if (event.isCancelled()) {
         ci.cancel();
      } else {
         this.trimmedMessages.removeIf(msg -> ((IChatHudLine)msg).meteor$getId() == this.nextId && this.nextId != 0);

         for (int i = this.allMessages.size() - 1; i > -1; i--) {
            if (((IChatHudLine)this.allMessages.get(i)).meteor$getId() == this.nextId && this.nextId != 0) {
               this.allMessages.remove(i);
            }
         }

         if (event.isModified()) {
            messageRef.set(event.getMessage());
            indicatorRef.set(event.getIndicator());
         }
      }
   }
}

package com.jfant.specter.core.commands.commands;

import com.jfant.specter.core.commands.Command;
import com.jfant.specter.core.commands.arguments.FriendArgumentType;
import com.jfant.specter.core.commands.arguments.PlayerListEntryArgumentType;
import com.jfant.specter.core.systems.friends.Friend;
import com.jfant.specter.core.systems.friends.Friends;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;

public class FriendsCommand extends Command {
   public FriendsCommand() {
      super("friends", "Manages friends.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(literal("add").then(argument("player", PlayerListEntryArgumentType.create()).executes(context -> {
         GameProfile profile = PlayerListEntryArgumentType.get(context).getProfile();
         Friend friend = new Friend(profile.name(), profile.id());
         if (Friends.get().add(friend)) {
            ChatUtils.sendMsg(friend.hashCode(), ChatFormatting.GRAY, "Added (highlight)%s (default)to friends.".formatted(friend.getName()));
         } else {
            this.error("Already friends with that player.", new Object[0]);
         }

         return 1;
      })));
      builder.then(literal("remove").then(argument("friend", FriendArgumentType.create()).executes(context -> {
         Friend friend = FriendArgumentType.get(context);
         if (friend == null) {
            this.error("Not friends with that player.", new Object[0]);
            return 1;
         } else {
            if (Friends.get().remove(friend)) {
               ChatUtils.sendMsg(friend.hashCode(), ChatFormatting.GRAY, "Removed (highlight)%s (default)from friends.".formatted(friend.getName()));
            } else {
               this.error("Failed to remove that friend.", new Object[0]);
            }

            return 1;
         }
      })));
      builder.then(literal("list").executes(context -> {
         this.info("--- Friends ((highlight)%s(default)) ---", new Object[]{Friends.get().count()});
         Friends.get().forEach(friend -> ChatUtils.info("(highlight)%s".formatted(friend.getName())));
         return 1;
      }));
   }
}

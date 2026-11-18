package com.jfant.specter.staff;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class LeaveMessages {
   private static final long REMEMBER_MS = 10000L;
   private static final int MAX_REMEMBERED = 64;
   private static final Set<String> LEAVE_WORDS = Set.of("left", "leaves", "quit", "disconnected");
   private final ArrayDeque<LeaveMessages.Announcement> announcements = new ArrayDeque<>();
   private boolean serverPrints;

   public void reset() {
      this.announcements.clear();
      this.serverPrints = false;
   }

   public boolean serverPrints() {
      return this.serverPrints;
   }

   public void onMessage(Component message, long now) {
      List<String> inOrder = tokens(message.getString());
      if (!inOrder.isEmpty()) {
         Set<String> words = new HashSet<>(inOrder);
         boolean vanillaLeave = message.getContents() instanceof TranslatableContents content && "multiplayer.player.left".equals(content.getKey());
         boolean saysLeft = vanillaLeave || words.stream().anyMatch(LEAVE_WORDS::contains) || words.contains("logged") && words.contains("out");
         if (saysLeft) {
            if (vanillaLeave || String.join(" ", inOrder).contains("left the game")) {
               this.serverPrints = true;
            }

            this.announcements.addLast(new LeaveMessages.Announcement(words, now));
            this.trim(now);
         }
      }
   }

   public boolean seenFor(String name, long now) {
      List<String> nameWords = tokens(name);
      if (nameWords.size() != 1) {
         return false;
      } else {
         String word = nameWords.getFirst();
         this.trim(now);

         for (LeaveMessages.Announcement announcement : this.announcements) {
            if (announcement.words().contains(word)) {
               return true;
            }
         }

         return false;
      }
   }

   private void trim(long now) {
      while (!this.announcements.isEmpty() && (this.announcements.size() > 64 || now - this.announcements.peekFirst().time() > 10000L)) {
         this.announcements.removeFirst();
      }
   }

   private static List<String> tokens(String text) {
      String plain = Normalizer.normalize(text.replaceAll("(?i)§[0-9a-fk-or]", ""), Form.NFKD).toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>(Arrays.asList(plain.split("[^a-z0-9_]+")));
      out.removeIf(String::isEmpty);
      return out;
   }

   private record Announcement(Set<String> words, long time) {
   }
}

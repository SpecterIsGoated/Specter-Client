package com.jfant.specter.names;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class NameTable {
   public static final NameTable EMPTY = new NameTable(Map.of());
   public static final int MAX_NAME_LENGTH = 32;
   private final Map<String, String> aliases;

   public NameTable(Map<String, String> nameToAlias) {
      Map<String, String> table = new HashMap<>();

      for (Entry<String, String> entry : nameToAlias.entrySet()) {
         String name = entry.getKey();
         if (name != null && !name.isEmpty() && name.length() <= 32 && entry.getValue() != null) {
            table.putIfAbsent(name.toLowerCase(Locale.ROOT), entry.getValue());
         }
      }

      this.aliases = Map.copyOf(table);
   }

   public boolean isEmpty() {
      return this.aliases.isEmpty();
   }

   public boolean sameAs(NameTable other) {
      return other != null && this.aliases.equals(other.aliases);
   }

   public NameTable newSince(NameTable older) {
      Map<String, String> fresh = new HashMap<>();

      for (Entry<String, String> entry : this.aliases.entrySet()) {
         if (older == null || !entry.getValue().equals(older.aliases.get(entry.getKey()))) {
            fresh.put(entry.getKey(), entry.getValue());
         }
      }

      return fresh.isEmpty() ? EMPTY : new NameTable(fresh);
   }

   public static boolean isNameChar(char c) {
      return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
   }

   public static String core(String raw) {
      if (raw == null) {
         return null;
      } else {
         int start = 0;
         int end = raw.length();

         while (start < end && !isNameChar(raw.charAt(start))) {
            start++;
         }

         while (end > start && !isNameChar(raw.charAt(end - 1))) {
            end--;
         }

         if (start != end && end - start <= 32) {
            for (int i = start; i < end; i++) {
               if (!isNameChar(raw.charAt(i))) {
                  return null;
               }
            }

            return raw.substring(start, end);
         } else {
            return null;
         }
      }
   }

   public String aliasAt(String text, int start, int end) {
      int length = end - start;
      return length > 0 && length <= 32 ? this.aliases.get(text.substring(start, end).toLowerCase(Locale.ROOT)) : null;
   }

   public String replace(String text) {
      if (text != null && !text.isEmpty() && !this.isEmpty()) {
         List<NameTable.Match> matches = this.scan(text, new int[]{0, text.length()}, 1);
         if (matches.isEmpty()) {
            return text;
         } else {
            StringBuilder out = new StringBuilder(text.length() + 16);
            int position = 0;

            for (NameTable.Match match : matches) {
               out.append(text, position, match.start()).append(match.alias());
               position = match.end();
            }

            return out.append(text, position, text.length()).toString();
         }
      } else {
         return text;
      }
   }

   public boolean mentionsAny(String text) {
      return text != null && !this.isEmpty() && !text.isEmpty() && !this.scan(text, new int[]{0, text.length()}, 1).isEmpty();
   }

   public List<NameTable.Match> scan(String text, int[] segmentStarts, int segmentCount) {
      if (!this.isEmpty() && !text.isEmpty()) {
         List<NameTable.Match> found = null;
         int length = text.length();
         int segment = 0;
         int i = 0;

         while (i < length) {
            while (segment < segmentCount - 1 && segmentStarts[segment + 1] <= i) {
               segment++;
            }

            int segmentEnd = segmentStarts[segment + 1];
            char c = text.charAt(i);
            if (c == 167) {
               i += i + 1 < segmentEnd ? 2 : 1;
            } else if (!isNameChar(c)) {
               i++;
            } else {
               int wordEnd = i + 1;

               while (wordEnd < length && isNameChar(text.charAt(wordEnd))) {
                  wordEnd++;
               }

               String alias = this.aliasAt(text, i, wordEnd);
               if (alias != null) {
                  found = add(found, i, wordEnd, alias);
               } else if (segmentEnd < wordEnd) {
                  for (int part = segment + 1; part < segmentCount && segmentStarts[part] < wordEnd; part++) {
                     String ending = this.aliasAt(text, segmentStarts[part], wordEnd);
                     if (ending != null) {
                        found = add(found, segmentStarts[part], wordEnd, ending);
                        break;
                     }
                  }
               }

               i = wordEnd;
            }
         }

         return found != null ? found : List.of();
      } else {
         return List.of();
      }
   }

   private static List<NameTable.Match> add(List<NameTable.Match> found, int start, int end, String alias) {
      List<NameTable.Match> list = (List<NameTable.Match>)(found != null ? found : new ArrayList<>(4));
      list.add(new NameTable.Match(start, end, alias));
      return list;
   }

   public record Match(int start, int end, String alias) {
   }
}

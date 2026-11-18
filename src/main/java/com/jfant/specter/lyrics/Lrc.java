package com.jfant.specter.lyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lrc {
   public static final Lrc EMPTY = new Lrc(List.of());
   private static final Pattern STAMP = Pattern.compile("(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?");
   private static final Pattern OFFSET = Pattern.compile("offset\\s*:\\s*([+-]?\\d{1,7})\\s*");
   private static final Pattern WORD_STAMP = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
   private static final Pattern TAG = Pattern.compile("[A-Za-z#][A-Za-z0-9_ ]*:.*");
   private final List<Lrc.Line> lines;

   private Lrc(List<Lrc.Line> lines) {
      this.lines = lines;
   }

   public static Lrc parse(String text) {
      if (text != null && !text.isBlank()) {
         List<Lrc.Line> parsed = new ArrayList<>();
         List<Long> stamps = new ArrayList<>(4);
         long offset = 0L;

         for (String raw : text.split("\r\n|\r|\n")) {
            String line = raw.strip();
            if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
               line = line.substring(1).strip();
            }

            stamps.clear();
            int position = 0;

            while (position < line.length() && line.charAt(position) == '[') {
               int close = line.indexOf(93, position + 1);
               if (close < 0) {
                  break;
               }

               String tag = line.substring(position + 1, close).strip();
               Matcher stamp = STAMP.matcher(tag);
               if (stamp.matches()) {
                  stamps.add(millis(stamp));
               } else {
                  if (!TAG.matcher(tag).matches()) {
                     break;
                  }

                  Matcher shift = OFFSET.matcher(tag.toLowerCase(Locale.ROOT));
                  if (shift.matches()) {
                     offset = Long.parseLong(shift.group(1));
                  }
               }

               position = close + 1;
            }

            if (!stamps.isEmpty()) {
               String body = line.substring(position);
               String words = WORD_STAMP.matcher(body).replaceAll("").replaceAll("\\s+", " ").strip();
               long[] timed = wordStarts(body, words.isEmpty() ? 0 : words.split(" ").length);

               for (long stamp : stamps) {
                  long[] starts = null;
                  if (timed != null) {
                     long shift = stamp - stamps.getFirst() - offset;
                     starts = new long[timed.length];

                     for (int i = 0; i < timed.length; i++) {
                        starts[i] = timed[i] < 0L ? -1L : Math.max(0L, timed[i] + shift);
                     }
                  }

                  parsed.add(new Lrc.Line(Math.max(0L, stamp - offset), words, starts));
               }
            }
         }

         if (parsed.isEmpty()) {
            return EMPTY;
         } else {
            parsed.sort(Comparator.comparingLong(Lrc.Line::timeMs));
            return new Lrc(List.copyOf(parsed));
         }
      } else {
         return EMPTY;
      }
   }

   public boolean isEmpty() {
      return this.lines.isEmpty();
   }

   public int size() {
      return this.lines.size();
   }

   public Lrc.Line line(int index) {
      return this.lines.get(index);
   }

   public int indexAt(long timeMs) {
      int low = 0;
      int high = this.lines.size();

      while (low < high) {
         int mid = low + high >>> 1;
         if (this.lines.get(mid).timeMs() <= timeMs) {
            low = mid + 1;
         } else {
            high = mid;
         }
      }

      return low - 1;
   }

   private static long[] wordStarts(String body, int wordCount) {
      if (wordCount != 0 && body.indexOf(60) >= 0) {
         long[] starts = new long[wordCount];
         Arrays.fill(starts, -1L);
         Matcher stamp = WORD_STAMP.matcher(body);
         long pending = -1L;
         int word = 0;
         int timed = 0;
         int at = 0;
         boolean inWord = false;

         while (at < body.length()) {
            if (body.charAt(at) == '<' && stamp.find(at) && stamp.start() == at) {
               if (!inWord) {
                  pending = millis(stamp);
               }

               at = stamp.end();
            } else {
               if (Character.isWhitespace(body.charAt(at))) {
                  inWord = false;
               } else if (!inWord) {
                  inWord = true;
                  if (word >= wordCount) {
                     return null;
                  }

                  if (pending >= 0L) {
                     starts[word] = pending;
                     timed++;
                     pending = -1L;
                  }

                  word++;
               }

               at++;
            }
         }

         return word == wordCount && timed > 0 ? starts : null;
      } else {
         return null;
      }
   }

   private static long millis(Matcher stamp) {
      long minutes = Long.parseLong(stamp.group(1));
      long seconds = Long.parseLong(stamp.group(2));
      long millis = 0L;
      String fraction = stamp.group(3);
      if (fraction != null) {
         millis = Long.parseLong(fraction);
         if (fraction.length() == 1) {
            millis *= 100L;
         } else if (fraction.length() == 2) {
            millis *= 10L;
         }
      }

      return minutes * 60000L + seconds * 1000L + millis;
   }

   public record Line(long timeMs, String text, long[] wordStarts) {
      public Line(long timeMs, String text) {
         this(timeMs, text, null);
      }

      public boolean gap() {
         return this.text.isEmpty();
      }
   }
}

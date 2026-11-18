package com.jfant.specter;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import meteordevelopment.orbit.EventHandler;

public final class Memory {
   public static final int KEEP_JOINS = 2;
   public static final String LAST_JOIN = "# last-join ";
   public static final String COLUMN = "join";
   private static final File FILE = new File(new File(SpecterCore.FOLDER, "specter"), "memory-joins.txt");
   private static final List<File> FOLDERS = new CopyOnWriteArrayList<>();
   private static final Memory.Listener LISTENER = new Memory.Listener();
   private static boolean subscribed;
   private static int join = -1;
   private static int start;

   private Memory() {
   }

   public static void init() {
      if (!subscribed) {
         subscribed = true;
         SpecterCore.EVENT_BUS.subscribe(LISTENER);
      }
   }

   public static void folder(File folder) {
      if (!FOLDERS.contains(folder)) {
         FOLDERS.add(folder);
      }
   }

   public static synchronized int join() {
      read();
      return join;
   }

   public static synchronized int start() {
      read();
      return start;
   }

   public static boolean expired(int stamp) {
      return join() - stamp >= 2;
   }

   public static int stamp(String text) {
      try {
         return Integer.parseInt(text.trim());
      } catch (NullPointerException | NumberFormatException var2) {
         return start();
      }
   }

   private static void read() {
      if (join < 0) {
         join = 0;
         start = -1;
         if (FILE.isFile()) {
            try {
               for (String line : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8)) {
                  int eq = line.indexOf(61);
                  if (eq >= 0) {
                     String key = line.substring(0, eq).trim();
                     int value = Integer.parseInt(line.substring(eq + 1).trim());
                     if (key.equals("join")) {
                        join = value;
                     } else if (key.equals("start")) {
                        start = value;
                     }
                  }
               }
            } catch (NumberFormatException | IOException var5) {
               SpecterClientAddon.LOG.warn("Specter: could not read {}: {}", FILE, var5.toString());
            }
         }

         if (start < 0) {
            start = join + 1;
         }
      }
   }

   private static void write() {
      try {
         FILE.getParentFile().mkdirs();
         Files.writeString(FILE.toPath(), "join=" + join + "\nstart=" + start + "\n", StandardCharsets.UTF_8);
      } catch (IOException var1) {
         SpecterClientAddon.LOG.warn("Specter: could not save {}: {}", FILE, var1.toString());
      }
   }

   private static void sweep() {
      int deleted = 0;

      for (File folder : FOLDERS) {
         File[] files = folder.listFiles((dir, name) -> name.endsWith(".csv") || name.endsWith(".txt"));
         if (files != null) {
            for (File file : files) {
               if (expired(lastJoin(file)) && file.delete()) {
                  deleted++;
               }
            }
         }
      }

      if (deleted > 0) {
         SpecterClientAddon.LOG.info("Specter: forgot {} chunk finder file(s) not seen in {} joins", deleted, 2);
      }
   }

   private static int lastJoin(File file) {
      try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
         for (int i = 0; i < 4; i++) {
            String line = reader.readLine();
            if (line == null) {
               return start();
            }

            if (line.startsWith("# last-join ")) {
               return stamp(line.substring("# last-join ".length()));
            }
         }

         return start();
      } catch (IOException var7) {
         return join();
      }
   }

   private static final class Listener {
      @EventHandler(
         priority = 200
      )
      private void onGameJoined(GameJoinedEvent event) {
         synchronized (Memory.class) {
            Memory.read();
            Memory.join++;
            Memory.write();
         }

         Thread thread = new Thread(Memory::sweep, "Specter memory sweep");
         thread.setDaemon(true);
         thread.start();
      }
   }
}

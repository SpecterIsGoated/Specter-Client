package com.jfant.specter.phone.apps.games;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The games in Donut Games. {@code GameRegistry.register(info, MyGame::new)} adds one; it shows up in the lobby. */
public final class GameRegistry {
   public record Entry(PhoneGame.Info info, Supplier<? extends PhoneGame> factory) {
   }

   private static final List<Entry> GAMES = new ArrayList<>();

   static {
      register(SnakeGame.INFO, SnakeGame::new);
      register(MinesweeperGame.INFO, MinesweeperGame::new);
      register(TicTacToeGame.INFO, TicTacToeGame::new);
      register(ReactionGame.INFO, ReactionGame::new);
      register(MemoryGame.INFO, MemoryGame::new);
   }

   private GameRegistry() {
   }

   public static synchronized void register(PhoneGame.Info info, Supplier<? extends PhoneGame> factory) {
      GAMES.removeIf(e -> e.info().id().equals(info.id()));
      GAMES.add(new Entry(info, factory));
   }

   public static synchronized List<Entry> all() {
      return List.copyOf(GAMES);
   }
}

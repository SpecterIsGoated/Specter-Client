package com.jfant.specter.lyrics;

public record Lyrics(Lyrics.Status status, Lrc synced) {
   public static final Lyrics LOADING = new Lyrics(Lyrics.Status.LOADING, Lrc.EMPTY);
   public static final Lyrics UNSYNCED = new Lyrics(Lyrics.Status.UNSYNCED, Lrc.EMPTY);
   public static final Lyrics INSTRUMENTAL = new Lyrics(Lyrics.Status.INSTRUMENTAL, Lrc.EMPTY);
   public static final Lyrics NOT_FOUND = new Lyrics(Lyrics.Status.NOT_FOUND, Lrc.EMPTY);
   public static final Lyrics UNAVAILABLE = new Lyrics(Lyrics.Status.UNAVAILABLE, Lrc.EMPTY);

   public static enum Status {
      LOADING,
      SYNCED,
      UNSYNCED,
      INSTRUMENTAL,
      NOT_FOUND,
      UNAVAILABLE;
   }
}

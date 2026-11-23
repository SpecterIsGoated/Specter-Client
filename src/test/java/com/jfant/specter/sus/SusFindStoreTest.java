package com.jfant.specter.sus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class SusFindStoreTest {
   private static long[] chunks(int... xz) {
      long[] out = new long[xz.length / 2];
      for (int i = 0; i < out.length; i++) out[i] = ChunkPos.pack(xz[i * 2], xz[i * 2 + 1]);
      return out;
   }

   @Test
   void sameSpotFlaggedTwiceIsOneFind() {
      SusFindStore store = new SusFindStore();
      store.ingest(SusFind.SUS_CHUNKS, chunks(10, 10, 11, 10), 1, 8, 1000L, 5);
      SusFindStore.Result again = store.ingest(SusFind.SUS_CHUNKS, chunks(10, 10, 11, 10), 1, 8, 2000L, 5);
      assertEquals(1, store.size());
      assertTrue(again.created().isEmpty());
      assertEquals(2000L, store.snapshot().get(0).lastSeen);
      assertEquals(1, store.snapshot().get(0).joins, "same join does not count twice");
   }

   @Test
   void nearbyAreasMergeFarAreasStaySeparate() {
      SusFindStore store = new SusFindStore();
      store.ingest(SusFind.SUS_CHUNKS_V2, chunks(0, 0), 0, 8, 1L, 1);
      store.ingest(SusFind.SUS_CHUNKS_V2, chunks(6, 0), 0, 8, 2L, 1);
      assertEquals(1, store.size(), "6 chunks apart is inside an 8-chunk merge radius");
      store.ingest(SusFind.SUS_CHUNKS_V2, chunks(40, 40), 0, 8, 3L, 1);
      assertEquals(2, store.size());
   }

   @Test
   void bothDetectorsConfirm() {
      SusFindStore store = new SusFindStore();
      store.ingest(SusFind.SUS_CHUNKS, chunks(3, 3), 1, 8, 1L, 1);
      assertEquals(SusFind.Confidence.UNCERTAIN, store.snapshot().get(0).confidence());
      SusFindStore.Result r = store.ingest(SusFind.SUS_CHUNKS_V2, chunks(4, 3), 0, 8, 2L, 1);
      assertEquals(1, r.confirmed().size());
      assertEquals(SusFind.Confidence.CONFIRMED, store.snapshot().get(0).confidence());
   }

   @Test
   void flaggedAgainOnLaterJoinConfirms() {
      SusFindStore store = new SusFindStore();
      store.ingest(SusFind.SUS_CHUNKS, chunks(3, 3), 1, 8, 1L, 1);
      store.ingest(SusFind.SUS_CHUNKS, chunks(3, 3), 1, 8, 2L, 2);
      assertEquals(2, store.snapshot().get(0).joins);
      assertEquals(SusFind.Confidence.CONFIRMED, store.snapshot().get(0).confidence());
   }

   @Test
   void findsThatGrowTogetherMerge() {
      SusFindStore store = new SusFindStore();
      store.ingest(SusFind.SUS_CHUNKS, chunks(0, 0), 1, 3, 1L, 1);
      store.ingest(SusFind.SUS_CHUNKS, chunks(10, 0), 1, 3, 1L, 1);
      assertEquals(2, store.size());
      // a strip that links both
      store.ingest(SusFind.SUS_CHUNKS, chunks(2, 0, 4, 0, 6, 0, 8, 0), 1, 3, 2L, 1);
      assertEquals(1, store.size());
      assertEquals(1, store.snapshot().get(0).id, "the older id survives");
   }

   @Test
   void groupsLinkWithinGap() {
      List<?> g = SusFindStore.groups(chunks(0, 0, 2, 0, 5, 0));
      assertEquals(2, g.size());
   }

   @Test
   void saveAndLoadRoundTrip() throws Exception {
      SusFindStore store = new SusFindStore();
      store.ingest(SusFind.SUS_CHUNKS, chunks(-5, 7, -4, 7, -4, 8), 2, 8, 123L, 4);
      Path dir = Files.createTempDirectory("susfinds");
      Path file = dir.resolve("x.json");
      store.save(file);
      SusFindStore loaded = new SusFindStore();
      loaded.load(file);
      SusFind f = loaded.snapshot().get(0);
      assertEquals(3, f.size());
      assertEquals(2, f.strength);
      assertEquals(-5, f.minX);
      assertEquals(8, f.maxZ);
      // next id continues after the loaded ones
      loaded.ingest(SusFind.SUS_CHUNKS, chunks(500, 500), 0, 8, 1L, 4);
      assertTrue(loaded.snapshot().stream().anyMatch(x -> x.id == 2));
   }
}

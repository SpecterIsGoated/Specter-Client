package com.jfant.specter.core.gui.screens.settings.base;

import com.jfant.specter.core.utils.Utils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SortingHelper {
   private static final Comparator<SortingHelper.Entry<?>> FILTER_COMPARATOR = Comparator.comparingInt(SortingHelper.Entry::distance);

   private SortingHelper() {
   }

   public static <T> Iterable<T> sort(Iterable<T> registry, Predicate<T> filter, Function<T, String[]> nameFunction, String filterText) {
      return sortInternal(registry, filter, nameFunction, filterText, null);
   }

   public static <T> Iterable<T> sortWithPriority(
      Iterable<T> registry, Predicate<T> filter, Function<T, String[]> nameFunction, String filterText, Comparator<T> comparator
   ) {
      return sortInternal(registry, filter, nameFunction, filterText, comparator);
   }

   private static <T> Iterable<T> sortInternal(
      Iterable<T> registry, Predicate<T> filter, Function<T, String[]> nameFunction, String filterText, @Nullable Comparator<T> comparator
   ) {
      if (filterText.isBlank()) {
         if (comparator == null) {
            return filtering(registry, filter);
         } else {
            List<T> list = createList(registry);

            for (T value : registry) {
               if (filter.test(value)) {
                  list.add(value);
               }
            }

            list.sort(comparator);
            return list;
         }
      } else {
         List<SortingHelper.Entry<T>> list = createList(registry);

         for (T valuex : registry) {
            if (filter.test(valuex)) {
               String[] names = nameFunction.apply(valuex);
               int bestWords = 0;
               int bestDistance = 2147483647;
               float relevancy = 0.0F;

               for (String name : names) {
                  int words = Utils.searchInWords(name, filterText);
                  int distance = Utils.searchLevenshteinDefault(name, filterText, false);
                  bestWords = Math.max(bestWords, words);
                  bestDistance = Math.min(bestDistance, distance);
                  relevancy = Math.max(relevancy, 1.0F - (float)distance / name.length());
               }

               if (bestWords > 0 || relevancy >= 0.5F) {
                  list.add(new SortingHelper.Entry<>(valuex, bestDistance));
               }
            }
         }

         Comparator<SortingHelper.Entry<T>> entryComparator = comparator != null
            ? Comparator.comparing(SortingHelper.Entry::value, comparator).thenComparing(filterComparator())
            : filterComparator();
         list.sort(entryComparator);
         return iterate(list);
      }
   }

   private static <T> List<T> createList(Iterable<?> iterable) {
      if (iterable instanceof IdMap<?> indexed) {
         return new ObjectArrayList(indexed.size());
      } else {
         return iterable instanceof Collection<?> collection ? new ObjectArrayList(collection.size()) : new ObjectArrayList();
      }
   }

   private static <T> Comparator<SortingHelper.Entry<T>> filterComparator() {
      return (Comparator<SortingHelper.Entry<T>>)FILTER_COMPARATOR;
   }

   private static <T> Iterable<T> iterate(List<SortingHelper.Entry<T>> sortedList) {
      return new Iterable<T>() {
         @NotNull
         @Override
         public Iterator<T> iterator() {
            return new Iterator<T>() {
               private final Iterator<SortingHelper.Entry<T>> it = sortedList.iterator();

               @Override
               public boolean hasNext() {
                  return this.it.hasNext();
               }

               @Override
               public T next() {
                  return this.it.next().value();
               }
            };
         }
      };
   }

   private static <T> Iterable<T> filtering(Iterable<T> iterable, Predicate<T> filter) {
      return new Iterable<T>() {
         @NotNull
         @Override
         public Iterator<T> iterator() {
            throw new UnsupportedOperationException("iterator() not supported by this Iterable, use forEach() instead.");
         }

         @Override
         public void forEach(Consumer<? super T> action) {
            for (T value : iterable) {
               if (filter.test(value)) {
                  action.accept(value);
               }
            }
         }
      };
   }

   public record Entry<T>(T value, int distance) {
   }
}

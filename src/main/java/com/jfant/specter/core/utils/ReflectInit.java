package com.jfant.specter.core.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.reflections.Reflections;
import org.reflections.scanners.Scanner;
import org.reflections.scanners.Scanners;

public class ReflectInit {
   private static final List<Reflections> reflections = new ArrayList<>();

   private ReflectInit() {
   }

   public static void registerPackages() {
      reflections.add(new Reflections("com.jfant.specter", new Scanner[]{Scanners.MethodsAnnotated}));
   }

   public static void init(Class<? extends Annotation> annotation) {
      for (Reflections reflection : reflections) {
         Set<Method> initTasks = reflection.getMethodsAnnotatedWith(annotation);
         if (initTasks == null) {
            return;
         }

         Map<Class<?>, List<Method>> byClass = initTasks.stream().collect(Collectors.groupingBy(Method::getDeclaringClass));
         Set<Method> left = new HashSet<>(initTasks);

         Method m;
         while ((m = left.stream().findAny().orElse(null)) != null) {
            reflectInit(m, annotation, left, byClass);
         }
      }
   }

   private static <T extends Annotation> void reflectInit(Method task, Class<T> annotation, Set<Method> left, Map<Class<?>, List<Method>> byClass) {
      left.remove(task);

      for (Class<?> clazz : getDependencies(task, annotation)) {
         for (Method m : byClass.getOrDefault(clazz, Collections.emptyList())) {
            if (left.contains(m)) {
               reflectInit(m, annotation, left, byClass);
            }
         }
      }

      try {
         task.invoke(null);
      } catch (InvocationTargetException | IllegalAccessException var10) {
         throw new IllegalStateException(
            "Error running @%s task '%s.%s'".formatted(annotation.getSimpleName(), task.getDeclaringClass().getSimpleName(), task.getName()), var10
         );
      } catch (NullPointerException var11) {
         throw new RuntimeException("Method \"%s\" using Init annotations from non-static context".formatted(task.getName()), var11);
      }
   }

   private static <T extends Annotation> Class<?>[] getDependencies(Method task, Class<T> annotation) {
      T init = task.getAnnotation(annotation);

      return switch (init) {
         case PreInit pre -> pre.dependencies();
         case PostInit post -> post.dependencies();
         default -> new Class[0];
      };
   }
}

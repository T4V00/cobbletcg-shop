package dev.tcgshop.ptcg;

import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Motor real del Pokémon TCG (twinleaf.gg, MIT, basado en ryuu-play de keeshii, MIT) ejecutado con GraalJS.
 * Todo el JavaScript corre en un único hilo propio ("tcgshop-ptcg") para no bloquear nunca el hilo del servidor;
 * las llamadas devuelven futuros y los resultados se aplican luego en el hilo del servidor.
 *
 * <h2>GraalJS aislado</h2>
 * GraalVM 24.1.1 va dentro del jar como recursos (graal/*.jar), no como mods anidados: Fabric no lo carga. Aquí se
 * copia a &lt;juego&gt;/.tcgshop/graal-24.1.1 y se carga en un URLClassLoader propio cuyo padre es el cargador de
 * plataforma (sólo Java). El puente {@code dev.tcgshop.ptcg.host.GraalHost} se define dentro de ese cargador y se usa
 * por reflexión con tipos de Java. Así no choca con el GraalVM de Cobblemon (1.8 lo trae sin reubicar) ni de nadie.
 */
public final class PtcgEngine {
    public static final String GRAAL_VERSION = "24.1.1";
    private static final String HOST = "dev.tcgshop.ptcg.host.GraalHost";

    /** Lo que ven las partidas: funciones del puente JS (PTCG.*) con argumentos y resultados de Java. */
    public interface Api {
        String str(String fn, Object... args);

        boolean bool(String fn, Object... args);

        int integer(String fn, Object... args);

        default void run(String fn, Object... args) { str(fn, args); }
    }

    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tcgshop-ptcg");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static Api api;
    private static ClassLoader graalLoader;
    private static volatile boolean ready, failed;
    private static volatile String error = "";

    private PtcgEngine() {}

    public static boolean ready() { return ready; }

    public static boolean failed() { return failed; }

    public static String error() { return error; }

    /** Carga el motor en segundo plano (tarda unos segundos la primera vez). */
    public static CompletableFuture<Boolean> preload() {
        return CompletableFuture.supplyAsync(PtcgEngine::load, THREAD);
    }

    private static boolean load() {
        if (ready) return true;
        if (failed) return false;
        long t0 = System.currentTimeMillis();
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader loader = isolatedLoader();
            Thread.currentThread().setContextClassLoader(loader);
            String code;
            try (InputStream in = PtcgEngine.class.getResourceAsStream("/assets/tcgshop/ptcg/engine.js")) {
                if (in == null) throw new IllegalStateException("falta assets/tcgshop/ptcg/engine.js");
                code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Class<?> host = Class.forName(HOST, true, loader);
            Object instance = host.getConstructor(String.class).newInstance(code);
            Method str = host.getMethod("str", String.class, Object[].class);
            Method bool = host.getMethod("bool", String.class, Object[].class);
            Method integer = host.getMethod("integer", String.class, Object[].class);
            api = new Api() {
                @Override public String str(String fn, Object... args) { return (String) invoke(str, instance, fn, args); }
                @Override public boolean bool(String fn, Object... args) { return (Boolean) invoke(bool, instance, fn, args); }
                @Override public int integer(String fn, Object... args) { return (Integer) invoke(integer, instance, fn, args); }
            };
            int cards = api.integer("init");
            ready = true;
            TcgShop.LOGGER.info("Motor Pokémon TCG listo: {} cartas en {} ms (GraalJS {} aislado)", cards, System.currentTimeMillis() - t0, GRAAL_VERSION);
            return true;
        } catch (Throwable e) {
            failed = true;
            error = e.toString();
            TcgShop.LOGGER.error("No se pudo cargar el motor Pokémon TCG", e);
            return false;
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    private static Object invoke(Method m, Object target, String fn, Object[] args) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(graalLoader);
            return m.invoke(target, fn, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            throw c instanceof RuntimeException re ? re : new RuntimeException(c);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    /** Copia los jars de GraalVM del mod a disco y crea el cargador aislado (con el puente GraalHost dentro). */
    private static synchronized ClassLoader isolatedLoader() throws Exception {
        if (graalLoader != null) return graalLoader;
        Path dir = FabricLoader.getInstance().getGameDir().resolve(".tcgshop").resolve("graal-" + GRAAL_VERSION);
        Files.createDirectories(dir);
        List<URL> urls = new ArrayList<>();
        String index;
        try (InputStream in = PtcgEngine.class.getResourceAsStream("/graal/index.txt")) {
            if (in == null) throw new IllegalStateException("faltan los jars de GraalVM (graal/index.txt) en el mod");
            index = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : index.split("\\R")) {
            String name = line.trim();
            if (name.isEmpty()) continue;
            Path target = dir.resolve(name);
            try (InputStream in = PtcgEngine.class.getResourceAsStream("/graal/" + name)) {
                if (in == null) throw new IllegalStateException("falta graal/" + name);
                byte[] bytes = in.readAllBytes();
                if (!Files.isRegularFile(target) || Files.size(target) != bytes.length) {
                    Path tmp = dir.resolve(name + ".tmp");
                    Files.write(tmp, bytes);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            urls.add(target.toUri().toURL());
        }
        byte[] hostBytes;
        try (InputStream in = PtcgEngine.class.getResourceAsStream("/" + HOST.replace('.', '/') + ".class")) {
            if (in == null) throw new IllegalStateException("falta " + HOST);
            hostBytes = in.readAllBytes();
        }
        graalLoader = new URLClassLoader("tcgshop-graal", urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(HOST)) return defineClass(name, hostBytes, 0, hostBytes.length);
                return super.findClass(name);
            }
        };
        return graalLoader;
    }

    /** Ejecuta algo con la API del motor en su hilo. */
    public static <T> CompletableFuture<T> call(Function<Api, T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            if (!load()) throw new IllegalStateException("motor TCG no disponible: " + error);
            return fn.apply(api);
        }, THREAD);
    }

    /** Llamada a una función del puente que devuelve texto. */
    public static String str(Api api, String fn, Object... args) {
        return api.str(fn, args);
    }

    public static boolean bool(Api api, String fn, Object... args) {
        return api.bool(fn, args);
    }
}

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
 * The real Pokémon TCG engine (twinleaf.gg, MIT, based on keeshii's ryuu-play, MIT) running on GraalJS.
 * All JavaScript runs on a single dedicated thread ("tcgshop-ptcg") so it never blocks the server thread;
 * calls return futures and the results are applied afterwards on the server thread.
 *
 * <h2>Isolated GraalJS</h2>
 * GraalVM 24.1.1 ships inside the jar as resources (graal/*.jar), not as nested mods: Fabric does not load it. Here it is
 * copied to &lt;game&gt;/.tcgshop/graal-24.1.1 and loaded in a dedicated URLClassLoader whose parent is the
 * platform loader (Java only). The bridge {@code dev.tcgshop.ptcg.host.GraalHost} is defined inside that loader and used
 * via reflection with Java types. This way it never clashes with Cobblemon's GraalVM (1.8 ships it unrelocated) or anyone else's.
 */
public final class PtcgEngine {
    public static final String GRAAL_VERSION = "24.1.1";
    private static final String HOST = "dev.tcgshop.ptcg.host.GraalHost";

    /** What matches see: JS bridge functions (PTCG.*) with Java arguments and results. */
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

    /** Loads the engine in the background (takes a few seconds the first time). */
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
                if (in == null) throw new IllegalStateException("missing assets/tcgshop/ptcg/engine.js");
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
            TcgShop.LOGGER.info("Pokémon TCG engine ready: {} cards in {} ms (isolated GraalJS {})", cards, System.currentTimeMillis() - t0, GRAAL_VERSION);
            return true;
        } catch (Throwable e) {
            failed = true;
            error = e.toString();
            TcgShop.LOGGER.error("Could not load the Pokémon TCG engine", e);
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

    /** Copies the mod's GraalVM jars to disk and creates the isolated loader (with the GraalHost bridge inside). */
    private static synchronized ClassLoader isolatedLoader() throws Exception {
        if (graalLoader != null) return graalLoader;
        Path dir = FabricLoader.getInstance().getGameDir().resolve(".tcgshop").resolve("graal-" + GRAAL_VERSION);
        Files.createDirectories(dir);
        List<URL> urls = new ArrayList<>();
        String index;
        try (InputStream in = PtcgEngine.class.getResourceAsStream("/graal/index.txt")) {
            if (in == null) throw new IllegalStateException("GraalVM jars (graal/index.txt) missing from the mod");
            index = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : index.split("\\R")) {
            String name = line.trim();
            if (name.isEmpty()) continue;
            Path target = dir.resolve(name);
            try (InputStream in = PtcgEngine.class.getResourceAsStream("/graal/" + name)) {
                if (in == null) throw new IllegalStateException("missing graal/" + name);
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
            if (in == null) throw new IllegalStateException("missing " + HOST);
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

    /** Runs something with the engine API on its thread. */
    public static <T> CompletableFuture<T> call(Function<Api, T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            if (!load()) throw new IllegalStateException("TCG engine unavailable: " + error);
            return fn.apply(api);
        }, THREAD);
    }

    /** Calls a bridge function that returns text. */
    public static String str(Api api, String fn, Object... args) {
        return api.str(fn, args);
    }

    public static boolean bool(Api api, String fn, Object... args) {
        return api.bool(fn, args);
    }
}

package dev.tcgshop.ptcg;

import dev.tcgshop.TcgShop;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Motor real del Pokémon TCG (twinleaf.gg, MIT, basado en ryuu-play de keeshii, MIT) ejecutado con GraalJS.
 * Todo el JavaScript corre en un único hilo propio ("tcgshop-ptcg") para no bloquear nunca el hilo del servidor;
 * las llamadas devuelven futuros y los resultados se aplican luego en el hilo del servidor.
 */
public final class PtcgEngine {
    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tcgshop-ptcg");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static Context ctx;
    private static Value api;
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
            Thread.currentThread().setContextClassLoader(PtcgEngine.class.getClassLoader());
            System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
            Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
            ctx = Context.newBuilder("js").engine(engine).allowAllAccess(false).build();
            String code;
            try (InputStream in = PtcgEngine.class.getResourceAsStream("/assets/tcgshop/ptcg/engine.js")) {
                if (in == null) throw new IllegalStateException("falta assets/tcgshop/ptcg/engine.js");
                code = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            ctx.eval(Source.newBuilder("js", code, "engine.js").build());
            api = ctx.getBindings("js").getMember("PTCG");
            int cards = api.getMember("init").execute().asInt();
            ready = true;
            TcgShop.LOGGER.info("Motor Pokémon TCG listo: {} cartas en {} ms", cards, System.currentTimeMillis() - t0);
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

    /** Ejecuta algo con la API del motor en su hilo. */
    public static <T> CompletableFuture<T> call(Function<Value, T> fn) {
        return CompletableFuture.supplyAsync(() -> {
            if (!load()) throw new IllegalStateException("motor TCG no disponible: " + error);
            return fn.apply(api);
        }, THREAD);
    }

    /** Llamada a una función del puente que devuelve texto. */
    public static String str(Value api, String fn, Object... args) {
        Value v = api.getMember(fn).execute(args);
        return v.isNull() ? "" : v.isString() ? v.asString() : v.toString();
    }

    public static boolean bool(Value api, String fn, Object... args) {
        return api.getMember(fn).execute(args).asBoolean();
    }
}

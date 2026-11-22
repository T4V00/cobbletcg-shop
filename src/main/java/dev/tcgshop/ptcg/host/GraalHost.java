package dev.tcgshop.ptcg.host;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Puente con GraalJS. ESTA CLASE NO SE CARGA CON EL CARGADOR DEL JUEGO: PtcgEngine la define dentro de un cargador
 * de clases aislado que sólo ve Java y nuestros jars de GraalVM 24.1.1 (guardados como recursos en graal/). Así nuestro
 * GraalJS nunca choca con el de otros mods (Cobblemon 1.8 trae GraalVM 22.3 sin reubicar).
 * Sólo usa tipos de Java en su interfaz (String, Object[], Boolean) para poder llamarla por reflexión desde fuera.
 */
public final class GraalHost {
    private final Context ctx;
    private final Value api;

    public GraalHost(String code) {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
        ctx = Context.newBuilder("js").engine(engine).allowAllAccess(false).build();
        ctx.eval(Source.newBuilder("js", code, "engine.js").buildLiteral());
        api = ctx.getBindings("js").getMember("PTCG");
    }

    /** Llama a PTCG.fn(args) y devuelve el resultado como texto ("" si es null). */
    public String str(String fn, Object[] args) {
        Value v = api.getMember(fn).execute(args);
        return v.isNull() ? "" : v.isString() ? v.asString() : v.toString();
    }

    public Boolean bool(String fn, Object[] args) {
        return api.getMember(fn).execute(args).asBoolean();
    }

    public Integer integer(String fn, Object[] args) {
        return api.getMember(fn).execute(args).asInt();
    }

    public void close() {
        ctx.close(true);
    }
}

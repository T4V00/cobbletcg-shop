package dev.tcgshop.ptcg.host;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Bridge to GraalJS. THIS CLASS IS NOT LOADED BY THE GAME CLASS LOADER: PtcgEngine defines it inside an isolated
 * class loader that only sees Java and our GraalVM 24.1.1 jars (stored as resources in graal/). This way our
 * GraalJS never clashes with other mods' (Cobblemon 1.8 ships GraalVM 22.3 unrelocated).
 * Its interface only uses Java types (String, Object[], Boolean) so it can be called via reflection from outside.
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

    /** Calls PTCG.fn(args) and returns the result as text ("" if null). */
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

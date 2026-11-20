import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.nio.file.Files;
import java.nio.file.Path;

/** Mide el motor de twinleaf dentro de GraalJS (sin JIT: HotSpot normal). */
public class PtcgBench {
    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
        Context ctx = Context.newBuilder("js").engine(engine).allowAllAccess(false).build();
        String code = Files.readString(Path.of(args[0]));
        ctx.eval(Source.newBuilder("js", code, "engine.js").build());
        Value p = ctx.getBindings("js").getMember("PTCG");
        System.out.println("carga " + (System.currentTimeMillis() - t0) + " ms");
        t0 = System.currentTimeMillis();
        System.out.println("cartas " + p.getMember("init").execute().asInt() + " " + (System.currentTimeMillis() - t0) + " ms");
        t0 = System.currentTimeMillis();
        String d1 = p.getMember("buildDeck").execute("", 101).asString();
        String d2 = p.getMember("buildDeck").execute("", 202).asString();
        System.out.println("mazos " + (System.currentTimeMillis() - t0) + " ms");
        t0 = System.currentTimeMillis();
        String err = p.getMember("create").execute("g", "{\"id\":1,\"name\":\"A\",\"deck\":" + d1 + ",\"bot\":true}",
                "{\"id\":2,\"name\":\"B\",\"deck\":" + d2 + ",\"bot\":true}").asString();
        System.out.println("create '" + err + "' " + (System.currentTimeMillis() - t0) + " ms");
        int steps = 0;
        long worst = 0;
        t0 = System.currentTimeMillis();
        while (steps < Integer.parseInt(args[1])) {
            long s = System.currentTimeMillis();
            boolean acted = p.getMember("botStep").execute("g").asBoolean();
            worst = Math.max(worst, System.currentTimeMillis() - s);
            if (!acted) break;
            steps++;
        }
        long v0 = System.currentTimeMillis();
        String view = p.getMember("view").execute("g", 1).asString();
        System.out.println("pasos " + steps + " en " + (System.currentTimeMillis() - t0) + " ms (peor paso " + worst + " ms), vista "
                + (System.currentTimeMillis() - v0) + " ms, " + view.length() + " bytes");
    }
}

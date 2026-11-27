package dev.tcgshop.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.TcgNpc;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * 1.0 beta 3: renderer of shop customers and employees. Everything TcgNpcRenderer draws (including its shared armor
 * layer, which shows the gear of upgraded guards, armed robbers and heist crews; ThiefTiers / GuardUpgrades), plus
 * the 10 unique customers' names (FixedCustomers) in animated rainbow colors, letter by letter.
 * Registered in TcgShopClient after ShopClient.init (it replaces the plain TcgNpcRenderer for CUSTOMER and EMPLOYEE).
 */
public class CustomerNpcRenderer<T extends Mob & TcgNpc> extends TcgNpcRenderer<T> {
    public CustomerNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void renderNameTag(T entity, Component text, PoseStack matrices, MultiBufferSource consumers, int light, float tickDelta) {
        if (entity instanceof CustomerEntity c && c.fixedId() >= 0) text = rainbow(text.getString(), entity.tickCount + tickDelta);
        super.renderNameTag(entity, text, matrices, consumers, light, tickDelta);
    }

    /** The name with each letter in its own hue, the rainbow sliding along the name over time. */
    public static MutableComponent rainbow(String s, float time) {
        MutableComponent out = Component.empty();
        int i = 0;
        for (int cp : s.codePoints().toArray()) {
            float hue = (time * 0.012f + i * 0.075f) % 1f;
            int rgb = Mth.hsvToRgb(hue < 0 ? hue + 1 : hue, 0.7f, 1f);
            out.append(Component.literal(new String(Character.toChars(cp))).withStyle(st -> st.withColor(rgb).withBold(true)));
            i++;
        }
        return out;
    }
}

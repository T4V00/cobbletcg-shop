package dev.tcgshop.client.render;

import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.TcgNpc;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * 1.0 beta 3: renderer of shop customers and employees. Everything TcgNpcRenderer draws (including its shared armor
 * layer, which shows the gear of upgraded guards, armed robbers and heist crews; ThiefTiers / GuardUpgrades), plus
 * the 10 unique customers' names (FixedCustomers) in animated rainbow colors, letter by letter.
 * Registered in TcgShopClient after ShopClient.init (it replaces the plain TcgNpcRenderer for CUSTOMER and EMPLOYEE).
 */
public class CustomerNpcRenderer<T extends MobEntity & TcgNpc> extends TcgNpcRenderer<T> {
    public CustomerNpcRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    protected void renderLabelIfPresent(T entity, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta) {
        if (entity instanceof CustomerEntity c && c.fixedId() >= 0) text = rainbow(text.getString(), entity.age + tickDelta);
        super.renderLabelIfPresent(entity, text, matrices, consumers, light, tickDelta);
    }

    /** The name with each letter in its own hue, the rainbow sliding along the name over time. */
    public static MutableText rainbow(String s, float time) {
        MutableText out = Text.empty();
        int i = 0;
        for (int cp : s.codePoints().toArray()) {
            float hue = (time * 0.012f + i * 0.075f) % 1f;
            int rgb = MathHelper.hsvToRgb(hue < 0 ? hue + 1 : hue, 0.7f, 1f);
            out.append(Text.literal(new String(Character.toChars(cp))).styled(st -> st.withColor(rgb).withBold(true)));
            i++;
        }
        return out;
    }
}

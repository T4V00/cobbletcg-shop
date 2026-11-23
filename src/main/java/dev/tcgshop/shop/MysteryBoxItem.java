package dev.tcgshop.shop;

import dev.tcgshop.TcgSounds;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Mystery Box: a surprise product. Whoever opens it gets packs or a random rare card.
 * When sold in your shop you earn its price multiplied by luck (from x0.3 to x1.9): you can win or lose.
 */
public class MysteryBoxItem extends Item {
    public MysteryBoxItem(Settings settings) {
        super(settings);
    }

    /** What the shop owner earns when selling a Mystery Box priced `price`. */
    public static long ownerEarning(long price, Random r) {
        return Math.round(price * (0.3 + r.nextDouble() * 1.6));
    }

    public static List<ItemStack> reward(Random r) {
        List<ItemStack> out = new ArrayList<>();
        List<Item> sealed = new ArrayList<>();
        List<Item> rares = new ArrayList<>(), hypers = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            if (!CardInfo.NS.equals(id.getNamespace())) continue;
            Item it = Registries.ITEM.get(id);
            if (id.getPath().contains("/sealed_")) { if (!PackLock.locked(id)) sealed.add(it); }
            else if (CardInfo.isCard(id)) {
                Rarity rar = Rarity.of(new ItemStack(it));
                if (rar.family == Rarity.Family.HYPER_RARE || rar.family == Rarity.Family.SPECIAL_ILLUSTRATION_RARE) hypers.add(it);
                else if (rar.family == Rarity.Family.ULTRA_RARE || rar.family == Rarity.Family.ILLUSTRATION_RARE) rares.add(it);
            }
        }
        float f = r.nextFloat();
        if (f < 0.05f && !hypers.isEmpty()) out.add(new ItemStack(hypers.get(r.nextInt(hypers.size()))));
        else if (f < 0.35f && !rares.isEmpty()) out.add(new ItemStack(rares.get(r.nextInt(rares.size()))));
        else if (!sealed.isEmpty()) {
            int n = f < 0.85f ? 2 : 3;
            for (int i = 0; i < n; i++) out.add(new ItemStack(sealed.get(r.nextInt(sealed.size()))));
        }
        return out;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            List<ItemStack> got = reward(world.random);
            for (ItemStack st : got) {
                user.getInventory().offerOrDrop(st);
                user.sendMessage(Text.translatable("tcgshop.mystery.got", st.toHoverableText()).formatted(Formatting.LIGHT_PURPLE), false);
            }
            world.playSound(null, user.getBlockPos(), TcgSounds.CARD_RARE, SoundCategory.PLAYERS, 1f, 0.8f);
            stack.decrementUnlessCreative(1, user);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop.mystery_box.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.tcgshop.mystery_box.tip2").formatted(Formatting.DARK_GRAY));
    }
}

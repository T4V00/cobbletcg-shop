package dev.tcgshop.entity;

import dev.tcgshop.shop.ShopSeats;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0.9.5 · Invisible seat a customer sits on (a CobbleFurnies chair, a sofa, a bench...).
 *
 * It is a "marker" armor stand (invisible, no gravity, no collision box) with its own server-side logic:
 * that way there is no need to register an entity type or a renderer, and the client sees it as an invisible vanilla
 * armor stand. The biped model applies the sitting pose by itself while riding.
 *
 * It is never saved (neither on its own nor as the customer's mount): if the chunk unloads or the server stops it
 * disappears, and it removes itself as soon as it has no passenger or the block is no longer a seat.
 */
public class ShopSeatEntity extends ArmorStand {
    /** Command tag of all seats (server only: tags are not synced). */
    public static final String TAG = "tcgshop_seat";
    @Nullable
    private BlockPos seatPos;
    @Nullable
    private Vec3 standPos;
    private int emptyTicks;

    public ShopSeatEntity(Level world) {
        super(EntityType.ARMOR_STAND, world);
        setInvisible(true);
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
        entityData.set(DATA_CLIENT_FLAGS, (byte) (entityData.get(DATA_CLIENT_FLAGS) | CLIENT_FLAG_MARKER));
        // tag so admins and other mods can exclude it: /kill @e[type=armor_stand,tag=!tcgshop_seat]
        addTag(TAG);
    }

    /** Creates the seat and places it in the world (y = height of the sitter's feet). */
    public static ShopSeatEntity spawn(ServerLevel world, BlockPos seat, double y, float yaw, @Nullable Vec3 stand) {
        ShopSeatEntity e = new ShopSeatEntity(world);
        e.seatPos = seat.immutable();
        e.standPos = stand;
        e.moveTo(seat.getX() + 0.5, y, seat.getZ() + 0.5, yaw, 0);
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
        world.addFreshEntity(e);
        return e;
    }

    @Nullable
    public BlockPos seatPos() { return seatPos; }

    @Nullable
    public Vec3 standPos() { return standPos; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        // no passenger (stood up, despawned...) or no chair underneath: remove
        if (!isVehicle()) {
            if (++emptyTicks > 2) discard();
            return;
        }
        emptyTicks = 0;
        if (tickCount % 10 == 0 && (seatPos == null || !(level() instanceof ServerLevel sw) || !ShopSeats.stillSeat(sw, seatPos))) {
            ejectPassengers();
            discard();
        }
    }

    /** On standing up it appears at the spot next to the seat (not on top of the chair). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return standPos != null ? standPos : super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && isVehicle()) ejectPassengers();
        super.remove(reason);
    }

    // ---- never saved
    @Override public boolean save(CompoundTag nbt) { return false; }
    @Override public boolean saveAsPassenger(CompoundTag nbt) { return false; }
    @Override public boolean shouldBeSaved() { return false; }

    // ---- untouchable
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean skipAttackInteraction(Entity attacker) { return true; }
    @Override public InteractionResult interactAt(Player player, Vec3 hitPos, InteractionHand hand) { return InteractionResult.PASS; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
}

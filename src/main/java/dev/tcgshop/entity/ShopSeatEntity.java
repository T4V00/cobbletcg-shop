package dev.tcgshop.entity;

import dev.tcgshop.shop.ShopSeats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
public class ShopSeatEntity extends ArmorStandEntity {
    /** Command tag of all seats (server only: tags are not synced). */
    public static final String TAG = "tcgshop_seat";
    @Nullable
    private BlockPos seatPos;
    @Nullable
    private Vec3d standPos;
    private int emptyTicks;

    public ShopSeatEntity(World world) {
        super(EntityType.ARMOR_STAND, world);
        setInvisible(true);
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
        dataTracker.set(ARMOR_STAND_FLAGS, (byte) (dataTracker.get(ARMOR_STAND_FLAGS) | MARKER_FLAG));
        // tag so admins and other mods can exclude it: /kill @e[type=armor_stand,tag=!tcgshop_seat]
        addCommandTag(TAG);
    }

    /** Creates the seat and places it in the world (y = height of the sitter's feet). */
    public static ShopSeatEntity spawn(ServerWorld world, BlockPos seat, double y, float yaw, @Nullable Vec3d stand) {
        ShopSeatEntity e = new ShopSeatEntity(world);
        e.seatPos = seat.toImmutable();
        e.standPos = stand;
        e.refreshPositionAndAngles(seat.getX() + 0.5, y, seat.getZ() + 0.5, yaw, 0);
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
        world.spawnEntity(e);
        return e;
    }

    @Nullable
    public BlockPos seatPos() { return seatPos; }

    @Nullable
    public Vec3d standPos() { return standPos; }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        // no passenger (stood up, despawned...) or no chair underneath: remove
        if (!hasPassengers()) {
            if (++emptyTicks > 2) discard();
            return;
        }
        emptyTicks = 0;
        if (age % 10 == 0 && (seatPos == null || !(getWorld() instanceof ServerWorld sw) || !ShopSeats.stillSeat(sw, seatPos))) {
            removeAllPassengers();
            discard();
        }
    }

    /** On standing up it appears at the spot next to the seat (not on top of the chair). */
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return standPos != null ? standPos : super.updatePassengerForDismount(passenger);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!getWorld().isClient && hasPassengers()) removeAllPassengers();
        super.remove(reason);
    }

    // ---- never saved
    @Override public boolean saveNbt(NbtCompound nbt) { return false; }
    @Override public boolean saveSelfNbt(NbtCompound nbt) { return false; }
    @Override public boolean shouldSave() { return false; }

    // ---- untouchable
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean handleAttack(Entity attacker) { return true; }
    @Override public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) { return ActionResult.PASS; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isCollidable() { return false; }
}

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
 * 0.9.5 · Asiento invisible en el que se sienta un cliente (una silla de CobbleFurnies, un sofá, un banco...).
 *
 * Es un soporte de armadura "marcador" (invisible, sin gravedad, sin caja de colisión) con lógica propia en el servidor:
 * así no hace falta registrar un tipo de entidad ni un renderizador, y el cliente lo ve como un soporte vanilla
 * invisible. La pose de sentado la pone sola el modelo bípedo al ir montado.
 *
 * Nunca se guarda (ni sola ni como montura del cliente): si se descarga el chunk o se para el servidor desaparece, y
 * se borra sola en cuanto se queda sin pasajero o el bloque deja de ser un asiento.
 */
public class ShopSeatEntity extends ArmorStandEntity {
    /** Etiqueta de comando de todos los asientos (sólo en el servidor: las etiquetas no se sincronizan). */
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
        // etiqueta para que admins y otros mods puedan excluirlo: /kill @e[type=armor_stand,tag=!tcgshop_seat]
        addCommandTag(TAG);
    }

    /** Crea y pone en el mundo el asiento (y = altura a la que van los pies del que se sienta). */
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
        // sin pasajero (se levantó, desapareció...) o sin silla debajo: fuera
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

    /** Al levantarse aparece en el sitio de al lado del asiento (no encima de la silla). */
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return standPos != null ? standPos : super.updatePassengerForDismount(passenger);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!getWorld().isClient && hasPassengers()) removeAllPassengers();
        super.remove(reason);
    }

    // ---- nunca se guarda
    @Override public boolean saveNbt(NbtCompound nbt) { return false; }
    @Override public boolean saveSelfNbt(NbtCompound nbt) { return false; }
    @Override public boolean shouldSave() { return false; }

    // ---- intocable
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean handleAttack(Entity attacker) { return true; }
    @Override public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) { return ActionResult.PASS; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isCollidable() { return false; }
}

package dev.tcgshop.events.incident;

import com.mojang.serialization.MapCodec;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.world.explosion.Explosion;

import java.util.EnumMap;
import java.util.Map;

/**
 * tcgshop:incident — charco, escombros, tierra, excrementos, quemadura, chispas, desorden o paquete. Lo coloca SÓLO el
 * efecto "incident" (nunca hay objeto ni pestaña creativa) en AIRE dentro de la tienda, y se quita solo cuando su
 * evento ya no está activo o sin arreglar.
 *
 * Seguridad (crítica E): sin colisión, sin drops (dropsNothing), irrompible a mano y resistente a explosiones,
 * PistonBehavior.BLOCK, "sólido" para que el agua no lo arrastre, no inflamable, sin pick-block. Sólo el dueño de la
 * tienda (u op en creativo) lo arregla, con la herramienta que pide (nunca cubos ni fuego).
 */
public class IncidentBlock extends BlockWithEntity {
    public static final MapCodec<IncidentBlock> CODEC = createCodec(IncidentBlock::new);
    public static final EnumProperty<IncidentKind> KIND = EnumProperty.of("kind", IncidentKind.class);

    private static final Map<IncidentKind, VoxelShape> SHAPES = new EnumMap<>(IncidentKind.class);

    static {
        for (IncidentKind k : IncidentKind.values())
            SHAPES.put(k, k == IncidentKind.PACKAGE ? Block.createCuboidShape(3, 0, 3, 13, k.height, 13)
                    : Block.createCuboidShape(0, 0, 0, 16, k.height, 16));
    }

    public IncidentBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(KIND, IncidentKind.PUDDLE));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(KIND); }

    @Override
    protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(KIND));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) { return true; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new IncidentBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, TcgBlocks.INCIDENT_BE, (w, p, s, be) -> be.serverTick((ServerWorld) w, s));
    }

    // ------------------------------------------------------------------ uso (sólo el dueño)

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // mano vacía: lo trata onUse (que prueba también la herramienta de la otra mano)
        if (stack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // siempre consumimos el clic: así ningún cubo, mechero ni bloque se usa "a través" del incidente
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp) IncidentModule.onPlayerUse((ServerWorld) world, pos, state, sp, stack, hand);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp) IncidentModule.onPlayerUse((ServerWorld) world, pos, state, sp, ItemStack.EMPTY, Hand.MAIN_HAND);
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ limpieza

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof IncidentBlockEntity be)
            IncidentModule.onBlockGone((ServerWorld) world, pos, be);
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** Sin suelo debajo (el dueño quita el bloque de abajo): desaparece (onStateReplaced lo cuenta como quitado). */
    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
                                                   BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !neighborState.isSideSolidFullSquare(world, neighborPos, Direction.UP))
            return Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    /** Red de seguridad: un bloque sin bloque-entidad o de un evento que ya no sigue se quita solo. */
    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!(world.getBlockEntity(pos) instanceof IncidentBlockEntity be) || !be.stillWanted(world)) IncidentModule.removeSilently(world, pos);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }

    @Override
    public boolean shouldDropItemsOnExplosion(Explosion explosion) { return false; }
}

package net.tls.lightlib.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public abstract class AbstractMultiSideBlock extends Block {
	private static final VoxelShape UP_SHAPE = Block.createCuboidShape(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);
	private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
	private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
	private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
	private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
	private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
	private static final Map<Direction, BooleanProperty> FACING_PROPERTIES;
	private static final Map<Direction, VoxelShape> SHAPES_FOR_DIRECTIONS;
	protected static final Direction[] DIRECTIONS;
	private final ImmutableMap<BlockState, VoxelShape> shapes;
	private final boolean hasAllHorizontalDirections;
	private final boolean canMirrorX;
	private final boolean canMirrorZ;

	public AbstractMultiSideBlock(Settings settings) {
		super(settings);
		this.setDefaultState(withAllDirections(this.stateManager));
		this.shapes = this.getShapesForStates(AbstractMultiSideBlock::getShapeForState);
		this.hasAllHorizontalDirections = Direction.Type.HORIZONTAL.stream().allMatch(this::canHaveDirection);
		this.canMirrorX = Direction.Type.HORIZONTAL.stream().filter(Direction.Axis.X).filter(this::canHaveDirection).count() % 2L == 0L;
		this.canMirrorZ = Direction.Type.HORIZONTAL.stream().filter(Direction.Axis.Z).filter(this::canHaveDirection).count() % 2L == 0L;
	}

	public static Set<Direction> getOpenFaces(BlockState state) {
		if (!(state.getBlock() instanceof AbstractLichenBlock)) {
			return Set.of();
		} else {
			Set<Direction> set = EnumSet.noneOf(Direction.class);
			Direction[] var2 = Direction.values();

			for (Direction direction : var2) {
                if (hasDirection(state, direction)) {
                    set.add(direction);
                }
            }

			return set;
		}
	}

	public static Set<Direction> unpackDirections(byte packed) {
		Set<Direction> set = EnumSet.noneOf(Direction.class);
		Direction[] var2 = Direction.values();

		for (Direction direction : var2) {
            if ((packed & (byte) (1 << direction.ordinal())) > 0) {
                set.add(direction);
            }
        }

		return set;
	}

	public static byte packDirections(Collection<Direction> directions) {
		byte b = 0;

		Direction direction;
		for(Iterator<Direction> var2 = directions.iterator(); var2.hasNext(); b = (byte)(b | 1 << direction.ordinal())) {
			direction = var2.next();
		}

		return b;
	}

	protected boolean canHaveDirection(Direction direction) {
		return true;
	}

	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {

        for (Direction direction : DIRECTIONS) {
            if (this.canHaveDirection(direction)) {
                builder.add(getProperty(direction));
            }
        }

	}


	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (!hasAnyDirection(state)) {
			return Blocks.AIR.getDefaultState();
		} else {
			return hasDirection(state, direction) && !canGrowOn(neighborState) ? disableDirection(state, getProperty(direction)) : state;
		}
	}

	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return this.shapes.get(state);
	}


	@Override
	public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack) {
		super.afterBreak(world, player, pos, state, blockEntity, stack);
		this.spawnBreakParticles(world, player, pos, state);
		if(player.isSneaking()){
			super.onBreak(world, pos, state, player);
		}
		else{
			if(hasDirection(state, player.getHorizontalFacing()))
				world.setBlockState(pos, state.with(FACING_PROPERTIES.get(player.getHorizontalFacing()), false));
		}
	}

	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		boolean bl = false;

        for (Direction direction : DIRECTIONS) {
            if (hasDirection(state, direction)) {
                BlockPos blockPos = pos.offset(direction);
                if (!canGrowOn(world.getBlockState(blockPos))) {
                    return false;
                }

                bl = true;
            }
        }

		return bl;
	}

	public boolean canReplace(BlockState state, ItemPlacementContext context) {
		boolean bl;
		if(context.getPlayer().isSneaking())
			bl = false;
		else
			bl = isNotFullBlock(state);
		return bl;
	}

	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		World world = ctx.getWorld();
		BlockPos blockPos = ctx.getBlockPos();
		BlockState blockState = world.getBlockState(blockPos);
		return Arrays.stream(ctx.getPlacementDirections()).map((direction) -> this.withDirection(blockState, world, blockPos, direction)).filter(Objects::nonNull).findFirst().orElse(null);
	}

	public boolean canPlace(BlockView view, BlockState state, BlockPos pos, Direction dir) {
		if (this.canHaveDirection(dir) && (!state.isOf(this) || !hasDirection(state, dir))) {
			BlockPos blockPos = pos.offset(dir);
			return canGrowOn(view.getBlockState(blockPos));
		} else {
			return false;
		}
	}

	@Nullable
	public BlockState withDirection(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		if (!this.canPlace(world, state, pos, direction)) {
			return null;
		} else {
			BlockState blockState;
			if (state.isOf(this)) {
				blockState = state;
			} else if (this.isWaterlogged() && state.getFluidState().isEqualAndStill(Fluids.WATER)) {
				blockState = this.getDefaultState().with(Properties.WATERLOGGED, true);
			} else {
				blockState = this.getDefaultState();
			}

			return blockState.with(getProperty(direction), true);
		}
	}

	public BlockState rotate(BlockState state, BlockRotation rotation) {
		if (!this.hasAllHorizontalDirections) {
			return state;
		} else {
			Objects.requireNonNull(rotation);
			return this.mirror(state, rotation::rotate);
		}
	}

	public BlockState mirror(BlockState state, BlockMirror mirror) {
		if (mirror == BlockMirror.FRONT_BACK && !this.canMirrorX) {
			return state;
		} else if (mirror == BlockMirror.LEFT_RIGHT && !this.canMirrorZ) {
			return state;
		} else {
			Objects.requireNonNull(mirror);
			return this.mirror(state, mirror::apply);
		}
	}

	private BlockState mirror(BlockState state, Function<Direction, Direction> mirror) {
		BlockState blockState = state;

        for (Direction direction : DIRECTIONS) {
            if (this.canHaveDirection(direction)) {
                blockState = blockState.with(getProperty(mirror.apply(direction)), state.get(getProperty(direction)));
            }
        }

		return blockState;
	}

	public static boolean hasDirection(BlockState state, Direction direction) {
		BooleanProperty booleanProperty = getProperty(direction);
		return state.contains(booleanProperty) && state.get(booleanProperty);
	}

	public static boolean canGrowOn(BlockState state) {
		return true;
	}

	private boolean isWaterlogged() {
		return this.stateManager.getProperties().contains(Properties.WATERLOGGED);
	}

	private static BlockState disableDirection(BlockState state, BooleanProperty direction) {
		BlockState blockState = state.with(direction, false);
		return hasAnyDirection(blockState) ? blockState : Blocks.AIR.getDefaultState();
	}

	public static BooleanProperty getProperty(Direction direction) {
		return FACING_PROPERTIES.get(direction);
	}

	private static BlockState withAllDirections(StateManager<Block, BlockState> stateManager) {
		BlockState blockState = stateManager.getDefaultState();

        for (BooleanProperty booleanProperty : FACING_PROPERTIES.values()) {
            if (blockState.contains(booleanProperty)) {
                blockState = blockState.with(booleanProperty, false);
            }
        }

		return blockState;
	}

	private static VoxelShape getShapeForState(BlockState state) {
		VoxelShape voxelShape = VoxelShapes.empty();

        for (Direction direction : DIRECTIONS) {
            if (hasDirection(state, direction)) {
                voxelShape = VoxelShapes.union(voxelShape, SHAPES_FOR_DIRECTIONS.get(direction));
            }
        }

		return voxelShape.isEmpty() ? VoxelShapes.fullCube() : voxelShape;
	}

	protected static boolean hasAnyDirection(BlockState state) {
		return Arrays.stream(DIRECTIONS).anyMatch((dir) -> hasDirection(state, dir));
	}

	public static boolean isNotFullBlock(BlockState state) {
		return Arrays.stream(DIRECTIONS).anyMatch((dir) -> !hasDirection(state, dir));
	}

	static {
		FACING_PROPERTIES = ConnectingBlock.FACING_PROPERTIES;
		SHAPES_FOR_DIRECTIONS = Util.make(Maps.newEnumMap(Direction.class), (shapes) -> {
			shapes.put(Direction.NORTH, SOUTH_SHAPE);
			shapes.put(Direction.EAST, WEST_SHAPE);
			shapes.put(Direction.SOUTH, NORTH_SHAPE);
			shapes.put(Direction.WEST, EAST_SHAPE);
			shapes.put(Direction.UP, UP_SHAPE);
			shapes.put(Direction.DOWN, DOWN_SHAPE);
		});
		DIRECTIONS = Direction.values();
	}
}

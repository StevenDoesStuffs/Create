package com.simibubi.create.content.curiosities.girder;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.contraptions.base.KineticTileEntity;
import com.simibubi.create.content.contraptions.fluids.pipes.BracketBlock;
import com.simibubi.create.content.contraptions.relays.elementary.BracketedTileEntityBehaviour;
import com.simibubi.create.content.contraptions.wrench.IWrenchable;
import com.simibubi.create.content.logistics.block.chute.AbstractChuteBlock;
import com.simibubi.create.content.logistics.trains.track.TrackBlock;
import com.simibubi.create.content.logistics.trains.track.TrackBlock.TrackShape;
import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.placement.IPlacementHelper;
import com.simibubi.create.foundation.utility.placement.PlacementHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GirderBlock extends Block implements SimpleWaterloggedBlock, IWrenchable {

	private static final int placementHelperId = PlacementHelpers.register(new GirderPlacementHelper());

	public static final BooleanProperty X = BooleanProperty.create("x");
	public static final BooleanProperty Z = BooleanProperty.create("z");
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");

	public GirderBlock(Properties p_49795_) {
		super(p_49795_);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false)
			.setValue(TOP, false)
			.setValue(BOTTOM, false)
			.setValue(X, false)
			.setValue(Z, false));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
		super.createBlockStateDefinition(pBuilder.add(X, Z, TOP, BOTTOM, WATERLOGGED));
	}

	@Override
	public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand,
		BlockHitResult pHit) {
		if (pPlayer == null)
			return InteractionResult.PASS;

		ItemStack itemInHand = pPlayer.getItemInHand(pHand);
		if (AllBlocks.SHAFT.isIn(itemInHand)) {
			KineticTileEntity.switchToBlockState(pLevel, pPos, AllBlocks.METAL_GIRDER_ENCASED_SHAFT.getDefaultState()
				.setValue(WATERLOGGED, pState.getValue(WATERLOGGED))
				.setValue(TOP, pState.getValue(TOP))
				.setValue(BOTTOM, pState.getValue(BOTTOM))
				.setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS, pState.getValue(X) || pHit.getDirection()
					.getAxis() == Axis.Z ? Axis.Z : Axis.X));
			pLevel.playSound(null, pPos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS, 0.5f, 1.25f);
			if (!pLevel.isClientSide && !pPlayer.isCreative()) {
				itemInHand.shrink(1);
				if (itemInHand.isEmpty())
					pPlayer.setItemInHand(pHand, ItemStack.EMPTY);
			}
			return InteractionResult.SUCCESS;
		}

		IPlacementHelper helper = PlacementHelpers.get(placementHelperId);
		if (helper.matchesItem(itemInHand))
			return helper.getOffset(pPlayer, pLevel, pState, pPos, pHit)
				.placeInWorld(pLevel, (BlockItem) itemInHand.getItem(), pPlayer, pHand, pHit);

		return InteractionResult.PASS;
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor world,
		BlockPos pos, BlockPos neighbourPos) {
		if (state.getValue(WATERLOGGED))
			world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
		Axis axis = direction.getAxis();
		Property<Boolean> updateProperty =
			axis == Axis.X ? X : axis == Axis.Z ? Z : direction == Direction.UP ? TOP : BOTTOM;
		state = state.setValue(updateProperty, false);
		for (Direction d : Iterate.directionsInAxis(axis))
			state = updateState(world, pos, state, d);
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		FluidState ifluidstate = level.getFluidState(pos);
		BlockState state = super.getStateForPlacement(context);
		state = state.setValue(X, face.getAxis() == Axis.X);
		state = state.setValue(Z, face.getAxis() == Axis.Z);

		for (Direction d : Iterate.directions)
			state = updateState(level, pos, state, d);

		return state.setValue(WATERLOGGED, Boolean.valueOf(ifluidstate.getType() == Fluids.WATER));
	}

	public static BlockState updateState(LevelAccessor level, BlockPos pos, BlockState state, Direction d) {
		Axis axis = d.getAxis();
		Property<Boolean> updateProperty = axis == Axis.X ? X : axis == Axis.Z ? Z : d == Direction.UP ? TOP : BOTTOM;
		BlockState sideState = level.getBlockState(pos.relative(d));

		if (axis.isVertical())
			return updateVerticalProperty(level, pos, state, updateProperty, sideState, d);

		if (sideState.getBlock() instanceof GirderEncasedShaftBlock
			&& sideState.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) != axis)
			state = state.setValue(updateProperty, true);
		if (sideState.getBlock() == state.getBlock() && sideState.getValue(updateProperty))
			state = state.setValue(updateProperty, true);

		for (Direction d2 : Iterate.directionsInAxis(axis == Axis.X ? Axis.Z : Axis.X)) {
			BlockState above = level.getBlockState(pos.above()
				.relative(d2));
			if (AllBlocks.TRACK.has(above)) {
				TrackShape shape = above.getValue(TrackBlock.SHAPE);
				if (shape == (axis == Axis.X ? TrackShape.XO : TrackShape.ZO))
					state = state.setValue(updateProperty, true);
			}
		}

		return state;
	}

	public static BlockState updateVerticalProperty(LevelAccessor level, BlockPos pos, BlockState state,
		Property<Boolean> updateProperty, BlockState sideState, Direction d) {
		if (sideState.getBlock() == state.getBlock() && sideState.getValue(X) == sideState.getValue(Z))
			state = state.setValue(updateProperty, true);
		else if (sideState.hasProperty(WallBlock.UP) && sideState.getValue(WallBlock.UP))
			state = state.setValue(updateProperty, true);
		else if (sideState.hasBlockEntity()) {
			BlockEntity blockEntity = level.getBlockEntity(pos.relative(d));
			if (!(blockEntity instanceof SmartTileEntity ste))
				return state;
			BracketedTileEntityBehaviour behaviour = ste.getBehaviour(BracketedTileEntityBehaviour.TYPE);
			if (behaviour == null)
				return state;
			BlockState bracket = behaviour.getBracket();
			if (!bracket.hasProperty(BracketBlock.FACING))
				return state;
			if (bracket.getValue(BracketBlock.FACING) == d)
				state = state.setValue(updateProperty, true);
		}
		return state;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		boolean x = state.getValue(GirderBlock.X);
		boolean z = state.getValue(GirderBlock.Z);
		return x ? z ? AllShapes.GIRDER_CROSS : AllShapes.GIRDER_BEAM.get(Axis.X)
			: z ? AllShapes.GIRDER_BEAM.get(Axis.Z) : AllShapes.EIGHT_VOXEL_POLE.get(Axis.Y);
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}

	public static boolean isConnected(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction side) {
		Axis axis = side.getAxis();
		if (state.getBlock() instanceof GirderBlock && !state.getValue(axis == Axis.X ? X : Z))
			return false;
		if (state.getBlock() instanceof GirderEncasedShaftBlock
			&& state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS) != axis)
			return false;
		BlockPos relative = pos.relative(side);
		BlockState blockState = world.getBlockState(relative);
		if (blockState.isAir())
			return false;
		VoxelShape shape = blockState.getShape(world, relative);
		if (shape.isEmpty())
			return false;
		if (Block.isFaceFull(shape, side.getOpposite()) && blockState.getMaterial()
			.isSolidBlocking())
			return true;
		return AbstractChuteBlock.getChuteFacing(blockState) == Direction.DOWN;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		if (rot.rotate(Direction.EAST)
			.getAxis() == Axis.X)
			return state;
		return state.setValue(X, state.getValue(Z))
			.setValue(Z, state.getValue(Z));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state;
	}

}

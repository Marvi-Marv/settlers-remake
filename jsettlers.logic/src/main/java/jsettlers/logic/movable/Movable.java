/*******************************************************************************
 * Copyright (c) 2015 - 2018
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.logic.movable;

import jsettlers.algorithms.fogofwar.FoWTask;
import jsettlers.algorithms.path.Path;
import jsettlers.algorithms.simplebehaviortree.BehaviorTreeHelper;
import jsettlers.algorithms.simplebehaviortree.IBooleanConditionFunction;
import jsettlers.algorithms.simplebehaviortree.IEDirectionSupplier;
import jsettlers.algorithms.simplebehaviortree.IEMaterialTypeSupplier;
import jsettlers.algorithms.simplebehaviortree.INodeStatusActionConsumer;
import jsettlers.algorithms.simplebehaviortree.IShortPoint2DSupplier;
import jsettlers.algorithms.simplebehaviortree.IShortSupplier;
import jsettlers.algorithms.simplebehaviortree.Node;
import jsettlers.algorithms.simplebehaviortree.NodeStatus;
import jsettlers.algorithms.simplebehaviortree.Root;
import jsettlers.algorithms.simplebehaviortree.Tick;
import jsettlers.algorithms.simplebehaviortree.nodes.Guard;
import jsettlers.common.action.EMoveToType;
import jsettlers.common.map.shapes.HexGridArea;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.material.ESearchType;
import jsettlers.common.movable.EDirection;
import jsettlers.common.movable.EEffectType;
import jsettlers.common.movable.EMovableAction;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.common.selectable.ESelectionType;
import jsettlers.logic.constants.Constants;
import jsettlers.logic.constants.MatchConstants;
import jsettlers.logic.movable.civilian.BearerMovable;
import jsettlers.logic.movable.civilian.BricklayerMovable;
import jsettlers.logic.movable.civilian.BuildingWorkerMovable;
import jsettlers.logic.movable.civilian.DiggerMovable;
import jsettlers.logic.movable.civilian.HealerMovable;
import jsettlers.logic.movable.cargo.CargoShipMovable;
import jsettlers.logic.movable.cargo.DonkeyMovable;
import jsettlers.logic.movable.interfaces.AbstractMovableGrid;
import jsettlers.logic.movable.interfaces.IAttackableHumanMovable;
import jsettlers.logic.movable.interfaces.IFerryMovable;
import jsettlers.logic.movable.interfaces.ILogicMovable;
import jsettlers.logic.movable.military.BowmanMovable;
import jsettlers.logic.movable.military.InfantryMovable;
import jsettlers.logic.movable.military.MageMovable;
import jsettlers.logic.movable.other.FerryMovable;
import jsettlers.logic.movable.specialist.GeologistMovable;
import jsettlers.logic.movable.specialist.PioneerMovable;
import jsettlers.logic.movable.specialist.ThiefMovable;
import jsettlers.logic.player.Player;

import java.util.LinkedList;

import static jsettlers.algorithms.simplebehaviortree.BehaviorTreeHelper.*;

/**
 * Central Movable class of JSettlers.
 *
 * @author Andreas Eberle
 */
public abstract class Movable implements ILogicMovable, FoWTask {
	private static final long serialVersionUID = -705947810059935866L;

	private static final int SHIP_PUSH_DISTANCE = 10;

	protected final AbstractMovableGrid grid;
	private final     int                 id;
	protected final   Player              player;

	private EMovableState state = EMovableState.DOING_NOTHING;

	private final EMovableType    movableType;

	private EMaterialType  materialType  = EMaterialType.NO_MATERIAL;
	private EMovableAction movableAction = EMovableAction.NO_ACTION;
	private EDirection     direction;
	private int[] effectEnd = new int[EEffectType.values().length];

	private int   animationStartTime;
	private short animationDuration;

	public ShortPoint2D oldFowPosition = null;
	protected ShortPoint2D position;

	protected Path path;

	protected float         health;
	private boolean       visible           = true;
	private ILogicMovable pushedFrom;

	private boolean isRightstep = false;
	protected int     flockDelay  = 700;

	private transient boolean selected    = false;
	private transient boolean soundPlayed = false;

	// the following data only for ship passengers
	protected IFerryMovable ferryToEnter = null;

	protected boolean playerControlled;

	private Tick<? extends Movable> tick;

	protected Movable(AbstractMovableGrid grid, EMovableType movableType, ShortPoint2D position, Player player, Movable replace, Root<? extends Movable> behaviour) {
		this.grid = grid;
		this.position = position;
		this.player = player;
		this.movableType = movableType;

		this.tick = behaviour != null? new Tick<>(this, (Root<Movable>)behaviour) : null;

		if(replace != null) {
			this.health = replace.getHealth()/replace.getMovableType().getHealth()*movableType.getHealth();
			this.direction = replace.getDirection();
			if(movableType == replace.movableType) {
				this.materialType = replace.materialType;
			}
		} else {
			this.health = movableType.getHealth();
			this.direction = EDirection.VALUES[MatchConstants.random().nextInt(EDirection.NUMBER_OF_DIRECTIONS)];
		}

		playerControlled = movableType.playerControllable;

		this.id = MovableManager.requestId(this, replace);
	}

	/**
	 * Tests if this movable can receive moveTo requests and if so, directs it to go to the given position.
	 *
	 * @param targetPosition
	 * 		Desired position the movable should move to
	 */
	@Override
	public void moveTo(ShortPoint2D targetPosition, EMoveToType moveToType) {
	}

	public void leavePosition() {
		if (isBusy()) {
			return;
		}

		int offset = MatchConstants.random().nextInt(EDirection.NUMBER_OF_DIRECTIONS);

		for (int i = 0; i < EDirection.NUMBER_OF_DIRECTIONS; i++) {
			EDirection currDir = EDirection.VALUES[(i + offset) % EDirection.NUMBER_OF_DIRECTIONS];
			if (goInDirection(currDir, EGoInDirectionMode.GO_IF_ALLOWED_AND_FREE)) {
				break;
			} else {
				ILogicMovable movableAtPos = grid.getMovableAt(currDir.getNextTileX(position.x), currDir.getNextTileY(position.y));
				if (movableAtPos != null) {
					movableAtPos.push(this);
				}
			}
		}
	}

	protected void action() {
		if(tick != null) {
			NodeStatus status = NodeStatus.SUCCESS;

			// continue behaviour if the previous run was successful
			for(int i = 0; i < 2 && status == NodeStatus.SUCCESS && isAlive(); i++) {
				status = tick.tick();
			}
		}
	}

	protected boolean checkPathStepPreconditions() {
		if(pathStep != null && !pathStep.test(this)) {
			aborted = true;
			return false;
		}

		return true;
	}

	protected boolean aborted;
	protected IBooleanConditionFunction<Movable> pathStep;

	protected void pathAborted(ShortPoint2D pathTarget) {
		aborted = true;
	}

	protected static <T extends Movable> Node<T> drop(IEMaterialTypeSupplier<T> materialType, IBooleanConditionFunction<T> offerMaterial) {
		return sequence(
				playAction(EMovableAction.BEND_DOWN, mov -> Constants.MOVABLE_BEND_DURATION),
				BehaviorTreeHelper.action(mov -> {
					EMaterialType takeDropMaterial = materialType.apply(mov);

					if (takeDropMaterial == null || !takeDropMaterial.isDroppable()) return;

					mov.setMaterial(EMaterialType.NO_MATERIAL);
					mov.grid.dropMaterial(mov.position, takeDropMaterial, offerMaterial.test(mov), false);
				}),
				playAction(EMovableAction.RAISE_UP, mov -> Constants.MOVABLE_BEND_DURATION)
		);
	}

	protected static <T extends Movable> Node<T> take(IEMaterialTypeSupplier<T> materialType, IBooleanConditionFunction<T> fromMap, INodeStatusActionConsumer<T> tookMaterial) {
		return sequence(
				condition(mov -> !fromMap.test(mov) || mov.grid.canTakeMaterial(mov.position, materialType.apply(mov))),
				playAction(EMovableAction.BEND_DOWN, mov -> Constants.MOVABLE_BEND_DURATION),
				BehaviorTreeHelper.action(mov -> {
					EMaterialType material = materialType.apply(mov);
					mov.grid.takeMaterial(mov.position, material);
					mov.setMaterial(material);
					tookMaterial.accept(mov);
				}),
				playAction(EMovableAction.RAISE_UP, mov -> Constants.MOVABLE_BEND_DURATION)
		);
	}

	protected static <T extends Movable> Node<T> goInDirectionIfAllowedAndFree(IEDirectionSupplier<T> direction) {
		return sequence(
				condition(mov -> mov.goInDirection(direction.apply(mov), EGoInDirectionMode.GO_IF_ALLOWED_AND_FREE)),
				waitFor(condition(mov -> ((Movable)mov).state == EMovableState.DOING_NOTHING))
		);
	}

	protected static <T extends Movable> Node<T> goInDirectionIfFree(IEDirectionSupplier<T> direction) {
		return sequence(
				condition(mov -> {
					ShortPoint2D targetPosition = direction.apply(mov).getNextHexPoint(mov.position);
					if(mov.grid.isFreePosition(targetPosition.x, targetPosition.y)) {
						((Movable)mov).initGoingSingleStep(targetPosition);
						mov.setState(EMovableState.GOING_SINGLE_STEP);
						return true;
					} else {
						return false;
					}
				}),
				waitFor(condition(mov -> ((Movable)mov).state == EMovableState.DOING_NOTHING))
		);
	}

	protected static <T extends Movable> Node<T> goInDirectionWaitFree(IEDirectionSupplier<T> direction, IBooleanConditionFunction<T> pathStep) {
		return sequence(
				BehaviorTreeHelper.action(mov -> {
					mov.aborted = false;
					mov.pathStep = (IBooleanConditionFunction<Movable>)pathStep;
					mov.goInDirection(direction.apply(mov), EGoInDirectionMode.GO_IF_ALLOWED_WAIT_TILL_FREE);
				}),
				waitFor(condition(mov -> mov.path == null)),
				condition(mov -> !mov.aborted)
		);
	}


	protected static <T extends Movable> Node<T> followPresearchedPath(IBooleanConditionFunction<T> pathStep) {
		return sequence(

				BehaviorTreeHelper.action(mov -> {
					Movable realMov = mov;

					mov.aborted = false;
					mov.pathStep = (IBooleanConditionFunction<Movable>)pathStep;

					assert mov.path != null : "path mustn't be null to be able to followPresearchedPath()!";
					realMov.followPath(mov.path);
				}),
				waitFor(condition(mov -> mov.path == null)),
				condition(mov -> !mov.aborted)

		);
	}

	/**
	 *
	 * @param duration
	 * 			duration in milliseconds
	 */
	protected static <T extends Movable> Node<T> playAction(EMovableAction action, IShortSupplier<T> duration) {
		return sequence(
				BehaviorTreeHelper.action(mov -> {
					Movable realMov = mov;

					realMov.playAnimation(action, duration.apply(mov));
					realMov.setState(EMovableState.WAITING);

					realMov.soundPlayed = false;
				}),
				waitFor(condition(mov -> ((Movable)mov).state == EMovableState.DOING_NOTHING))
		);
	}

	protected static <T extends Movable> Node<T> goToPos(IShortPoint2DSupplier<T> target, IBooleanConditionFunction<T> pathStep) {
		return sequence(
				condition(mov -> {
					Movable realMov = mov;

					mov.aborted = false;
					mov.pathStep = (IBooleanConditionFunction<Movable>)pathStep;
					Path path = mov.grid.calculatePathTo(mov, target.apply(mov));

					realMov.followPath(path);
					return mov.path != null;
				}),
				waitFor(condition(mov -> mov.path == null)),
				condition(mov -> !mov.aborted)
		);
	}


	protected Path findWayAroundObstacle(ShortPoint2D position, Path path) {
		if (!path.hasOverNextStep()) { // if path has no position left
			return path;
		}

		EDirection direction = EDirection.getApproxDirection(position, path.getOverNextPos());

		EDirection rightDir = direction.getNeighbor(-1);
		EDirection leftDir = direction.getNeighbor(1);

		ShortPoint2D straightPos = direction.getNextHexPoint(position);
		ShortPoint2D twoStraightPos = direction.getNextHexPoint(position, 2);

		ShortPoint2D rightPos = rightDir.getNextHexPoint(position);
		ShortPoint2D rightStraightPos = direction.getNextHexPoint(rightPos);
		ShortPoint2D straightRightPos = rightDir.getNextHexPoint(straightPos);

		ShortPoint2D leftPos = leftDir.getNextHexPoint(position);
		ShortPoint2D leftStraightPos = direction.getNextHexPoint(leftPos);
		ShortPoint2D straightLeftPos = leftDir.getNextHexPoint(straightPos);

		ShortPoint2D overNextPos = path.getOverNextPos();

		LinkedList<ShortPoint2D[]> possiblePaths = new LinkedList<>();

		if (twoStraightPos.equals(overNextPos)) {
			if (grid.isValidPosition(this, rightPos.x, rightPos.y) && grid.isValidPosition(this, rightStraightPos.x, rightStraightPos.y)) {
				possiblePaths.add(new ShortPoint2D[]{
						rightPos,
						rightStraightPos});
			} else if (grid.isValidPosition(this, leftPos.x, leftPos.y) && grid.isValidPosition(this, leftStraightPos.x, leftStraightPos.y)) {
				possiblePaths.add(new ShortPoint2D[]{
						leftPos,
						leftStraightPos});
			} else {
				// TODO @Andreas Eberle maybe calculate a new path
			}
		}

		if (rightStraightPos.equals(overNextPos) && grid.isValidPosition(this, rightPos.x, rightPos.y)) {
			possiblePaths.add(new ShortPoint2D[]{rightPos});
		}
		if (leftStraightPos.equals(overNextPos) && grid.isValidPosition(this, leftPos.x, leftPos.y)) {
			possiblePaths.add(new ShortPoint2D[]{leftPos});
		}

		if ((straightRightPos.equals(overNextPos) || straightLeftPos.equals(overNextPos))
				&& grid.isValidPosition(this, straightPos.x, straightPos.y) && grid.hasNoMovableAt(straightPos.x, straightPos.y)) {
			possiblePaths.add(new ShortPoint2D[]{straightPos});

		} else {
			// TODO @Andreas Eberle maybe calculate a new path
		}

		// try to find a way without a movable or with a pushable movable.
		for (ShortPoint2D[] pathPrefix : possiblePaths) { // check if any of the paths is free of movables
			ShortPoint2D firstPosition = pathPrefix[0];
			ILogicMovable movable = grid.getMovableAt(firstPosition.x, firstPosition.y);
			if (movable == null || movable.isProbablyPushable(this)) {
				path.goToNextStep();
				return new Path(path, pathPrefix);
			}
		}

		return path;
	}

	@Override
	public int timerEvent() {
		if (state == EMovableState.DEAD) {
			return -1;
		}

		switch (state) { // ensure animation is finished, if not, reschedule
			case GOING_SINGLE_STEP:
			case PATHING:
			case WAITING:
				int remainingAnimationTime = animationStartTime + animationDuration - MatchConstants.clock().getTime();
				if (remainingAnimationTime > 0) {
					return remainingAnimationTime;
				}
				break;
			default:
				break;
		}

		switch (state) {
			case WAITING:
			case GOING_SINGLE_STEP:
				setState(EMovableState.DOING_NOTHING); // the action is finished, as the time passed
				movableAction = EMovableAction.NO_ACTION;

				break;

			case PATHING:
				pathingAction();
				break;

			default:
				break;
		}

		if (state == EMovableState.DOING_NOTHING) { // if movable is currently doing nothing
			action();
		}

		if (state == EMovableState.DOING_NOTHING) {
			return Constants.MOVABLE_INTERRUPT_PERIOD;
		}

		return animationDuration;
	}

	private void pathingAction() {
		if (path == null || !path.hasNextStep() || ferryToEnter == null && !checkPathStepPreconditions()) {
			// if path is finished, or canceled by strategy return from here
			setState(EMovableState.DOING_NOTHING);
			movableAction = EMovableAction.NO_ACTION;
			path = null;
			if (ferryToEnter != null) {
				enterFerry();
			}
			return;
		}

		ILogicMovable blockingMovable = grid.getMovableAt(path.nextX(), path.nextY());
		if (blockingMovable == null) { // if we can go on to the next step
			if (grid.isValidNextPathPosition(this, path.getNextPos(), path.getTargetPosition())) { // next position is valid
				goSinglePathStep();

			} else { // next position is invalid
				movableAction = EMovableAction.NO_ACTION;
				animationDuration = Constants.MOVABLE_INTERRUPT_PERIOD; // recheck shortly
				Path newPath = grid.calculatePathTo(this, path.getTargetPosition()); // try to find a new path

				if (newPath == null) { // no path found
					setState(EMovableState.DOING_NOTHING);

					pathAborted(path.getTargetPosition()); // inform strategy
					path = null;
				} else {
					this.path = newPath; // continue with new path
					if (grid.hasNoMovableAt(path.nextX(), path.nextY())) { // path is valid, but maybe blocked (leaving blocked area)
						goSinglePathStep();
					}
				}
			}

		} else { // step not possible, so try it next time
			movableAction = EMovableAction.NO_ACTION;
			boolean pushedSuccessfully = blockingMovable.push(this);
			if (!pushedSuccessfully) {
				path = findWayAroundObstacle(position, path);
				animationDuration = Constants.MOVABLE_INTERRUPT_PERIOD; // recheck shortly
			} else if (movableAction == EMovableAction.NO_ACTION) {
				animationDuration = Constants.MOVABLE_INTERRUPT_PERIOD; // recheck shortly
			} // else: push initiated our next step
		}
		if (this.isShip()) { // ships need more space
			pushShips();
		}
	}

	private void enterFerry() {
		int distanceToFerry = this.getPosition().getOnGridDistTo(ferryToEnter.getPosition());
		if (distanceToFerry <= Constants.MAX_FERRY_ENTRANCE_DISTANCE) {
			if (ferryToEnter.addPassenger((IAttackableHumanMovable)this)) {
				grid.leavePosition(this.getPosition(), this);
				setState(EMovableState.ON_FERRY);
			}
		}
		ferryToEnter = null;
	}

	private void pushShips() {
		HexGridArea.stream(position.x, position.y, 1, SHIP_PUSH_DISTANCE)
				   .filterBounds(grid.getWidth(), grid.getHeight())
				   .forEach((x, y) -> {
					   ILogicMovable blockingMovable = grid.getMovableAt(x, y);
					   if (blockingMovable != null && blockingMovable.isShip()) {
						   blockingMovable.push(this);
					   }
				   });
	}

	@Override
	public void goSinglePathStep() {
		initGoingSingleStep(path.getNextPos());
		path.goToNextStep();
	}

	@Override
	public ShortPoint2D getPosition() {
		return this.position;
	}

	@Override
	public int getViewDistance() {
		return movableType.getViewDistance();
	}

	@Override
	public ShortPoint2D getFoWPosition() {
		return position;
	}

	@Override
	public ShortPoint2D getOldFoWPosition() {
		return oldFowPosition;
	}

	@Override
	public void setOldFoWPosition(ShortPoint2D position) {
		oldFowPosition = position;
	}

	@Override
	public ILogicMovable getPushedFrom() {
		return pushedFrom;
	}

	private void initGoingSingleStep(ShortPoint2D position) {
		direction = EDirection.getDirection(this.position, position);
		playAnimation(EMovableAction.WALKING, movableType.getStepDurationMs());
		grid.leavePosition(this.position, this);
		grid.enterPosition(position, this, false);
		this.position = position;
		isRightstep = !isRightstep;
	}

	protected static <T extends Movable> Guard<T> doingNothingGuard() {
		return guard(mov -> true, doingNothingAction());
	}

	protected static <T extends Movable> Node<T> doingNothingAction() {
		return selector(
				sequence(
					condition(Movable::isShip),
					BehaviorTreeHelper.action(Movable::pushShips),
					sleep(mov -> ((Movable)mov).flockDelay)
				),
				sequence(
					condition(mov -> mov.grid.isBlockedOrProtected(mov.position.x, mov.position.y)),
					selector(
						condition(mov -> mov.preSearchPath(true, mov.position.x, mov.position.y, (short) 50, ESearchType.NON_BLOCKED_OR_PROTECTED)),
						sequence(
							BehaviorTreeHelper.action(Movable::kill),
							alwaysFail()
						)
					),
					followPresearchedPath(mov -> true)
				),
				// flock to decentralize
				sequence(
					condition(mov -> {
						ShortPoint2D decentVector = mov.grid.calcDecentralizeVector(mov.position.x, mov.position.y);

						EDirection randomDirection = mov.getDirection().getNeighbor(MatchConstants.random().nextInt(-1, 1));
						int dx = randomDirection.gridDeltaX + decentVector.x;
						int dy = randomDirection.gridDeltaY + decentVector.y;

						if (ShortPoint2D.getOnGridDist(dx, dy) >= 2) {
							mov.flockDelay = Math.max(mov.flockDelay - 100, 500);
							mov.flockDirection = EDirection.getApproxDirection(0, 0, dx, dy);
							return true;
						} else {
							mov.flockDelay = Math.min(mov.flockDelay + 100, 1000);
							return false;
						}
					}),
					goInDirectionIfAllowedAndFree(mov -> mov.flockDirection)
				),
				sequence(
					BehaviorTreeHelper.action(mov -> {
						int turnDirection = MatchConstants.random().nextInt(-8, 8);
						if (Math.abs(turnDirection) <= 1) {
							mov.lookInDirection(mov.getDirection().getNeighbor(turnDirection));
						}
					}),
					sleep(mov -> ((Movable)mov).flockDelay)
				)
		);
	}

	protected EDirection flockDirection;

	/**
	 * A call to this method indicates this movable that it shall leave it's position to free the position for another movable.
	 *
	 * @param pushingMovable
	 * 		The movable pushing at this movable. This should be the movable that want's to get the position!
	 * @return true if this movable will move out of it's way in the near future <br>
	 * false if this movable doesn't move.
	 */
	@Override
	public boolean push(ILogicMovable pushingMovable) {
		if (state == EMovableState.DEAD) {
			return false;
		}

		switch (state) {
			case DOING_NOTHING:
				if (isBusy()) { // don't go to random direction if movable shouldn't do something in DOING_NOTHING
					return false;
				}

				if (goToRandomDirection(pushingMovable)) { // try to find free direction
					return true; // if we found a free direction, go there and tell the pushing one we'll move

				} else { // if we didn't find a direction, check if it's possible to exchange positions
					if (pushingMovable.getPath() == null || !pushingMovable.getPath().hasNextStep()) {
						return false; // the other movable just pushed to get space, we can't do anything for it here.

					} else if (pushingMovable.getMovableType().isPlayerControllable()
						|| grid.isValidPosition(this, position.x, position.y)) { // exchange positions
						EDirection directionToPushing = EDirection.getApproxDirection(this.position, pushingMovable.getPosition());
						pushingMovable.goSinglePathStep(); // if no free direction found, exchange the positions of the movables
						pathStep = null;
						goInDirection(directionToPushing, EGoInDirectionMode.GO_IF_ALLOWED_WAIT_TILL_FREE);
						return true;

					} else { // exchange not possible, as the location is not valid.
						return false;
					}
				}

			case PATHING:
				if (path == null || pushingMovable.getPath() == null || !pushingMovable.getPath().hasNextStep()) {
					return false; // the other movable just pushed to get space, so we can't do anything for it in this state.
				}

				if (animationStartTime + animationDuration <= MatchConstants.clock().getTime() && this.path.hasNextStep()) {
					ShortPoint2D nextPos = path.getNextPos();
					if (pushingMovable.getPosition() == nextPos) { // two movables going in opposite direction and wanting to exchange positions
						pushingMovable.goSinglePathStep();
						this.goSinglePathStep();

					} else {
						if (grid.hasNoMovableAt(nextPos.x, nextPos.y)) {
							// this movable isn't blocked, so just let it's pathingAction() handle this
						} else if (pushedFrom == null) {
							try {
								this.pushedFrom = pushingMovable;
								return grid.getMovableAt(nextPos.x, nextPos.y).push(this);
							} finally {
								this.pushedFrom = null;
							}
						} else {
							while (pushingMovable != this) {
								pushingMovable.goSinglePathStep();
								pushingMovable = pushingMovable.getPushedFrom();
							}
							this.goSinglePathStep();
						}
					}
				}
				return true;

			case GOING_SINGLE_STEP:
			case WAITING:
				return false; // we can't do anything

			case DEBUG_STATE:
				return false;

			default:
				assert false : "got pushed in unhandled state: " + state;
				return false;
		}
	}

	@Override
	public Path getPath() {
		return path;
	}

	public boolean isProbablyPushable(ILogicMovable pushingMovable) {
		switch (state) {
			case DOING_NOTHING:
				return true;
			case PATHING:
				return path != null && pushingMovable.getPath() != null && pushingMovable.getPath().hasNextStep();
			default:
				return false;
		}
	}

	private boolean goToRandomDirection(ILogicMovable pushingMovable) {
		int offset = MatchConstants.random().nextInt(EDirection.NUMBER_OF_DIRECTIONS);
		EDirection pushedFromDir = EDirection.getApproxDirection(this.getPosition(), pushingMovable.getPosition());
		if (pushedFromDir == null) {
			return false;
		}

		for (int i = 0; i < EDirection.NUMBER_OF_DIRECTIONS; i++) {
			EDirection currDir = EDirection.VALUES[(i + offset) % EDirection.NUMBER_OF_DIRECTIONS];
			if (currDir != pushedFromDir && currDir != pushedFromDir.rotateRight(1)
				&& currDir != pushedFromDir.rotateRight(EDirection.NUMBER_OF_DIRECTIONS - 1)
				&& goInDirection(currDir, EGoInDirectionMode.GO_IF_ALLOWED_AND_FREE)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Sets the material this movable is carrying to the given one.
	 *
	 * @param materialType
	 * The material type to be set
	 * @return {@link EMaterialType} that has been set before.
	 */
	public final EMaterialType setMaterial(EMaterialType materialType) {
		assert materialType != null : "MaterialType may not be null";
		EMaterialType former = this.materialType;
		this.materialType = materialType;
		return former;
	}

	private void playAnimation(EMovableAction movableAction, short duration) {
		this.animationStartTime = MatchConstants.clock().getTime();
		this.animationDuration = duration;
		this.movableAction = movableAction;
	}

	/**
	 * Lets this movable look in the given direction.
	 *
	 * @param direction
	 * The direction to look.
	 */
	public final void lookInDirection(EDirection direction) {
		this.direction = direction;
	}

	/**
	 * Tries to go a step in the given direction.
	 *
	 * @param direction
	 * 		direction to go
	 * @param mode
	 * 		Use the given mode to go.<br>
	 * @return true if the step can and will immediately be executed. <br>
	 * false if the target position is generally blocked or a movable occupies that position.
	 */
	public final boolean goInDirection(EDirection direction, EGoInDirectionMode mode) {
		ShortPoint2D targetPosition = direction.getNextHexPoint(position);

		switch (mode) {
			case GO_IF_ALLOWED_WAIT_TILL_FREE: {
				this.direction = direction;
				setState(EMovableState.PATHING);
				this.followPath(new Path(targetPosition));
				return true;
			}
			case GO_IF_ALLOWED_AND_FREE:
				if ((grid.isValidPosition(this, targetPosition.x, targetPosition.y) && grid.hasNoMovableAt(targetPosition.x, targetPosition.y))) {
					initGoingSingleStep(targetPosition);
					setState(EMovableState.GOING_SINGLE_STEP);
					return true;
				} else {
					break;
				}
		}
		return false;
	}

	@Override
	public final void setPosition(ShortPoint2D position) {
		if (visible) {
			grid.leavePosition(this.position, this);
			grid.enterPosition(position, this, true);
		}

		this.position = position;
	}

	public final void setVisible(boolean visible) {
		if (this.visible == visible) { // nothing to change
		} else if (this.visible) { // is visible and gets invisible
			grid.leavePosition(position, this);
		} else {
			grid.enterPosition(position, this, true);
		}

		this.visible = visible;
	}

	/**
	 * @param dijkstra
	 * 		if true, dijkstra algorithm is used<br>
	 * 		if false, in area finder is used.
	 * @param centerX
	 * @param centerY
	 * @param radius
	 * @param searchType
	 * @return true if a path has been found.
	 */
	public final boolean preSearchPath(boolean dijkstra, short centerX, short centerY, short radius, ESearchType searchType) {
		assert state == EMovableState.DOING_NOTHING : "this method can only be invoked in state DOING_NOTHING";

		if (dijkstra) {
			this.path = grid.searchDijkstra(this, centerX, centerY, radius, searchType);
		} else {
			this.path = grid.searchInArea(this, centerX, centerY, radius, searchType);
		}

		return path != null;
	}

	protected boolean isBusy() {
		return state != EMovableState.DOING_NOTHING;
	}

	public boolean isOnOwnGround() {
		return grid.getPlayerAt(position) == player;
	}

	private void followPath(Path path) {
		this.path = path;
		setState(EMovableState.PATHING);
		this.movableAction = EMovableAction.NO_ACTION;
		pathingAction();
	}

	/**
	 * Sets the state to the given one and resets the movable to a clean start of this state.
	 *
	 * @param newState
	 */
	protected void setState(EMovableState newState) {
		this.state = newState;
	}

	/**
	 * kills this movable.
	 */
	@Override
	public final void kill() {
		if(state == EMovableState.DEAD) return;

		decoupleMovable();

		if (state != EMovableState.ON_FERRY) { // position of the movable on a ferry is the position it loaded into the ferry => not correct => don't show ghost
			grid.addSelfDeletingMapObject(position, EMapObjectType.GHOST, Constants.GHOST_PLAY_DURATION, player);
		}

		killMovable();
	}

	protected void decoupleMovable() {
		if (state == EMovableState.DEAD) {
			return; // this movable already died.
		}

		grid.leavePosition(this.position, this);

		MovableManager.remove(this);
	}

	protected void killMovable() {
		this.health = -200;
		this.state = EMovableState.DEAD;
		this.selected = false;
		position = null;
	}

	public boolean isOnFerry() {
		return state == EMovableState.ON_FERRY;
	}

	/**
	 * Gets the player object of this movable.
	 *
	 * @return The player object of this movable.
	 */
	public final Player getPlayer() {
		return player;
	}

	@Override
	public final boolean isSelected() {
		return selected;
	}

	@Override
	public final boolean isWounded() {
		return health < movableType.health;
	}

	@Override
	public final void setSelected(boolean selected) {
		this.selected = selected;
	}

	@Override
	public void stopOrStartWorking(boolean stop) {
	}

	@Override
	public final ESelectionType getSelectionType() {
		return movableType.selectionType;
	}

	@Override
	public final void setSoundPlayed() {
		this.soundPlayed = true;
	}

	@Override
	public final boolean isSoundPlayed() {
		return soundPlayed;
	}

	@Override
	public final EMovableType getMovableType() {
		return movableType;
	}

	@Override
	public final EMovableAction getAction() {
		return movableAction;
	}

	@Override
	public final EDirection getDirection() {
		return direction;
	}

	@Override
	public final float getMoveProgress() {
		return ((float) (MatchConstants.clock().getTime() - animationStartTime)) / animationDuration;
	}

	@Override
	public final EMaterialType getMaterial() {
		return materialType;
	}

	@Override
	public final float getHealth() {
		return health;
	}

	@Override
	public final boolean isAlive() {
		return health > 0;
	}

	@Override
	public final boolean isRightstep() {
		return isRightstep;
	}

	@Override
	public final boolean needsPlayersGround() {
		return movableType.needsPlayersGround();
	}

	@Override
	public final void debug() {
		System.out.println("debug: " + this);
	}

	@Override
	public final int getID() {
		return id;
	}

	@Override
	public String toString() {
		return "Movable: " + id + " position: " + position + " player: " + player.playerId + " movableType: " + movableType
			+ " direction: " + direction + " material: " + materialType;
	}

	protected enum EMovableState {
		PATHING,
		DOING_NOTHING,
		GOING_SINGLE_STEP,
		WAITING,
		ON_FERRY,

		DEAD,

		/**
		 * This state may only be used for debugging reasons!
		 */
		DEBUG_STATE
	}

	public boolean isShip() {
		return movableType.isShip();
	}

	public final void setDirection(EDirection direction) {
		this.direction = direction;
	}

	public void addEffect(EEffectType effect) {
		effectEnd[effect.ordinal()] = effect.getTime()*1000 + MatchConstants.clock().getTime();
	}

	@Override
	public boolean hasEffect(EEffectType effect) {
		return effectEnd[effect.ordinal()] >= MatchConstants.clock().getTime();
	}

	protected int getEffectTime(EEffectType effect) {
		return effectEnd[effect.ordinal()] - MatchConstants.clock().getTime();
	}

	protected static <T extends Movable> Guard<T> handleFrozenEffect() {
		return guard(mov -> mov.hasEffect(EEffectType.FROZEN),
				BehaviorTreeHelper.sleep(mov -> mov.getEffectTime(EEffectType.FROZEN))
		);
	}


	public static Movable createMovable(EMovableType movableType, Player player, ShortPoint2D position, AbstractMovableGrid grid) {
		return createMovable(movableType, player, position, grid, null);
	}

	protected static Movable createMovable(EMovableType movableType, Player player, ShortPoint2D position, AbstractMovableGrid grid, Movable replaceMovable) {
		if(replaceMovable != null) replaceMovable.decoupleMovable();

		Movable movable = chooseMovableClass(movableType, player, position, grid, replaceMovable);

		MovableManager.add(movable);
		grid.enterPosition(position, movable, true);

		if(replaceMovable != null) replaceMovable.killMovable();
		return movable;
	}

	private static Movable chooseMovableClass(EMovableType movableType, Player player, ShortPoint2D position, AbstractMovableGrid grid, Movable movable) {
		switch (movableType) {
			case SWORDSMAN_L1:
			case SWORDSMAN_L2:
			case SWORDSMAN_L3:
			case PIKEMAN_L1:
			case PIKEMAN_L2:
			case PIKEMAN_L3:
				return new InfantryMovable(grid, movableType, position, player, movable);

			case BOWMAN_L1:
			case BOWMAN_L2:
			case BOWMAN_L3:
				return new BowmanMovable(grid, movableType, position, player, movable);

			case MAGE:
				return new MageMovable(grid, position, player, movable);


			case BEARER:
				return new BearerMovable(grid, position, player, movable);
			case DIGGER:
				return new DiggerMovable(grid, position, player, movable);
			case BRICKLAYER:
				return new BricklayerMovable(grid, position, player, movable);


			case THIEF:
				return new ThiefMovable(grid, position, player, movable);
			case PIONEER:
				return new PioneerMovable(grid, position, player, movable);
			case GEOLOGIST:
				return new GeologistMovable(grid, position, player, movable);

			case CARGO_SHIP:
				return new CargoShipMovable(grid, position, player, movable);
			case FERRY:
				return new FerryMovable(grid, position, player, movable);

			case DONKEY:
				return new DonkeyMovable(grid, movableType, position, player, movable);

			case BAKER:
			case CHARCOAL_BURNER:
			case FARMER:
			case FISHERMAN:
			case FORESTER:
			case MELTER:
			case MILLER:
			case MINER:
			case PIG_FARMER:
			case DONKEY_FARMER:
			case LUMBERJACK:
			case SAWMILLER:
			case SLAUGHTERER:
			case SMITH:
			case STONECUTTER:
			case WATERWORKER:
			case WINEGROWER:
			case DOCKWORKER:
			case BREWER:
			case RICE_FARMER:
			case DISTILLER:
			case BEEKEEPER:
			case MEADKEEPER:
				return new BuildingWorkerMovable(grid, movableType, position, player, movable);

			case HEALER:
				return new HealerMovable(grid, position, player, movable);

			default:
				throw new AssertionError("movable type " + movableType + " is not mapped to a movable class!");
		}
	}
}

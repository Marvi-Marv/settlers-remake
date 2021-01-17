package jsettlers.logic.movable.civilian;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import jsettlers.algorithms.simplebehaviortree.BehaviorTreeHelper;
import jsettlers.algorithms.simplebehaviortree.Node;
import jsettlers.algorithms.simplebehaviortree.Root;
import jsettlers.algorithms.simplebehaviortree.nodes.Repeat;
import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.buildings.jobs.EBuildingJobType;
import jsettlers.common.buildings.jobs.IBuildingJob;
import jsettlers.common.landscape.EResourceType;
import jsettlers.common.map.shapes.MapCircle;
import jsettlers.common.map.shapes.MapCircleIterator;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.material.EPriority;
import jsettlers.common.menu.messages.SimpleMessage;
import jsettlers.common.movable.EDirection;
import jsettlers.common.movable.EMovableAction;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.player.ECivilisation;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.buildings.Building;
import jsettlers.logic.buildings.workers.DockyardBuilding;
import jsettlers.logic.buildings.workers.MillBuilding;
import jsettlers.logic.buildings.workers.SlaughterhouseBuilding;
import jsettlers.logic.map.grid.partition.manager.manageables.IManageableWorker;
import jsettlers.logic.map.grid.partition.manager.manageables.interfaces.IWorkerRequestBuilding;
import jsettlers.logic.movable.Movable;
import jsettlers.logic.movable.interfaces.AbstractMovableGrid;
import jsettlers.logic.movable.interfaces.IAttackableHumanMovable;
import jsettlers.logic.movable.interfaces.IBuildingWorkerMovable;
import jsettlers.logic.movable.interfaces.IHealerMovable;
import jsettlers.logic.movable.interfaces.ILogicMovable;
import jsettlers.logic.player.Player;

import static jsettlers.algorithms.simplebehaviortree.BehaviorTreeHelper.*;

public class BuildingWorkerMovable extends CivilianMovable implements IBuildingWorkerMovable, IManageableWorker {

	private transient IBuildingJob currentJob = null;
	protected IWorkerRequestBuilding building;
	private boolean registered = false;

	private EMaterialType poppedMaterial;
	private int searchFailedCtr = 0;

	private ShortPoint2D markedPosition;
	private IAttackableHumanMovable nextPatient = null;

	public BuildingWorkerMovable(AbstractMovableGrid grid, EMovableType movableType, ShortPoint2D position, Player player, Movable replace) {
		super(grid, movableType, position, player, replace, tree);
	}

	private static final Root<BuildingWorkerMovable> tree = new Root<>(createBuildingWorkerBehaviour());

	private static Node<BuildingWorkerMovable> createBuildingWorkerBehaviour() {
		return guardSelector(
				fleeIfNecessary(),
				guard(mov -> mov.building != null && mov.building.isDestroyed(),
					BehaviorTreeHelper.action(BuildingWorkerMovable::buildingDestroyed)
				),
				guard(mov -> mov.currentJob != null,
					selector(
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.GO_TO),
							nodeToJob(goToPos(BuildingWorkerMovable::getCurrentJobPos, BuildingWorkerMovable::pathStep))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.TRY_TAKING_RESOURCE),
							nodeToJob(condition(BuildingWorkerMovable::tryTakingResource))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.TRY_TAKING_FOOD),
							nodeToJob(condition(mov -> mov.building.tryTakingFood(mov.currentJob.getFoodOrder())))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.WAIT),
							BehaviorTreeHelper.sleep(mov -> (int)(mov.currentJob.getTime()*1000)),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.WALK),
							nodeToJob(goInDirectionWaitFree(mov -> mov.currentJob.getDirection(), BuildingWorkerMovable::pathStep))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.SHOW),
							repeat(Repeat.Policy.PREEMPTIVE,
									mov -> mov.building.getPriority() == EPriority.STOPPED,
									alwaysRunning()
							),
							BehaviorTreeHelper.action(mov -> {
								ShortPoint2D pos = mov.getCurrentJobPos();
								if (mov.currentJob.getDirection() != null) {
									mov.lookInDirection(mov.currentJob.getDirection());
								}
								mov.setPosition(pos);
								mov.setVisible(true);
							}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.HIDE),
							BehaviorTreeHelper.action(mov -> {mov.setVisible(false);}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.SET_MATERIAL),
							BehaviorTreeHelper.action(mov -> {mov.setMaterial(mov.currentJob.getMaterial());}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.TAKE),
							nodeToJob(take(mov -> mov.currentJob.getMaterial(), mov -> mov.currentJob.isTakeMaterialFromMap(), mov -> {}))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.DROP),
							BehaviorTreeHelper.action(mov -> {mov.dropAction(mov.currentJob.getMaterial());}),
							nodeToJob(drop(mov -> mov.currentJob.getMaterial(), mov -> true))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.DROP_POPPED),
							BehaviorTreeHelper.action(mov -> {mov.dropAction(mov.poppedMaterial);}),
							nodeToJob(drop(mov -> mov.poppedMaterial, mov -> true))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PRE_SEARCH),
							nodeToJob(condition(mov -> mov.preSearchPathAction(true)))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PRE_SEARCH_IN_AREA),
							nodeToJob(condition(mov -> mov.preSearchPathAction(false)))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.FOLLOW_SEARCHED),
							resetAfter(BuildingWorkerMovable::clearMark,
								nodeToJob(
									sequence(
										BehaviorTreeHelper.action(BuildingWorkerMovable::mark),
										followPresearchedPath(BuildingWorkerMovable::pathStep)
									)
								)
							)
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.LOOK_AT_SEARCHED),
							nodeToJob(condition(BuildingWorkerMovable::lookAtSearched))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.GO_TO_DOCK),
							nodeToJob(goToPos(mov -> ((DockyardBuilding)mov.building).getDock().getPosition(), BuildingWorkerMovable::pathStep))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.BUILD_SHIP),
							BehaviorTreeHelper.action(mov -> {((DockyardBuilding)mov.building).buildShipAction();}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.LOOK_AT),
							BehaviorTreeHelper.action(mov -> {mov.setDirection(mov.currentJob.getDirection());}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.EXECUTE),
							nodeToJob(condition(mov -> mov.grid.executeSearchType(mov, mov.position, mov.currentJob.getSearchType())))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PLAY_ACTION1),
							playAction(EMovableAction.ACTION1, mov -> (short)(mov.currentJob.getTime()*1000)),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PLAY_ACTION2),
							playAction(EMovableAction.ACTION2, mov -> (short)(mov.currentJob.getTime()*1000)),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PLAY_ACTION3),
							playAction(EMovableAction.ACTION3, mov -> (short)(mov.currentJob.getTime()*1000)),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.AVAILABLE),
							nodeToJob(condition(mov -> mov.grid.canTakeMaterial(mov.getCurrentJobPos(), mov.currentJob.getMaterial())))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.NOT_FULL),
							nodeToJob(condition(mov -> mov.grid.canPushMaterial(mov.getCurrentJobPos())))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.SMOKE_ON),
							BehaviorTreeHelper.action(mov -> {
								mov.grid.placeSmoke(mov.getCurrentJobPos(), true);
								mov.building.addMapObjectCleanupPosition(mov.getCurrentJobPos(), EMapObjectType.SMOKE);
							}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.SMOKE_OFF),
							BehaviorTreeHelper.action(mov -> {
								mov.grid.placeSmoke(mov.getCurrentJobPos(), false);
								mov.building.addMapObjectCleanupPosition(mov.getCurrentJobPos(), EMapObjectType.SMOKE);
							}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.START_WORKING),
							BehaviorTreeHelper.action(mov -> {
								if (mov.building instanceof SlaughterhouseBuilding) {
									((SlaughterhouseBuilding) mov.building).requestSound();
								}
								if (mov.building instanceof MillBuilding) {
									((MillBuilding) mov.building).setRotating(true);
								}
							}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.STOP_WORKING),
							BehaviorTreeHelper.action(mov -> {
								if (mov.building instanceof SlaughterhouseBuilding) {
									((SlaughterhouseBuilding) mov.building).requestSound();
								}
								if (mov.building instanceof MillBuilding) {
									((MillBuilding) mov.building).setRotating(false);
								}
							}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PIG_IS_ADULT),
							nodeToJob(condition(mov -> mov.grid.isPigAdult(mov.getCurrentJobPos())))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PIG_IS_THERE),
							nodeToJob(condition(mov -> mov.grid.hasPigAt(mov.getCurrentJobPos())))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PIG_PLACE),
							BehaviorTreeHelper.action(mov -> {
								mov.grid.placePigAt(mov.getCurrentJobPos(), true);
								mov.building.addMapObjectCleanupPosition(mov.getCurrentJobPos(), EMapObjectType.PIG);
							}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.PIG_REMOVE),
							BehaviorTreeHelper.action(mov -> {
								mov.grid.placePigAt(mov.getCurrentJobPos(), false);
								mov.building.addMapObjectCleanupPosition(mov.getCurrentJobPos(), EMapObjectType.PIG);
							}),
							jobFinishedNode()
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.POP_TOOL),
							nodeToJob(condition(BuildingWorkerMovable::popToolRequestAction))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.POP_WEAPON),
							nodeToJob(
								condition(mov -> {
									mov.poppedMaterial = mov.building.getMaterialProduction().getWeaponToProduce();
									return mov.poppedMaterial != null;
								})
							)
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.GROW_DONKEY),
							nodeToJob(
								sequence(
									condition(mov -> mov.grid.feedDonkeyAt(mov.getCurrentJobPos())),
									BehaviorTreeHelper.action(mov -> {
										mov.building.addMapObjectCleanupPosition(mov.getCurrentJobPos(), EMapObjectType.DONKEY);
									})
								)
							)
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.CAN_HEAL),
							nodeToJob(condition(BuildingWorkerMovable::canHeal))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.CALL_WOUNDED),
							nodeToJob(condition(BuildingWorkerMovable::callWounded))
						),
						sequence(
							condition(mov -> mov.currentJob.getType() == EBuildingJobType.HEAL),
							nodeToJob(condition(BuildingWorkerMovable::heal))
						),
						// unknown job type
						BehaviorTreeHelper.action(BuildingWorkerMovable::abortJob)
					)
				),
				guard(mov -> !mov.registered,
					BehaviorTreeHelper.action(mov -> {
						mov.dropCurrentMaterial();
						mov.building = null;
						mov.registered = true;
						mov.pathStep = null;
						mov.grid.addJobless(mov);
					})
				),
				doingNothingGuard()
		);
	}

	private static Node<BuildingWorkerMovable> jobFailedNode() {
		return BehaviorTreeHelper.action(BuildingWorkerMovable::jobFailed);
	}

	private static Node<BuildingWorkerMovable> jobFinishedNode() {
		return BehaviorTreeHelper.action(BuildingWorkerMovable::jobFinished);
	}

	private static Node<BuildingWorkerMovable> nodeToJob(Node<BuildingWorkerMovable> child) {
		return selector(
				sequence(
					child,
					jobFinishedNode()
				),
				jobFailedNode()
		);
	}

	@Override
	protected boolean isBusy() {
		return super.isBusy() || !registered;
	}

	@Override
	public EBuildingType getGarrisonedBuildingType() {
		if(building != null) {
			return building.getBuildingVariant().getType();
		} else {
			return null;
		}
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		String currentJobName = ois.readUTF();
		if (currentJobName.equals("null")) {
			currentJob = null;
		} else {
			EBuildingType building = (EBuildingType) ois.readObject();
			ECivilisation civilisation = (ECivilisation) ois.readObject();
			currentJob = building.getVariant(civilisation).getJobByName(currentJobName);
		}
	}

	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();
		if (currentJob != null) {
			oos.writeUTF(currentJob.getName());
			oos.writeObject(building.getBuildingVariant().getType());
			oos.writeObject(building.getPlayer().getCivilisation());
		} else {
			oos.writeUTF("null");
		}
	}

	@Override
	protected void decoupleMovable() {
		super.decoupleMovable();

		dropCurrentMaterial();

		if(building != null) {
			building.leaveBuilding(this);
		} else {
			grid.removeJobless(this);
		}
	}

	private boolean canHeal() {
		ShortPoint2D jobPos = getCurrentJobPos();

		ILogicMovable movable = grid.getMovableAt(jobPos.x, jobPos.y);
		if(movable instanceof IAttackableHumanMovable && ((IAttackableHumanMovable)movable).needsTreatment()) {
			nextPatient = (IAttackableHumanMovable) movable;
			return true;
		} else {
			nextPatient = null;
			if(movable != null) movable.push(this);
			return false;
		}
	}

	private boolean callWounded() {
		// check if patient is still interested
		IAttackableHumanMovable patient = ((IHealerMovable) this).getPatient();
		if (patient != null) {
			int healX = position.x + 2;
			int healY = position.y + 2;

			if (patient.getPath() == null ||
					!patient.getPath().getTargetPosition().equals(healX, healY)) {
				// reset patient
				((IHealerMovable) this).requestTreatment(null);
			}
		}

		patient = ((IHealerMovable) this).getPatient();
		if (patient != null) return true;

		IAttackableHumanMovable bestPatient = null;
		float patientHealth = Float.MAX_VALUE;
		MapCircleIterator iter = new MapCircleIterator(new MapCircle(building.getWorkAreaCenter(), building.getBuildingVariant().getWorkRadius()));

		int width = grid.getWidth();
		int height = grid.getHeight();
		while (iter.hasNext()) {
			ShortPoint2D next = iter.next();
			if (next.x > 0 && next.x < width && next.y > 0 && next.y < height) {
				ILogicMovable potentialPatient = grid.getMovableAt(next.x, next.y);
				if (potentialPatient instanceof IAttackableHumanMovable &&
						potentialPatient.getPlayer() == player &&
						((IAttackableHumanMovable) potentialPatient).needsTreatment()) {
					float newHealth = potentialPatient.getHealth();
					if (newHealth < patientHealth) {
						bestPatient = (IAttackableHumanMovable) potentialPatient;
						patientHealth = newHealth;
					}
				}
			}
		}

		return bestPatient != null && bestPatient.pingWounded((IHealerMovable) this);
	}

	private boolean heal() {
		if(nextPatient != null) {
			nextPatient.heal();
			((IHealerMovable)this).requestTreatment(null);
			return true;
		} else {
			return false;
		}
	}

	private boolean isJobless() {
		return currentJob == null;
	}

	private boolean popToolRequestAction() {
		poppedMaterial = building.getMaterialProduction().drawRandomAbsolutelyRequestedTool(); // first priority: Absolutely set tool production requests of user
		if (poppedMaterial == null) {
			poppedMaterial = grid.popToolProductionRequest(building.getDoor()); // second priority: Tools needed by settlers (automated production)
		}
		if (poppedMaterial == null) {
			poppedMaterial = building.getMaterialProduction().drawRandomRelativelyRequestedTool(); // third priority: Relatively set tool production requests of user
		}

		return poppedMaterial != null;
	}

	private void dropAction(EMaterialType materialType) {
		if (materialType == EMaterialType.GOLD) {
			player.getEndgameStatistic().incrementAmountOfProducedGold();
		}
	}

	/**
	 * @param dijkstra
	 * 		if true, dijkstra algorithm is used<br>
	 * 		if false, in area finder is used.
	 */
	private boolean preSearchPathAction(boolean dijkstra) {
		super.setPosition(getCurrentJobPos());

		ShortPoint2D workAreaCenter = building.getWorkAreaCenter();

		boolean pathFound = super.preSearchPath(dijkstra, workAreaCenter.x, workAreaCenter.y, building.getBuildingVariant().getWorkRadius(),
				currentJob.getSearchType());

		if (pathFound) {
			searchFailedCtr = 0;
			this.building.setCannotWork(false);
			return true;
		} else {
			searchFailedCtr++;

			if (searchFailedCtr > 10) {
				this.building.setCannotWork(true);
				player.showMessage(SimpleMessage.cannotFindWork(building));
			}
			return false;
		}
	}

	private boolean tryTakingResource() {
		if(building.getBuildingVariant().isVariantOf(EBuildingType.FISHER)) {
			EDirection fishDirection = getDirection();
			return grid.tryTakingResource(fishDirection.getNextHexPoint(position), EResourceType.FISH);
		} else if(building.getBuildingVariant().isMine()) {
			return building.tryTakingResource();
		} else {
			return false;
		}
	}

	private void jobFinished() {
		this.currentJob = this.currentJob.getNextSucessJob();
	}

	private void jobFailed() {
		this.currentJob = this.currentJob.getNextFailJob();
	}

	private ShortPoint2D getCurrentJobPos() {
		return currentJob.calculatePoint(building);
	}

	private boolean lookAtSearched() {
		EDirection direction = grid.getDirectionOfSearched(position, currentJob.getSearchType());
		if (direction != null) {
			setDirection(direction);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void setWorkerJob(IWorkerRequestBuilding building) {
		this.building = building;
		this.currentJob = building.getBuildingVariant().getStartJob();
		building.occupyBuilding(this);
		this.registered = false;
	}

	@Override
	protected void abortJob() {
		if(building != null) {
			building.leaveBuilding(this);
			building = null;
			currentJob = null;
		}

		dropCurrentMaterial();
	}

	@Override
	public void buildingDestroyed() {
		setVisible(true);

		dropCurrentMaterial();

		currentJob = null;
		building = null;
	}

	private void dropCurrentMaterial() {
		EMaterialType material = getMaterial();
		if (material.isDroppable()) {
			grid.dropMaterial(position, material, true, false);
		}
		super.setMaterial(EMaterialType.NO_MATERIAL);
	}

	private void mark() {
		markedPosition = path.getTargetPosition();
		grid.setMarked(markedPosition, true);
	}

	private void clearMark() {
		if (markedPosition != null) {
			grid.setMarked(markedPosition, false);
			markedPosition = null;
		}
	}

	private boolean pathStep() {
		return isJobless() || building != null; // TODO
	}

	public ShortPoint2D getBuildingPosition() {
		if (building instanceof Building) {
			return ((Building) building).getPosition();
		} else {
			return null;
		}
	}
}

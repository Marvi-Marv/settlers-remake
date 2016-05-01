/*******************************************************************************
 * Copyright (c) 2015, 2016
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.logic.buildings.military;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

import jsettlers.algorithms.path.IPathCalculatable;
import jsettlers.algorithms.path.Path;
import jsettlers.algorithms.path.dijkstra.DijkstraAlgorithm.DijkstraContinuableRequest;
import jsettlers.common.CommonConstants;
import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.buildings.IBuilding;
import jsettlers.common.buildings.IBuildingOccupyer;
import jsettlers.common.buildings.OccupyerPlace;
import jsettlers.common.map.shapes.FreeMapArea;
import jsettlers.common.map.shapes.MapCircle;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.mapobject.IAttackableTowerMapObject;
import jsettlers.common.material.ESearchType;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.movable.ESoldierClass;
import jsettlers.common.movable.ESoldierType;
import jsettlers.common.movable.IMovable;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.graphics.messages.SimpleMessage;
import jsettlers.logic.buildings.Building;
import jsettlers.logic.buildings.IBuildingsGrid;
import jsettlers.logic.constants.Constants;
import jsettlers.logic.constants.MatchConstants;
import jsettlers.logic.movable.Movable;
import jsettlers.logic.movable.interfaces.IAttackable;
import jsettlers.logic.movable.interfaces.IAttackableMovable;
import jsettlers.logic.objects.StandardMapObject;
import jsettlers.logic.player.Player;

/**
 * This is a tower building that can dijkstraRequest soldiers and let them defend the tower.
 *
 * @author Andreas Eberle
 */
public class OccupyingBuilding extends Building implements IBuilding.IOccupyed, IPathCalculatable, IOccupyableBuilding, Serializable {
	private static final long serialVersionUID = 5267249978497095473L;

	private static final int TIMER_PERIOD = 500;

	private final LinkedList<OccupyerPlace> emptyPlaces = new LinkedList<>();
	private final LinkedList<SoldierRequest> searchedSoldiers = new LinkedList<>();
	private final HashMap<IBuildingOccupyableMovable, SoldierRequest> commingSoldiers = new LinkedHashMap<>();
	private final LinkedList<TowerOccupier> sortedOccupiers = new LinkedList<>();
	private final LinkedList<TowerOccupier> toBeReleasedOccupiers = new LinkedList<>();

	private DijkstraContinuableRequest dijkstraRequest;

	private boolean occupiedArea;
	private float doorHealth = 50f;
	private boolean inFight = false;
	private AttackableTowerMapObject attackableTowerObject = null;

	public OccupyingBuilding(EBuildingType type, Player player, ShortPoint2D position, IBuildingsGrid buildingsGrid) {
		super(type, player, position, buildingsGrid);

		initSoldierRequests();
	}

	private void initSoldierRequests() {
		final OccupyerPlace[] occupyerPlaces = super.getBuildingType().getOccupyerPlaces();
		if (occupyerPlaces.length > 0) {
			for (OccupyerPlace currPlace : occupyerPlaces) {
				emptyPlaces.add(currPlace);
			}
			requestSoldier(ESoldierType.SWORDSMAN);
		}
	}

	@Override
	protected final int constructionFinishedEvent() {
		setAttackableTowerObject(true);
		return TIMER_PERIOD + MatchConstants.random().nextInt(200); // adding random prevents simultaneous scan after map creation
	}

	private void setAttackableTowerObject(boolean set) {
		if (set) {
			attackableTowerObject = new AttackableTowerMapObject();
			super.grid.getMapObjectsManager().addAttackableTowerObject(getDoor(), attackableTowerObject);
		} else {
			super.grid.getMapObjectsManager().removeMapObjectType(getDoor().x, getDoor().y, EMapObjectType.ATTACKABLE_TOWER);
			attackableTowerObject = null;
		}
	}

	void changePlayerTo(ShortPoint2D attackerPos) {
		assert sortedOccupiers.isEmpty() : "there cannot be any occupiers in the tower when changing the player.";

		Movable attacker = super.grid.getMovable(attackerPos);
		Player newPlayer = attacker.getPlayer();

		setAttackableTowerObject(false);
		super.showFlag(false);

		resetSoldierSearch();

		super.setPlayer(newPlayer);

		if (occupiedArea) { // free the area if it had been occupied.
			super.grid.changePlayerOfTower(super.pos, newPlayer, getGroundArea());
		} else {
			occupyAreaIfNeeded();
		}

		initSoldierRequests();
		searchedSoldiers.remove(ESearchType.SOLDIER_SWORDSMAN);
		IBuildingOccupyableMovable newOccupier = attacker.setOccupyableBuilding(this);
		commingSoldiers.put(newOccupier, searchedSoldiers.pop());

		doorHealth = 0.1f;
		inFight = false;

		super.showFlag(true);
		setAttackableTowerObject(true);
	}

	private FreeMapArea getGroundArea() {
		return new FreeMapArea(super.pos, getBuildingType().getProtectedTiles());
	}

	private void resetSoldierSearch() {
		dijkstraRequest = null;
		searchedSoldiers.clear();
		emptyPlaces.clear();
		commingSoldiers.clear();
	}

	@Override
	protected final void appearedEvent() {
		occupyAreaIfNeeded();
		searchedSoldiers.remove(ESearchType.SOLDIER_SWORDSMAN);
	}

	@Override
	protected final EMapObjectType getFlagType() {
		return EMapObjectType.FLAG_DOOR;
	}

	@Override
	protected final int subTimerEvent() {
		if (doorHealth < 1 && !inFight) {
			doorHealth = Math.min(1, doorHealth + Constants.TOWER_DOOR_REGENERATION);
		}

		if (!toBeReleasedOccupiers.isEmpty()) {
			Movable movableAtDoor = grid.getMovable(super.getDoor());
			if (movableAtDoor == null) {
				TowerOccupier releasedOccupier = toBeReleasedOccupiers.pop();
				sortedOccupiers.remove(releasedOccupier);
				emptyPlaces.add(releasedOccupier.place);
				IBuildingOccupyableMovable soldier = releasedOccupier.soldier;
				soldier.leaveOccupyableBuilding(super.getDoor());
			} else {
				movableAtDoor.leavePosition();
			}
		}

		if (!searchedSoldiers.isEmpty()) {
			if (dijkstraRequest == null) {
				dijkstraRequest = new DijkstraContinuableRequest(this, super.pos.x, super.pos.y, (short) 1, Constants.TOWER_SEARCH_RADIUS);
			}

			SoldierRequest soldierRequest = searchedSoldiers.peek();
			dijkstraRequest.setSearchType(soldierRequest.getSearchType());

			Path path = super.grid.getDijkstra().find(dijkstraRequest);
			if (path != null) {
				Movable soldier = super.grid.getMovable(path.getTargetPos());
				if (soldier != null) {
					IBuildingOccupyableMovable occupier = soldier.setOccupyableBuilding(this);
					if (occupier != null) {
						searchedSoldiers.pop();
						commingSoldiers.put(occupier, soldierRequest);
						dijkstraRequest.reset();
					}
				} // soldier wasn't at the position or wasn't able to take the job to go to this building
			}
		}
		return TIMER_PERIOD;
	}

	@Override
	protected final void killedEvent() {
		setSelected(false);

		if (occupiedArea) {
			freeArea();

			int idx = 0;
			FreeMapArea buildingArea = super.getBuildingArea();
			for (TowerOccupier curr : sortedOccupiers) {
				addInformableMapObject(curr, false);// if curr is a bowman, this removes the informable map object.

				curr.getSoldier().leaveOccupyableBuilding(buildingArea.get(idx));
				idx++;
			}

			sortedOccupiers.clear();
		}

		if (attackableTowerObject != null && attackableTowerObject.currDefender != null) {
			attackableTowerObject.currDefender.soldier.leaveOccupyableBuilding(attackableTowerObject.getPos());
		}

		setAttackableTowerObject(false);
	}

	private void freeArea() {
		super.grid.freeAreaOccupiedByTower(super.pos);
	}

	@Override
	public final List<? extends IBuildingOccupyer> getOccupyers() {
		return sortedOccupiers;
	}

	@Override
	public final boolean needsPlayersGround() { // soldiers don't need players ground.
		return false;
	}

	@Override
	public final OccupyerPlace addSoldier(IBuildingOccupyableMovable soldier) {
		SoldierRequest soldierRequest = commingSoldiers.remove(soldier);
		OccupyerPlace place = soldierRequest.place;

		TowerOccupier towerOccupier = new TowerOccupier(place, soldier);
		addOccupier(towerOccupier);

		occupyAreaIfNeeded();

		soldier.setSelected(super.isSelected());

		addInformableMapObject(towerOccupier, true);

		return place;
	}

	private void addOccupier(TowerOccupier towerOccupier) {
		sortedOccupiers.add(towerOccupier);
		Collections.sort(sortedOccupiers, new Comparator<TowerOccupier>() {
			@Override
			public int compare(TowerOccupier o1, TowerOccupier o2) {
				return o1.place.getSoldierClass().ordinal - o2.place.getSoldierClass().ordinal;
			}
		});
	}

	@Override
	public void removeSoldier(IBuildingOccupyableMovable soldier) {
		TowerOccupier occupier = null;
		for (TowerOccupier currOccupier : sortedOccupiers) {
			if (currOccupier.soldier == soldier) {
				occupier = currOccupier;
				break;
			}
		}

		// if the soldier is not in the tower, just return
		if (occupier == null) {
			return;
		}

		// remove the soldier and dijkstraRequest a new one
		sortedOccupiers.remove(occupier);
		emptyPlaces.add(occupier.place);
		requestSoldier(occupier.place.getSoldierClass());
	}

	protected TowerOccupier removeSoldier() {
		TowerOccupier removedSoldier = sortedOccupiers.removeFirst();

		addInformableMapObject(removedSoldier, false);

		return removedSoldier;
	}

	/**
	 * Adds or removes the informable map object for the given soldier.
	 *
	 * @param soldier
	 * @param add     if true, the object is added<br>
	 *                if false, the object is removed.
	 */
	private void addInformableMapObject(TowerOccupier soldier, boolean add) {
		if (soldier.place.getSoldierClass() == ESoldierClass.BOWMAN) {
			ShortPoint2D position = getTowerBowmanSearchPosition(soldier.place);

			if (add) {
				super.grid.getMapObjectsManager().addInformableMapObjectAt(position, soldier.getSoldier().getMovable());
			} else {
				super.grid.getMapObjectsManager().removeMapObjectType(position.x, position.y, EMapObjectType.INFORMABLE_MAP_OBJECT);
			}
		}
	}

	@Override
	public ShortPoint2D getTowerBowmanSearchPosition(OccupyerPlace place) {
		ShortPoint2D pos = place.getPosition().calculatePoint(super.pos);
		// FIXME @Andreas Eberle introduce new field in the buildings xml file
		ShortPoint2D position = new ShortPoint2D(pos.x + 3, pos.y + 6);
		return position;
	}

	private final void occupyAreaIfNeeded() {
		if (!occupiedArea) {
			MapCircle occupying = new MapCircle(super.pos, CommonConstants.TOWER_RADIUS);
			super.grid.occupyAreaByTower(super.getPlayer(), occupying, getGroundArea());
			occupiedArea = true;
		}
	}

	@Override
	public final void requestFailed(IBuildingOccupyableMovable soldier) {
		SoldierRequest soldierRequest = commingSoldiers.remove(soldier.getMovable());
		searchedSoldiers.add(soldierRequest);
	}

	@Override
	public final ShortPoint2D getPosition(IBuildingOccupyableMovable soldier) {
		for (TowerOccupier curr : sortedOccupiers) {
			if (curr.getSoldier() == soldier) {
				return curr.place.getPosition().calculatePoint(super.pos);
			}
		}
		return null;
	}

	@Override
	public final void setSelected(boolean selected) {
		super.setSelected(selected);
		for (TowerOccupier curr : sortedOccupiers) {
			curr.getSoldier().setSelected(selected);
		}

		if (attackableTowerObject != null && attackableTowerObject.currDefender != null) {
			attackableTowerObject.currDefender.getSoldier().setSelected(selected);
		}
	}

	@Override
	public final boolean isOccupied() {
		return !sortedOccupiers.isEmpty() || inFight;
	}

	@Override
	public void towerDefended(IBuildingOccupyableMovable soldier) {
		inFight = false;
		if (attackableTowerObject.currDefender == null) {
			System.err.println("ERROR: WHAT? No defender in a defended tower!");
		} else {
			addOccupier(new TowerOccupier(attackableTowerObject.currDefender.place, soldier));
			attackableTowerObject.currDefender = null;
		}
		doorHealth = 0.1f;
	}

	@Override
	public int getMaximumRequestedSoldiers(ESoldierClass soldierClass) {
		// TODO implement this correctly
		return 0;
	}

	@Override
	public int getCurrentlyCommingSoldiers(ESoldierClass soldierClass) {
		// TODO implement this correctly
		return 0;
	}

	@Override
	public void requestSoldier(Movable soldier) {
		if (!soldier.getMovableType().isSoldier()) {
			return;
		}

		ESoldierClass soldierClass = soldier.getMovableType().getSoldierClass();
		OccupyerPlace emptyPlace = getEmptyPlaceForSoldierClass(soldierClass);

		if (emptyPlace == null) {
			return;
		}

		IBuildingOccupyableMovable occupier = soldier.setOccupyableBuilding(this);
		commingSoldiers.put(occupier, new SoldierRequest(soldierClass, emptyPlace));
	}

	public void requestSoldiers() {
		for (OccupyerPlace emptyPlace : emptyPlaces) {
			searchedSoldiers.add(new SoldierRequest(emptyPlace.getSoldierClass(), emptyPlace));
		}
		emptyPlaces.clear();
	}

	public void requestSoldier(ESoldierType soldierType) {
		OccupyerPlace emptyPlace = getEmptyPlaceForSoldierClass(soldierType.getSoldierClass());
		if (emptyPlace != null) {
			emptyPlaces.remove(emptyPlace);
			searchedSoldiers.add(new SoldierRequest(soldierType, emptyPlace));
		}
	}

	private void requestSoldier(ESoldierClass soldierClass) {
		OccupyerPlace emptyPlace = getEmptyPlaceForSoldierClass(soldierClass);
		if (emptyPlace != null) {
			emptyPlaces.remove(emptyPlace);
			searchedSoldiers.add(new SoldierRequest(soldierClass, emptyPlace));
		}
	}

	public void releaseSoldiers() {
		toBeReleasedOccupiers.addAll(sortedOccupiers); // release all but first occupier
		toBeReleasedOccupiers.removeFirst();

		for (SoldierRequest searchedSoldier : searchedSoldiers) {
			emptyPlaces.add(searchedSoldier.place);
		}
		searchedSoldiers.clear();

		for (Entry<IBuildingOccupyableMovable, SoldierRequest> commingSoldierEntry : commingSoldiers.entrySet()) {
			commingSoldierEntry.getKey().leaveOccupyableBuilding(super.getDoor());
			emptyPlaces.add(commingSoldierEntry.getValue().place);
		}
		commingSoldiers.clear();
	}

	public void releaseSoldier(ESoldierType soldierType) {
		Iterator<SoldierRequest> searchedSoldiersIterator = searchedSoldiers.iterator();
		while (searchedSoldiersIterator.hasNext()) {
			if (searchedSoldiersIterator.next().soldierType == soldierType) {
				searchedSoldiersIterator.remove();
			}
		}

		for (Entry<IBuildingOccupyableMovable, SoldierRequest> commingSoldierEntry : commingSoldiers.entrySet()) {
			if (commingSoldierEntry.getValue().soldierType == soldierType) {
				commingSoldierEntry.getKey().leaveOccupyableBuilding(super.getDoor());
				emptyPlaces.add(commingSoldierEntry.getValue().place);

				commingSoldiers.remove(commingSoldierEntry.getKey());
				return;
			}
		}

		for (TowerOccupier occupier : sortedOccupiers) {
			if (occupier.soldier.getMovableType().getSoldierType() == soldierType && !toBeReleasedOccupiers.contains(occupier)) {
				toBeReleasedOccupiers.add(occupier);
				return;
			}
		}
	}

	private OccupyerPlace getEmptyPlaceForSoldierClass(ESoldierClass soldierClass) {
		for (OccupyerPlace place : emptyPlaces) {
			if (place.getSoldierClass() == soldierClass) {
				return place;
			}
		}

		return null;
	}

	/**
	 * This map object lies at the door of a tower and is used to signal soldiers that there is something to attack.
	 *
	 * @author Andreas Eberle
	 */
	public class AttackableTowerMapObject extends StandardMapObject implements IAttackable, IAttackableTowerMapObject {
		private static final long serialVersionUID = -5137593316096740750L;
		private TowerOccupier currDefender;

		public AttackableTowerMapObject() {
			super(EMapObjectType.ATTACKABLE_TOWER, false, OccupyingBuilding.this.getPlayerId());
		}

		@Override
		public ShortPoint2D getPos() {
			return OccupyingBuilding.this.getDoor();
		}

		@Override
		public void receiveHit(float strength, ShortPoint2D attackerPos, byte attackingPlayer) {
			Movable attacker = grid.getMovable(attackerPos);
			if (attacker != null && attacker.getPlayer() == getPlayer()) {
				return; // this can happen directly after the tower changed its player
			}

			if (doorHealth > 0) {
				doorHealth -= strength / Constants.DOOR_HIT_RESISTENCY_FACTOR;

				if (doorHealth <= 0) {
					doorHealth = 0;
					inFight = true;

					OccupyingBuilding.this.grid.getMapObjectsManager()
							.addSelfDeletingMapObject(getPos(), EMapObjectType.GHOST, Constants.GHOST_PLAY_DURATION, getPlayer());

					pullNewDefender(attackerPos);
				}
			} else if (currDefender != null) {
				IAttackableMovable movable = currDefender.getSoldier().getMovable();
				movable.receiveHit(strength, attackerPos, attackingPlayer);

				if (movable.getHealth() <= 0) {
					emptyPlaces.add(currDefender.place); // dijkstraRequest a new soldier.
					requestSoldier(currDefender.place.getSoldierClass());

					pullNewDefender(attackerPos);
				}
			}

			OccupyingBuilding.this.getPlayer().showMessage(SimpleMessage.attacked(attackingPlayer, attackerPos));
		}

		private void pullNewDefender(ShortPoint2D attackerPos) {
			if (sortedOccupiers.isEmpty()) {
				currDefender = null;
				changePlayerTo(attackerPos);
			} else {
				currDefender = removeSoldier();
				currDefender.getSoldier().setDefendingAt(getPos());
			}
		}

		@Override
		public float getHealth() {
			if (doorHealth > 0) {
				return doorHealth;
			} else {
				return currDefender == null ? 0 : currDefender.getMovable().getHealth();
			}
		}

		@Override
		public boolean isAttackable() {
			return true;
		}

		@Override
		public IMovable getMovable() {
			return currDefender == null ? null : currDefender.getSoldier().getMovable();
		}

		@Override
		public EMovableType getMovableType() {
			assert false : "This should never have been called";
			return EMovableType.SWORDSMAN_L1;
		}

		@Override
		public void informAboutAttackable(IAttackable attackable) {
		}

		@Override
		public boolean isTower() {
			return true;
		}
	}

	private final static class TowerOccupier implements IBuildingOccupyer, Serializable {
		private static final long serialVersionUID = -1491427078923346232L;

		final OccupyerPlace place;
		final IBuildingOccupyableMovable soldier;

		TowerOccupier(OccupyerPlace place, IBuildingOccupyableMovable soldier) {
			this.place = place;
			this.soldier = soldier;
		}

		@Override
		public OccupyerPlace getPlace() {
			return place;
		}

		@Override
		public IMovable getMovable() {
			return soldier.getMovable();
		}

		public IBuildingOccupyableMovable getSoldier() {
			return soldier;
		}
	}

	private static class SoldierRequest implements Serializable {
		final ESoldierClass soldierClass;
		final ESoldierType soldierType;
		final OccupyerPlace place;

		SoldierRequest(ESoldierType soldierType, OccupyerPlace place) {
			this.soldierType = soldierType;
			soldierClass = null;
			this.place = place;
		}

		SoldierRequest(ESoldierClass soldierClass, OccupyerPlace place) {
			this.soldierClass = soldierClass;
			soldierType = null;
			this.place = place;
		}

		public ESearchType getSearchType() {
			if (soldierClass != null) {
				switch (soldierClass) {
					case INFANTRY:
						return ESearchType.SOLDIER_INFANTRY;
					case BOWMAN:
						return ESearchType.SOLDIER_BOWMAN;
				}
			} else {
				switch (soldierType) {
					case SWORDSMAN:
						return ESearchType.SOLDIER_SWORDSMAN;
					case PIKEMAN:
						return ESearchType.SOLDIER_PIKEMAN;
					case BOWMAN:
						return ESearchType.SOLDIER_BOWMAN;
				}
			}
			throw new RuntimeException("Unknown soldier or search type");
		}
	}
}

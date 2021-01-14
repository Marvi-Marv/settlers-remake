/*******************************************************************************
 * Copyright (c) 2015 - 2017
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
package jsettlers.logic.buildings.others;

import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.player.ECivilisation;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.buildings.Building;
import jsettlers.logic.buildings.IBuildingsGrid;
import jsettlers.logic.buildings.stack.IRequestStack;
import jsettlers.logic.objects.ManaMapObject;
import jsettlers.logic.player.Player;

import java.util.List;

/**
 *
 * 
 * @author Andreas Eberle
 * @author MarviMarv
 *
 * TODO: check whether the consumption timer is for all civilisations the same?
 */
public final class TempleBuilding extends Building {
	private static final long serialVersionUID = 1L;

	private static final int CHECK_DELAY = 1500;
	private static final int CONSUME_DELAY = 30000;

	public TempleBuilding(Player player, ShortPoint2D position, IBuildingsGrid buildingsGrid) {
		super(EBuildingType.TEMPLE, player, position, buildingsGrid);
		setOccupied(true);
	}

	@Override
	protected int subTimerEvent() {
		IRequestStack manaStack = getManaStack();

		if (manaStack.pop()) {
			getPlayer().getMannaInformation().increaseManna();
			return CONSUME_DELAY;
		} else {
			return CHECK_DELAY;
		}
	}

	private IRequestStack getManaStack() {
		List<? extends IRequestStack> stacks = super.getStacks();
		assert stacks.size() == 1;
		IRequestStack manaStack = stacks.get(0);
		assert manaStack.getMaterialType() == (EMaterialType)this.getCivilisationManaData(1);

		return manaStack;
	}

	@Override
	protected int constructionFinishedEvent() {
		super.grid.getMapObjectsManager().addManaObject(super.getDoor(), new ManaMapObject(this.getManaStack(), this.getPlayer().getCivilisation()));
		return CHECK_DELAY;
	}

	@Override
	protected EMapObjectType getFlagType() {
		return EMapObjectType.FLAG_DOOR;
	}


	@Override
	protected void killedEvent() {
		ShortPoint2D door = super.getDoor();
		super.grid.getMapObjectsManager().removeMapObjectType(door.x, door.y, (EMapObjectType)this.getCivilisationManaData(2));
	}

	private Object getCivilisationManaData(int dataIndex) {
		switch(this.getPlayer().getCivilisation()) {
			case AMAZON:
				switch (dataIndex) {
					case 1: return EMaterialType.MEAD;
					case 2: return EMapObjectType.MEAD_BOWL;
					default: return null;
				}
			case ASIAN:
				switch (dataIndex) {
					case 1: return EMaterialType.LIQUOR;
					case 2: return EMapObjectType.LIQUOR_BOWL;
					default: return null;
				}
			case EGYPTIAN:
				switch (dataIndex) {
					case 1: return EMaterialType.KEG;
					case 2: return EMapObjectType.BEER_BOWL;
					default: return null;
				}
			case ROMAN:
			default:
				switch (dataIndex) {
					case 1: return EMaterialType.WINE;
					case 2: return EMapObjectType.WINE_BOWL;
					default: return null;
				}
		}
	}
}
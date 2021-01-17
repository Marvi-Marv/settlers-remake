/*******************************************************************************
 * Copyright (c) 2021
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
package jsettlers.logic.buildings.workers;

import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.buildings.IBuilding;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.buildings.IBuildingsGrid;
import jsettlers.logic.player.Player;

/**
 * This is a melter building (iron or gold) with the ability to melt and smoke.
 * 
 * @author MarviMarv
 */
public final class MelterBuilding extends WorkerAnimationBuilding  {
	private static final long serialVersionUID = -939588556733030645L;

	//{DURATION_SMOKE, DURATION_MELT_PROGRESS, DURATION_MELT_RESULT}
	private static final int[] DURATIONS = {3500, 1500, 6000};
	//{DURATION_SMOKE -> DURATION_MELT_PROGRESS, DURATION_MELT_PROGRESS -> DURATION_MELT_RESULT}
	private static final int[] TRANSITIONS = {2000, 1500};

	public MelterBuilding(EBuildingType type, Player player, ShortPoint2D position, IBuildingsGrid buildingsGrid) {
		super(type, player, position, buildingsGrid);
	}


	@Override
	protected int[] getAnimationDurations() {
		return DURATIONS;
	}

	@Override
	protected int[] getAnimationTransitions() {
		return TRANSITIONS;
	}
}

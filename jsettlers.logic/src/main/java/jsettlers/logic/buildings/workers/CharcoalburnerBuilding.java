package jsettlers.logic.buildings.workers;

import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.buildings.IBuildingsGrid;
import jsettlers.logic.player.Player;

/**
 * This is a charcoal burner building with the ability to show smoke.
 *
 * @author MarviMarv
 */
public final class CharcoalburnerBuilding extends WorkerAnimationBuilding  {
    private static final long serialVersionUID = -2652624466987173515L;

    //{DURATION_SMOKE} 1 coal needs 40 secs in the original game
    private static final int[] DURATIONS = {1000, 38000, 1000};
    private static final int[] TRANSITIONS = {1000, 38000};

    public CharcoalburnerBuilding(EBuildingType type, Player player, ShortPoint2D position, IBuildingsGrid buildingsGrid) {
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
package jsettlers.common.movable;

import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.position.ShortPoint2D;

public interface IGraphicsBuildingWorker extends IGraphicsMovable {

	/**
	 * This method returns the building type of a work if the worker is stationed in a building
	 *
	 * @return EBuildingType of the building the worker is garrisoned in or null if the worker is not garrisoned.
	 */
	EBuildingType getGarrisonedBuildingType();

	/**
	 * This method returns the position of the building the worker belongs to
	 *
	 * @return position of worker's building
	 */
	ShortPoint2D getBuildingPosition();
}

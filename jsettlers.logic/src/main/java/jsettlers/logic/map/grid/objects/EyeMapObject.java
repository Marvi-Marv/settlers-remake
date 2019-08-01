package jsettlers.logic.map.grid.objects;

import jsettlers.algorithms.fogofwar.FogOfWar;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.player.IPlayer;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.objects.SoundableSelfDeletingObject;

public class EyeMapObject extends SoundableSelfDeletingObject {

	private boolean drawn = false;
	private ShortPoint2D at;
	private short distance;

	EyeMapObject(ShortPoint2D pos, float duration, short distance, IPlayer player) {
		super(pos, EMapObjectType.EYE, duration, player);
		this.distance = distance;
		this.at = pos;
	}

	@Override
	protected void changeState() {
		if(FogOfWar.instance != null && FogOfWar.instance.team == getPlayer().getTeamId()) {
			if (!drawn) {
				FogOfWar.queueResizeCircle(at, (short) 0, distance);
				drawn = true;
			} else {
				FogOfWar.queueResizeCircle(at, distance, (short) 0);
			}
		}
	}
}

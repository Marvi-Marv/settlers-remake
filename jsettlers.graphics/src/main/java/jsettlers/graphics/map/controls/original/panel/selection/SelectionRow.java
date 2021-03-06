/*******************************************************************************
 * Copyright (c) 2015
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
package jsettlers.graphics.map.controls.original.panel.selection;

import go.graphics.GLDrawContext;
import go.graphics.text.EFontSize;
import go.graphics.text.TextDrawer;
import jsettlers.common.Color;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.movable.EMovableAction;
import jsettlers.common.movable.EDirection;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.player.IPlayer;
import jsettlers.graphics.image.Image;
import jsettlers.graphics.localization.Labels;
import jsettlers.graphics.map.MapDrawContext;
import jsettlers.graphics.map.draw.settlerimages.SettlerImageMap;
import jsettlers.graphics.ui.UIPanel;

public class SelectionRow extends UIPanel {

	private final EMovableType type;
	private final IPlayer player;
	private final int count;

	/**
	 * Creates a new row in the selection view
	 *
	 * @param player
	 * 			The player that owns the most settlers of this type in this selection
	 * @param type
	 *            The type of the movables
	 * @param count
	 *            How many of them are selected.
	 */
	public SelectionRow(IPlayer player, EMovableType type, int count) {
		this.player = player;
		this.type = type;
		this.count = count;
	}

	@Override
	public void drawAt(GLDrawContext gl) {
		float width = getPosition().getWidth();
		Image image =
				SettlerImageMap.getInstance().getImageForSettler(player.getCivilisation(), type,
						EMovableAction.NO_ACTION, EMaterialType.NO_MATERIAL,
						EDirection.SOUTH_EAST, 0);

		Color color = getColor();
		float bottomy = getPosition()
				.getMinY() + getPosition().getHeight() / 4;
		float left = getPosition().getMinX();
		float imagex = left + width / 20;
		if(type == EMovableType.FERRY || type == EMovableType.CARGO_SHIP) {
			image.drawImageAtRect(gl, imagex, bottomy, width/5, width/5, 1);
		} else {
			image.drawAt(gl, imagex, bottomy, 0, color, 1);
		}

		TextDrawer drawer = gl.getTextDrawer(EFontSize.NORMAL);

		drawer.drawString(left + width / 5, getPosition().getMinY() + getPosition().getHeight() * .75f, "" + count);
		drawer.drawString(left + width / 5, bottomy, Labels.getName(type));

	}

	private Color getColor() {
		return MapDrawContext.getPlayerColor(player.getPlayerId());
	}
}

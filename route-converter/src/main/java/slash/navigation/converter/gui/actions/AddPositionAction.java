/*
    This file is part of RouteConverter.

    RouteConverter is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    RouteConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RouteConverter; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Copyright (C) 2007 Christian Pesch. All Rights Reserved.
*/

package slash.navigation.converter.gui.actions;

import slash.navigation.base.BaseNavigationPosition;
import slash.navigation.converter.gui.RouteConverter;
import slash.navigation.converter.gui.models.PositionsModel;
import slash.navigation.converter.gui.models.PositionsSelectionModel;
import slash.navigation.gui.actions.FrameAction;
import slash.navigation.gui.events.Range;
import slash.navigation.util.NumberPattern;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static javax.swing.SwingUtilities.invokeLater;
import static slash.navigation.converter.gui.helper.JTableHelper.scrollToPosition;
import static slash.navigation.util.Positions.center;
import static slash.navigation.util.RouteComments.formatNumberedPosition;

/**
 * {@link Action} that inserts a new {@link BaseNavigationPosition} after
 * the last selected row of a {@link JTable}.
 *
 * @author Christian Pesch
 */

public class AddPositionAction extends FrameAction {
    private JTable table;
    private PositionsModel positionsModel;
    private PositionsSelectionModel positionsSelectionModel;

    public AddPositionAction(JTable table, PositionsModel positionsModel, PositionsSelectionModel positionsSelectionModel) {
        this.table = table;
        this.positionsModel = positionsModel;
        this.positionsSelectionModel = positionsSelectionModel;
    }

    private BaseNavigationPosition calculateCenter(int row) {
        BaseNavigationPosition position = positionsModel.getPosition(row);
        // if there is only one position or it is the first row, choose the map center
        if (row >= positionsModel.getRowCount() - 1)
            return null;
        // otherwise center between given positions
        BaseNavigationPosition second = positionsModel.getPosition(row + 1);
        if (!second.hasCoordinates() || !position.hasCoordinates())
            return null;
        return center(asList(second, position));
    }

    private String getRouteComment() {
        NumberPattern numberPattern = RouteConverter.getInstance().getNumberPatternPreference();
        String number = Integer.toString(positionsModel.getRowCount() + 1);
        String description = RouteConverter.getBundle().getString("new-position-name");
        return formatNumberedPosition(numberPattern, number, description);
    }

    private BaseNavigationPosition insertRow(int row, BaseNavigationPosition position) {
        positionsModel.add(row, position.getLongitude(), position.getLatitude(), position.getElevation(),
                position.getSpeed(), position.getTime(), getRouteComment());
        return positionsModel.getPosition(row);
    }

    private void complementRow(int row, BaseNavigationPosition position) {
        RouteConverter r = RouteConverter.getInstance();
        r.complementComment(row, position.getLongitude(), position.getLatitude());
        r.complementElevation(row, position.getLongitude(), position.getLatitude());
        r.complementTime(row, position.getTime());
    }

    private int[] asInt(List<Integer> indices) {
        int[] result = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            result[i] = indices.get(i);
        }
        return result;
    }

    public void run() {
        RouteConverter r = RouteConverter.getInstance();

        boolean hasInsertedRowInMapCenter = false;
        List<BaseNavigationPosition> insertedPositions = new ArrayList<BaseNavigationPosition>();
        int[] rowIndices = Range.revert(table.getSelectedRows());
        for (int aReverted : rowIndices) {
            int row = rowIndices.length > 0 ? aReverted : table.getRowCount();
            int insertRow = row > positionsModel.getRowCount() - 1 ? row : row + 1;

            BaseNavigationPosition center = rowIndices.length > 0 ? calculateCenter(row) :
                    positionsModel.getRowCount() > 0 ? calculateCenter(positionsModel.getRowCount() - 1) : null;
            if (center == null) {
                // only insert row in map center once
                if (hasInsertedRowInMapCenter)
                    continue;
                center = r.getMapCenter();
                hasInsertedRowInMapCenter = true;
            }

            insertedPositions.add(insertRow(insertRow, center));
        }

        if (insertedPositions.size() > 0) {
            List<Integer> insertedRows = new ArrayList<Integer>();
            for (BaseNavigationPosition position : insertedPositions) {
                int index = positionsModel.getIndex(position);
                insertedRows.add(index);
                complementRow(index, position);
            }

            final int[] rows = asInt(insertedRows);
            final int insertRow = rows.length > 0 ? rows[0] : table.getRowCount();
            invokeLater(new Runnable() {
                public void run() {
                    scrollToPosition(table, insertRow);
                    positionsSelectionModel.setSelectedPositions(rows, true);
                }
            });
        }
    }
}
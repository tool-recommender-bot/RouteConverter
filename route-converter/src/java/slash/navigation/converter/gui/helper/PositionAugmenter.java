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

package slash.navigation.converter.gui.helper;

import slash.navigation.BaseNavigationPosition;
import slash.navigation.googlemaps.GoogleMapsService;
import slash.navigation.converter.gui.RouteConverter;
import slash.navigation.converter.gui.models.PositionsModel;
import slash.navigation.geonames.GeoNamesService;
import slash.navigation.gui.Constants;
import slash.navigation.util.Conversion;
import slash.navigation.util.RouteComments;

import javax.swing.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.awt.*;

/**
 * Helps to augment positions with elevation, postal address and populated place information.
 *
 * @author Christian Pesch
 */

public class PositionAugmenter {
    private JFrame frame;

    public PositionAugmenter(JFrame frame) {
        this.frame = frame;
    }


    private interface OverwritePredicate {
        boolean shouldOverwrite(BaseNavigationPosition position);
    }

    private static final OverwritePredicate TAUTOLOGY_PREDICATE = new OverwritePredicate() {
        public boolean shouldOverwrite(BaseNavigationPosition position) {
            return true;
        }
    };

    private static final OverwritePredicate NO_ELEVATION_PREDICATE = new OverwritePredicate() {
        public boolean shouldOverwrite(BaseNavigationPosition position) {
            return position.getElevation() == null || position.getElevation() == 0.0;
        }
    };

    private static final OverwritePredicate NO_COMMENT_PREDICATE = new OverwritePredicate() {
        public boolean shouldOverwrite(BaseNavigationPosition position) {
            return Conversion.trim(position.getComment()) == null ||
                    RouteComments.isPositionComment(position.getComment());
        }
    };

    private static final OverwritePredicate NO_SPEED_PREDICATE = new OverwritePredicate() {
        public boolean shouldOverwrite(BaseNavigationPosition position) {
            return position.getSpeed() == null || position.getSpeed() == 0.0;
        }
    };


    private int[] selectAllRows(PositionsModel positionsModel) {
        int[] selectedRows = new int[positionsModel.getRowCount()];
        for (int i = 0; i < selectedRows.length; i++)
            selectedRows[i] = i;
        return selectedRows;
    }


    private interface Operation {
        String getName();
        boolean run(BaseNavigationPosition position) throws Exception;
        String getErrorMessage();
    }

    private void executeOperation(final JTable positionsTable,
                                  final PositionsModel positionsModel,
                                  final int[] rows,
                                  final OverwritePredicate predicate,
                                  final Operation operation) {
        Constants.startWaitCursor(frame.getRootPane());

        new Thread(new Runnable() {
            public void run() {
                try {
                    Exception lastException = null;
                    for (final int row : rows) {
                        BaseNavigationPosition position = positionsModel.getPosition(row);
                        if (position.hasCoordinates() && predicate.shouldOverwrite(position)) {
                            try {
                                if (operation.run(position)) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        public void run() {
                                            positionsModel.fireTableRowsUpdated(row, row);

                                            Rectangle rectangle = positionsTable.getCellRect(Math.min(row + 10, positionsModel.getRowCount()), 1, true);
                                            positionsTable.scrollRectToVisible(rectangle);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                lastException = e;
                            }
                        }
                    }
                    if (lastException != null)
                        JOptionPane.showMessageDialog(frame,
                                MessageFormat.format(operation.getErrorMessage(), lastException.getMessage()),
                                frame.getTitle(), JOptionPane.ERROR_MESSAGE);
                }
                finally {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            Constants.stopWaitCursor(frame.getRootPane());
                        }
                    });
                }
            }
        }, operation.getName()).start();
    }


    private boolean addElevation(GeoNamesService service, final BaseNavigationPosition position) throws IOException {
        final Integer elevation = service.getElevationFor(position.getLongitude(), position.getLatitude());
        if (elevation != null)
            position.setElevation(elevation.doubleValue());
        return elevation != null;
    }

    private void processElevations(final JTable positionsTable,
                                   final PositionsModel positionsModel,
                                   final int[] rows,
                                   final OverwritePredicate predicate) {
        executeOperation(positionsTable, positionsModel, rows, predicate,
                new Operation() {
                    private GeoNamesService service = new GeoNamesService();

                    public String getName() {
                        return "ElevationPositionAugmenter";
                    }

                    public boolean run(BaseNavigationPosition position) throws Exception {
                        return addElevation(service, position);
                    }

                    public String getErrorMessage() {
                        return RouteConverter.getBundle().getString("add-elevation-error");
                    }
                }
        );
    }

    public void addElevations(JTable positionsTable, PositionsModel positionsModel, int[] selectedRows) {
        processElevations(positionsTable, positionsModel, selectedRows, TAUTOLOGY_PREDICATE);
    }

    public void complementElevations(JTable positionsTable, PositionsModel positionsModel) {
        processElevations(positionsTable, positionsModel, selectAllRows(positionsModel), NO_ELEVATION_PREDICATE);
    }


    private boolean addPopulatedPlace(GeoNamesService service, BaseNavigationPosition position) throws IOException {
        String comment = service.getNearByFor(position.getLongitude(), position.getLatitude());
        if (comment != null)
            position.setComment(comment);
        return comment != null;
    }

    private void addPopulatedPlaces(final JTable positionsTable,
                                    final PositionsModel positionsModel,
                                    final int[] rows,
                                    final OverwritePredicate predicate) {
        executeOperation(positionsTable, positionsModel, rows, predicate,
                new Operation() {
                    private GeoNamesService service = new GeoNamesService();

                    public String getName() {
                        return "PopulatedPlacePositionAugmenter";
                    }

                    public boolean run(BaseNavigationPosition position) throws Exception {
                        return addPopulatedPlace(service, position);
                    }

                    public String getErrorMessage() {
                        return RouteConverter.getBundle().getString("add-populated-place-error");
                    }
                }
        );
    }

    public void addPopulatedPlaces(JTable positionsTable, PositionsModel positionsModel, int[] selectedRows) {
        addPopulatedPlaces(positionsTable, positionsModel, selectedRows, TAUTOLOGY_PREDICATE);
    }

    public void complementPopulatedPlaces(JTable positionsTable, PositionsModel positionsModel) {
        addPopulatedPlaces(positionsTable, positionsModel, selectAllRows(positionsModel), NO_COMMENT_PREDICATE);
    }


    private boolean addPostalAddress(GoogleMapsService service, BaseNavigationPosition position) throws IOException {
        String comment = service.getLocationFor(position.getLongitude(), position.getLatitude());
        if (comment != null)
            position.setComment(comment);
        return comment != null;
    }

    private void addPostalAddresses(final JTable positionsTable,
                                    final PositionsModel positionsModel,
                                    final int[] rows,
                                    final OverwritePredicate predicate) {
        executeOperation(positionsTable, positionsModel, rows, predicate,
                new Operation() {
                    private GoogleMapsService service = new GoogleMapsService();

                    public String getName() {
                        return "PostalAddressPositionAugmenter";
                    }

                    public boolean run(BaseNavigationPosition position) throws Exception {
                        return addPostalAddress(service, position);
                    }

                    public String getErrorMessage() {
                        return RouteConverter.getBundle().getString("add-postal-address-error");
                    }
                }
        );
    }

    public void addPostalAddresses(JTable positionsTable, PositionsModel positionsModel, int[] selectedRows) {
        addPostalAddresses(positionsTable, positionsModel, selectedRows, TAUTOLOGY_PREDICATE);
    }

    public void complementPostalAddresses(JTable positionsTable, PositionsModel positionsModel) {
        addPostalAddresses(positionsTable, positionsModel, selectAllRows(positionsModel), NO_COMMENT_PREDICATE);
    }


    private void processSpeeds(final JTable positionsTable,
                               final PositionsModel positionsModel,
                               final int[] rows,
                               final OverwritePredicate predicate) {
        executeOperation(positionsTable, positionsModel, rows, predicate,
                new Operation() {
                    public String getName() {
                        return "SpeedPositionAugmenter";
                    }

                    public boolean run(BaseNavigationPosition position) throws Exception {
                        BaseNavigationPosition predecessor = positionsModel.getPredecessor(position);
                        if (predecessor != null) {
                            Double speed = position.calculateSpeed(predecessor);
                            position.setSpeed(speed);
                            return true;
                        }
                        return false;
                    }

                    public String getErrorMessage() {
                        return RouteConverter.getBundle().getString("add-speed-error");
                    }
                }
        );
    }

    public void addSpeeds(JTable positionsTable, PositionsModel positionsModel, int[] selectedRows) {
        processSpeeds(positionsTable, positionsModel, selectedRows, TAUTOLOGY_PREDICATE);
    }

    public void complementSpeeds(JTable positionsTable, PositionsModel positionsModel) {
        processSpeeds(positionsTable, positionsModel, selectAllRows(positionsModel), NO_SPEED_PREDICATE);
    }
}

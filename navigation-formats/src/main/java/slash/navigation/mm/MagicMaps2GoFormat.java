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

package slash.navigation.mm;

import slash.common.io.CompactCalendar;
import slash.navigation.base.BaseNavigationPosition;
import slash.navigation.base.RouteCharacteristics;
import slash.navigation.base.SimpleLineBasedFormat;
import slash.navigation.base.SimpleRoute;
import slash.navigation.base.Wgs84Position;
import slash.navigation.base.Wgs84Route;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static slash.common.io.CompactCalendar.fromDate;
import static slash.common.io.Transfer.formatDoubleAsString;
import static slash.common.io.Transfer.parseDouble;
import static slash.common.io.Transfer.trim;
import static slash.navigation.base.RouteCharacteristics.Track;

/**
 * Reads and writes MagicMaps2Go (.txt) files.
 * <p/>
 * Format: 52.4135141 13.3115464 40.8000000 31.05.09 07:05:58
 *
 * @author Christian Pesch
 */

public class MagicMaps2GoFormat extends SimpleLineBasedFormat<SimpleRoute> {
    private static final Logger log = Logger.getLogger(MagicMaps2GoFormat.class.getName());
    
    private static final char SEPARATOR = ' ';
    private static final DateFormat DATE_AND_TIME_FORMAT = new SimpleDateFormat("dd.MM.yy HH:mm:ss");
    static {
       DATE_AND_TIME_FORMAT.setTimeZone(CompactCalendar.UTC);
    }

    private static final Pattern LINE_PATTERN = Pattern.
            compile(BEGIN_OF_LINE +
                    "(" + POSITION + ")" + SEPARATOR +
                    "(" + POSITION + ")" + SEPARATOR +
                    "(" + POSITION + ")" + SEPARATOR +
                    "(\\d\\d\\.\\d\\d\\.\\d\\d)" + SEPARATOR +
                    "(\\d\\d\\:\\d\\d\\:\\d\\d)" +
                    END_OF_LINE);

    public String getExtension() {
        return ".txt";
    }

    public String getName() {
        return "MagicMaps2Go (*" + getExtension() + ")";
    }

    protected RouteCharacteristics getRouteCharacteristics() {
        return Track;
    }

    @SuppressWarnings({"unchecked"})
    public <P extends BaseNavigationPosition> SimpleRoute createRoute(RouteCharacteristics characteristics, String name, List<P> positions) {
        return new Wgs84Route(this, characteristics, (List<Wgs84Position>) positions);
    }

    protected boolean isPosition(String line) {
        Matcher matcher = LINE_PATTERN.matcher(line);
        return matcher.matches();
    }

    private CompactCalendar parseDateAndTime(String date, String time) {
        time = trim(time);
        date = trim(date);
        String dateAndTime = date + " " + time;
        try {
            Date parsed = DATE_AND_TIME_FORMAT.parse(dateAndTime);
            return fromDate(parsed);
        } catch (ParseException e) {
            log.severe("Could not parse date and time '" + dateAndTime + "'");
        }
        return null;
    }

    protected Wgs84Position parsePosition(String line, CompactCalendar startDate) {
        Matcher lineMatcher = LINE_PATTERN.matcher(line);
        if (!lineMatcher.matches())
            throw new IllegalArgumentException("'" + line + "' does not match");
        String latitude = lineMatcher.group(1);
        String longitude = lineMatcher.group(2);
        String elevation = lineMatcher.group(3);
        String date = lineMatcher.group(4);
        String time = lineMatcher.group(5);
        return new Wgs84Position(parseDouble(longitude), parseDouble(latitude),
                parseDouble(elevation), null, parseDateAndTime(date, time), null);
    }

    protected void writePosition(Wgs84Position position, PrintWriter writer, int index, boolean firstPosition) {
        String latitude = formatDoubleAsString(position.getLatitude(), 7);
        String longitude = formatDoubleAsString(position.getLongitude(), 7);
        String elevation = formatDoubleAsString(position.getElevation(), 7);
        String dateAndTime = position.getTime() != null ? DATE_AND_TIME_FORMAT.format(position.getTime().getTime()) : "00.00.00 00:00:=00";
        writer.println(latitude + SEPARATOR + longitude + SEPARATOR + elevation + SEPARATOR + dateAndTime);
    }
}
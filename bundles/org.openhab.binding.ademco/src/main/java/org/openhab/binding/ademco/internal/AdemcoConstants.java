/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.ademco.internal;

import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link AdemcoConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author WeeMin Chan - Initial contribution
 */
@NonNullByDefault
public class AdemcoConstants {

    public static final String BINDING_ID = "ademco";
    public static final short DEFAULT_PARTITION = 1;
    public static final short PARTITIONSIZE = 8;
    public static final short PARTITION_READY_STATUS = 1;

    // List of bridge device types
    public static final String ENVISALINK_BRIDGE = "envisalink";

    // List of bridge Partition
    public static final String PARTITION_BRIDGE = "partition";

    // List of Thing Zone
    public static final String ZONE_THING = "zone";

    // List of all Thing Type UIDs
    public static final ThingTypeUID ENVISALINKBRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, ENVISALINK_BRIDGE);
    public static final ThingTypeUID PARTITION_THING_TYPE = new ThingTypeUID(BINDING_ID, PARTITION_BRIDGE);
    public static final ThingTypeUID ZONE_THING_TYPE = new ThingTypeUID(BINDING_ID, ZONE_THING);

    // Constants use by EnvisalinkBridgeHandler
    public static final String BRIDGE_CLOSE = "Bridge close";

    // List of Partition Channel ids
    public static final String statusID[] = { "IconAlarm", "IconAlarmInMemory", "IconArmedAway", "IconACPresent",
            "IconBypass", "IconChime", "", "IconArmedInstant", "IconFireAlarm", "IconTrouble", "", "", "IconReady",
            "IconFire", "IconLowBattery", "IconArmedStay" };
    public static final String ARM_MODE = "ArmMode";

    public static final String ICONARMEDSTAY = "IconArmedStay";
    public static final String ICONARMEDAWAY = "IconArmedAway";
    public static final String ICONARMEDINSTANT = "IconArmedInstant";
    public static final String ICONCHIME = "IconChime";

    public static final Integer ARM_MODE_READY = 1;
    public static final Integer ARM_MODE_AWAY = 2;
    public static final Integer ARM_MODE_STAY = 3;
    public static final Integer ARM_MODE_INSTANT = 7;
    public static final Integer ARM_MODE_CHIME = 9;

    // Partition Beep status
    public static final int PARTITION_BEEP_OFF = 0;
    public static final int PARTITION_BEEP_ONCE = 1;
    public static final int PARTITION_BEEP_TWICE = 2;
    public static final int PARTITION_BEEP_THRICE = 3;
    public static final int PARTITION_BEEP_FAST = 4;
    public static final int PARTITION_BEEP_SLOW = 5;

    public static final short PARTITION_STATUS_READY = 1;
    public static final short PARTITION_STATUS_AWAY = 5;
    public static final short PARTITION_STATUS_STAY = 4;
    public static final short PARTITION_STATUS_INSTANT = 6;
    public static final String ARM_STAY = statusID[15];
    public static final String ARM_INSTANT = statusID[7];
    public static final String PARTITION_MESSAGE = "partitionMessage";
    public static final String PARTITION_STATUS = "partitionStatus";

    // List of Zone Channel ids
    public static final String ZONE_STATUS = "zoneStatus";
    public static final String ZONE_FAULT = "zoneFault";
    public static final String ZONE_BYPASS = "zoneBypass";
    public static final String ZONE_ALARM = "zoneAlarm";
    public static final String ZONE_CHECK = "zoneCheck";
    public static final String ZONE_LOBAT = "zoneLowBattery";

    public static final String ZONELASTUPDATEDTS = "zoneLastUpdated";

    // ZoneAction
    public static final String ZONE_ACTION_FAULTED = "FAULT";
    public static final String ZONE_ACTION_BYPASS = "BYPAS";
    public static final String ZONE_ACTION_ALARMED = "ALARM";
    public static final String ZONE_ACTION_CHECK = "CHECK";
    public static final String ZONE_ACTION_LOBAT = "LOBAT";

    public static final String LOGIN = "Login:";
    public static final String LOGIN_OK = "OK";
    public static final String LOGIN_FAILED = "FAILED";
    public static final String LOGIN_TIMEOUT = "Timed Out";
    public static final String non_command_input_regex = "^[*/#]?+\\d{1,5}[*/#]?+$";
    public static final Pattern command_input_regex = Pattern.compile("^\\^(0[0-3]{1}),([0-9]*)\\$$");
    public static final Pattern ZONEACTION_REGEX = Pattern.compile("^(\\D+)(\\d+)(\\D+)\\s?.*");
    public static final int COMMAND_KEYPADUPDATE = 0;
    public static final int COMMAND_ZONESATECHANGE = 1;
    public static final int COMMAND_PARTITIONSTATECHANGE = 2;
    public static final int COMMAND_REALTIMECID = 3;
    public static final int COMMAND_ZONETIMERDUMP = 0xFF;
}

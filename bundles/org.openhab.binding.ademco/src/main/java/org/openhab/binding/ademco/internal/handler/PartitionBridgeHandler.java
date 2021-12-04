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
package org.openhab.binding.ademco.internal.handler;

import static org.openhab.binding.ademco.internal.AdemcoConstants.*;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.ademco.internal.config.PartitionBridgeConfiguration;
import org.openhab.binding.ademco.internal.discovery.AdemcoDiscoveryService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PartitionBridgeHandler} class extends BaseBridgeHandler
 *
 * @author WeeMin Chan - Initial contribution
 */
public class PartitionBridgeHandler extends BaseBridgeHandler {

    public PartitionBridgeHandler(Bridge bridge) {
        super(bridge);
        config = this.getConfigAs(PartitionBridgeConfiguration.class);
    }

    private Logger logger = LoggerFactory.getLogger(PartitionBridgeHandler.class);

    // This map will store zone that's configured by the user. We are using hashmap for lookup, not sure if it's a
    // performance hit.
    private Map<Integer, ZoneThingHandler> configuredZoneThingHandler = new HashMap<Integer, ZoneThingHandler>();
    private PartitionBridgeConfiguration config;

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.thing.binding.BaseThingHandler#initialize()
     */
    @SuppressWarnings("null")
    @Override
    public void initialize() {
        String newLabel = String.format("Partition %d", config.partitionNumber);
        logger.debug("Change label to {}", newLabel);
        thing.setLabel(newLabel);
        ((EnvisalinkBridgeHandler) this.getBridge().getHandler()).addPartitionHandler(this, config.partitionNumber);
        this.updateStatus(ThingStatus.ONLINE);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.thing.binding.BaseThingHandler#dispose()
     */
    @Override
    public void dispose() {
        if (!this.configuredZoneThingHandler.isEmpty()) {
            for (ZoneThingHandler eachZoneHandler : this.configuredZoneThingHandler.values()) {
                eachZoneHandler.dispose();
            }
            this.configuredZoneThingHandler.clear();
        } else {
            logger.info("no zones configured!");
        }
        Bridge evlBridge = this.getBridge();
        if (evlBridge != null) {
            EnvisalinkBridgeHandler handler = (EnvisalinkBridgeHandler) evlBridge.getHandler();
            if (handler != null) {
                handler.removePartitionHandler((short) this.config.partitionNumber);
            }
        }
        super.dispose();
    }

    /**
     * Method to update partition's LED/ICON status.
     *
     * @param ledIcon
     */
    private void decodeLed(BitSet ledIcon) {
        if (ledIcon.get(2)) {
        } else if (ledIcon.get(12)) {
            updateState(ARM_MODE, new DecimalType(ARM_MODE_READY));
        } else if (ledIcon.get(7)) {
            updateState(ARM_MODE, new DecimalType(ARM_MODE_INSTANT));
        } else if (ledIcon.get(15)) {
            updateState(ARM_MODE, new DecimalType(ARM_MODE_STAY));
        }
        for (int i = 0; i < 16; i++) {
            // Skip because it's unused according to EnvisalinkTPI-ADEMCO-1-03.pdf
            if (i == 6 || i == 10 || i == 11) {
                continue;
            }
            updateState(statusID[i], ledIcon.get(i) ? OnOffType.ON : OnOffType.OFF);
        }
    }

    /**
     * @param beep
     */
    private void decodeBeep(int beep) {
        switch (beep) {
            case PARTITION_BEEP_OFF:
                break;
            case PARTITION_BEEP_ONCE:
            case PARTITION_BEEP_TWICE:
            case PARTITION_BEEP_THRICE:
                break;
            case PARTITION_BEEP_FAST:
                break;
            case PARTITION_BEEP_SLOW:
                break;

        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.openhab.core.thing.binding.ThingHandler#handleCommand(org.openhab.core.thing.ChannelUID,
     * org.openhab.core.types.Command)
     *
     * Method to handle arming of partition.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!(command instanceof RefreshType)) {
            String postfixCommand = null;
            switch (channelUID.getId()) {
                case ARM_MODE:
                    postfixCommand = command.toFullString();
                    break;
                case ICONARMEDSTAY:
                    if (command.equals(OnOffType.ON)) {
                        postfixCommand = ARM_MODE_STAY.toString();
                    } else if (command.equals(OnOffType.OFF)) {
                        postfixCommand = ARM_MODE_READY.toString();
                    }
                    break;
                case ICONARMEDAWAY:
                    if (command.equals(OnOffType.ON)) {
                        postfixCommand = ARM_MODE_AWAY.toString();
                    } else if (command.equals(OnOffType.OFF)) {
                        postfixCommand = ARM_MODE_READY.toString();
                    }
                    break;
                case ICONARMEDINSTANT:
                    if (command.equals(OnOffType.ON)) {
                        postfixCommand = ARM_MODE_INSTANT.toString();
                    } else if (command.equals(OnOffType.OFF)) {
                        postfixCommand = ARM_MODE_READY.toString();
                    }
                    break;
                case ICONCHIME:
                    if (command.equals(OnOffType.ON)) {
                        postfixCommand = ARM_MODE_CHIME.toString();
                    } else if (command.equals(OnOffType.OFF)) {
                        postfixCommand = ARM_MODE_READY.toString();
                    }
                    break;
                default:
                    break;
            }
            if (postfixCommand != null) {
                logger.debug("Sending XXXX{}", postfixCommand);
                try {
                    Bridge t_Envisalink = this.getBridge();
                    if (t_Envisalink != null) {
                        EnvisalinkBridgeHandler handler = (EnvisalinkBridgeHandler) t_Envisalink.getHandler();
                        if (handler != null && handler.isConnected()) {
                            handler.sendCommand(config.userCode + postfixCommand);
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Error found: ", e);
                }
            }

        } else {
            logger.debug("Refresh command called for {}", channelUID);
            switch (channelUID.getId()) {
                case PARTITION_MESSAGE:
                    break;
            }
        }
    }

    /**
     * Method to parse the keypad message from Envisalink.
     *
     * @param inputString
     */
    public void parseVirtualKeypadUpdate(String inputString) {
        // "01,1C28,08,00, DISARMED CHIME Ready to Arm "
        BitSet ledIcon = BitSet.valueOf(new long[] { Long.parseLong(inputString.substring(3, 7), 16) });
        // int zone = Integer.parseInt(inputString.substring(8, 10), 16);
        int beepField = Integer.parseInt(inputString.substring(11, 13), 16);
        String Msg = inputString.substring(14);
        this.updateState(PARTITION_MESSAGE, new StringType(Msg));
        decodeBeep(beepField);
        decodeLed(ledIcon);
        decodeLastMessage(Msg);
    }

    /**
     * Decode the ALPHA Field from Envisalink.
     * Currently there's no documentation on the format. As more new actions surface we will implement them.
     * NOTE: New actions will show up as warning in the log.
     *
     * @param ALPHA_Field
     */
    private void decodeLastMessage(String ALPHA_Field) {
        logger.debug("Decoding lastMessage: {}", ALPHA_Field);
        Matcher _match = ZONEACTION_REGEX.matcher(ALPHA_Field);
        if (_match.matches()) {
            // Matches Fault
            String action = _match.group(1).trim();
            String zone_name = _match.group(3).trim();
            Integer zone_index = Integer.parseInt(_match.group(2));
            logger.debug("Action {}, ZoneNumber {}, ZoneName {}", action, zone_index, zone_name);
            ZoneThingHandler t_zoneHandler = this.configuredZoneThingHandler.get(zone_index);
            if (t_zoneHandler != null) {
                t_zoneHandler.updateZoneName(zone_name);
                t_zoneHandler.setActionState(action);
            } else {
                this.addZoneToDisovery(zone_index);
            }
        }
    }

    /**
     * Method to add a ZoneThing to inbox
     *
     * @param zone_index
     */
    private void addZoneToDisovery(Integer zone_index) {
        Bridge envisalinkBridge = this.getBridge();
        EnvisalinkBridgeHandler envisalinkHandler;
        if (envisalinkBridge != null) {
            envisalinkHandler = (EnvisalinkBridgeHandler) envisalinkBridge.getHandler();
            if (envisalinkHandler != null) {
                AdemcoDiscoveryService envisalinkBridgeDiscoveryService = envisalinkHandler
                        .getEnvisalinkBridgeDiscoveryService();
                envisalinkBridgeDiscoveryService.addZoneThing(zone_index, this.getThing());
            } else {
                logger.warn("Bridge Handler is not setup!");
            }
        } else {
            logger.warn("Bridge is not setup, highly unlikely");
        }
    }

    /**
     * Parse the ZoneTimerDump from Envisalink.
     *
     * @param Data
     */
    void parseZoneTimerDump(String Data) {
        short length = (short) (Data.length() / 4);
        logger.debug("Length = {}", length);
        int temp_value;
        for (Integer i = 0; i < length; i++) {
            ZoneThingHandler currentZoneHandler = this.configuredZoneThingHandler.get(i);
            if (currentZoneHandler != null) {
                String val1 = Data.substring(i * 4 + 2, i * 4 + 4) + Data.substring(i * 4, i * 4 + 2);
                temp_value = Integer.parseInt(val1, 16);
                if (temp_value > 0) {
                    int seconds_since = (0xFFFF - temp_value) * 5;
                    ZonedDateTime now = ZonedDateTime.now();
                    currentZoneHandler.updateZoneTS(now.minus(Duration.ofSeconds(seconds_since)));
                }
            }
        }
    }

    /**
     * This method will parse the raw data from Envisalink bridge and update respective states accordingly.
     *
     * @param Data
     */
    void parseZoneStateChange(String Data) {
        short Length = (short) (Data.length() / 2);
        byte number[] = new byte[Length];
        for (int j = 0; j < Length; j++) {
            String sub1 = Data.substring(j * 2, j * 2 + 2);
            number[j] = (byte) Short.parseShort(sub1, 16);
        }
        BitSet bitZone = BitSet.valueOf(number);
        // Loop through all set bits.
        for (int i = bitZone.nextSetBit(0); i >= 0; i = bitZone.nextSetBit(i + 1)) {
            // This is because zone starts at 1 and bitset starts at 0
            int zoneAffected = i + 1;
            ZoneThingHandler currentZoneHandler = this.configuredZoneThingHandler.get(zoneAffected);
            if (currentZoneHandler == null) {
                this.addZoneToDisovery(zoneAffected);
            } else {
                currentZoneHandler.setZoneStatus(true);
            }
        }
    }

    /**
     * Method to add a zoneHandler into the mapping
     *
     * @param childHandler
     * @param zoneNumber
     */
    public void addZoneHandler(ZoneThingHandler childHandler, int zoneNumber) {
        logger.debug("Adding zone handler {} to partition {}", zoneNumber, config.partitionNumber);
        configuredZoneThingHandler.put(zoneNumber, childHandler);
    }

    /**
     * Method to remove a zoneHandler from the mapping
     *
     * @param zoneNumber
     */
    public void removeZoneHandler(int zoneNumber) {
        logger.info("Removing zone handler {} from partition {}", zoneNumber, config.partitionNumber);
        if (this.configuredZoneThingHandler.containsKey(zoneNumber)) {
            this.configuredZoneThingHandler.remove(zoneNumber);
        } else {
            logger.warn("Zone {} not in hashmap!", zoneNumber);
        }
    }

    /**
     * Handles the partition status state and armode state according to the newStatus.
     *
     * @param newStatus Status from the Envisalink bridge
     */
    public void updatePartitionStatus(Short newStatus) {
        // TODO: Look into converting passed in parameter as enum.
        switch (newStatus) {
            case PARTITION_STATUS_READY:
                setAllZoneClose();
                updateState(ARM_MODE, new DecimalType(ARM_MODE_READY));
                break;
            case PARTITION_STATUS_AWAY:
                updateState(ARM_MODE, new DecimalType(ARM_MODE_AWAY));
                break;
            case PARTITION_STATUS_INSTANT:
                updateState(ARM_MODE, new DecimalType(ARM_MODE_INSTANT));
                break;
            case PARTITION_STATUS_STAY:
                updateState(ARM_MODE, new DecimalType(ARM_MODE_STAY));
                break;
        }
        updateState(PARTITION_STATUS, new DecimalType(newStatus));
    }

    /**
     * Close all Zones that's configured.
     */
    private void setAllZoneClose() {
        this.configuredZoneThingHandler.forEach((k, v) -> v.setZoneStatus(false));
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.thing.binding.BaseThingHandler#handleConfigurationUpdate(java.util.Map)
     */
    @Override
    public void handleConfigurationUpdate(Map<@NonNull String, @NonNull Object> configurationParameters) {
        logger.debug("PartitionHandler handling config update");
        this.config = this.getConfigAs(PartitionBridgeConfiguration.class);
        super.handleConfigurationUpdate(configurationParameters);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.openhab.core.thing.binding.BaseThingHandler#thingUpdated(org.openhab.core.thing.Thing)
     */
    @Override
    public void thingUpdated(Thing thing) {
        logger.debug("PartitionHandler thingUpdated handling update");
        this.config = this.getConfigAs(PartitionBridgeConfiguration.class);
        super.thingUpdated(thing);
    }
}

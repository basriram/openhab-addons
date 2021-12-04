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

import java.time.ZonedDateTime;

import org.openhab.binding.ademco.internal.config.ZoneThingConfiguration;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ZoneThingHandler} class extends BaseThingHandler
 *
 * @author WeeMin Chan - Initial contribution
 */
public class ZoneThingHandler extends BaseThingHandler {
    private Logger logger = LoggerFactory.getLogger(ZoneThingHandler.class);
    private ZoneThingConfiguration config;

    // We are tracking in memory each Zone's label change state.
    private boolean updatedLabelName = false;

    public ZoneThingHandler(Thing thing) {
        super(thing);
        config = getConfigAs(ZoneThingConfiguration.class);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.thing.binding.BaseThingHandler#initialize()
     */
    @Override
    public void initialize() {
        PartitionBridgeHandler handler = this.getPartitionBridgeHandler();
        if (handler != null) {
            // Update the label
            thing.setLabel(String.format("Zone %d", config.zoneNumber));
            handler.addZoneHandler(this, config.zoneNumber);
            this.updateStatus(ThingStatus.ONLINE);
        }
    }

    // /*
    // * (non-Javadoc)
    // *
    // * @see org.openhab.core.thing.binding.BaseThingHandler#dispose()
    // */
    // @Override
    // public void dispose() {
    // PartitionBridgeHandler handler = this.getPartitionBridgeHandler();
    // if (handler != null) {
    // handler.removeZoneHandler(config.zoneNumber);
    // }
    // this.updateStatus(ThingStatus.OFFLINE);
    // super.dispose();
    // }

    private PartitionBridgeHandler getPartitionBridgeHandler() {
        Bridge partitionBridge = this.getBridge();
        if (partitionBridge != null) {
            PartitionBridgeHandler handler = (PartitionBridgeHandler) partitionBridge.getHandler();
            if (handler != null) {
                return handler;
            } else {
                logger.warn("Parition Handler not found!");
            }
        } else {
            logger.warn("Partition thing not found!");
        }
        return null;
    }

    /**
     * Method to update the label and configured name of the zone thing.
     *
     * @param newLabel new label to use.
     */
    public void updateZoneName(String newLabel) {
        if (!updatedLabelName) {
            String zoneName = thing.getLabel() + " " + newLabel;
            logger.info("Setting Label to {}", zoneName);
            this.thing.setLabel(zoneName);
            updatedLabelName = true;
        }
    }

    /**
     * Method to set the alarmed state of the zone.
     *
     * @param faulted true if faulted, false otherwise.
     */
    public void setActionState(boolean state, String action) {
        if (state) {
            updateState(action, OnOffType.ON);
            updateZoneTS();
        } else {
            updateState(action, OnOffType.OFF);
        }
    }

    /**
     * Method to set the action based on EVL message.
     *
     * @param action String of action from EVL.
     */
    public void setActionState(String action) {
        switch (action) {
            case (ZONE_ACTION_FAULTED):
                setActionState(true, ZONE_FAULT);
                break;
            case (ZONE_ACTION_BYPASS):
                setActionState(true, ZONE_BYPASS);
                break;
            case (ZONE_ACTION_ALARMED):
                setActionState(true, ZONE_ALARM);
                break;
            case (ZONE_ACTION_CHECK):
                setActionState(true, ZONE_CHECK);
                break;
            case (ZONE_ACTION_LOBAT):
                setActionState(true, ZONE_LOBAT);
            default:
                // Need to fill this I get more info on what messages are output on the ADEMCO message.
                logger.warn("Unknown action {} please add", action);
                break;
        }
    }

    /**
     * Method to set a zone status to open/close.
     *
     * @param newContactStatus true of Contact is open, close otherwise.
     */
    public void setZoneStatus(boolean newContactStatus) {
        if (newContactStatus) {
            updateState(ZONE_STATUS, OpenClosedType.OPEN);
            updateZoneTS();
        } else {
            updateState(ZONE_STATUS, OpenClosedType.CLOSED);
        }
    }

    /**
     * Method to update the zone last updated Time stamp.
     *
     * @param zonedDateTime Timestamp
     */
    public void updateZoneTS(ZonedDateTime zonedDateTime) {
        updateState(ZONELASTUPDATEDTS, new DateTimeType(zonedDateTime));
    }

    /**
     * Method to update the zone last updated to current time.
     */
    public void updateZoneTS() {
        updateState(ZONELASTUPDATEDTS, new DateTimeType());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO: Handle bypass of zone to EVL.
    }
}

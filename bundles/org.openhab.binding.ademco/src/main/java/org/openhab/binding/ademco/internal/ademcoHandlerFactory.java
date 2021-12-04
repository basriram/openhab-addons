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

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.ademco.internal.discovery.AdemcoDiscoveryService;
import org.openhab.binding.ademco.internal.handler.EnvisalinkBridgeHandler;
import org.openhab.binding.ademco.internal.handler.PartitionBridgeHandler;
import org.openhab.binding.ademco.internal.handler.ZoneThingHandler;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ademcoHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author WeeMin Chan - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.ademco", service = ThingHandlerFactory.class)
public class ademcoHandlerFactory extends BaseThingHandlerFactory {
    private Logger logger = LoggerFactory.getLogger(ademcoHandlerFactory.class);
    private final Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegistrations = new HashMap<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        if (thingTypeUID.equals(AdemcoConstants.ENVISALINKBRIDGE_THING_TYPE)) {
            return true;
        }
        return AdemcoConstants.BINDING_ID.equals(thingTypeUID.getBindingId());
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        logger.debug("Try Creating thing {}", thing.getUID());
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (thingTypeUID.equals(AdemcoConstants.ENVISALINKBRIDGE_THING_TYPE)) {
            logger.debug("Creating Envisalink Bridge");
            EnvisalinkBridgeHandler handler = new EnvisalinkBridgeHandler((Bridge) thing);
            this.registerAdemcoAlarmDiscoveryService(handler);
            return handler;
        } else if (thingTypeUID.equals(AdemcoConstants.PARTITION_THING_TYPE)) {
            return new PartitionBridgeHandler((Bridge) thing);
        } else if (thingTypeUID.equals(AdemcoConstants.ZONE_THING_TYPE)) {
            return new ZoneThingHandler(thing);
        }
        // Everything else gets handled in a single handler
        return null;
    }

    /**
     * Register the Thing Discovery Service for a bridge.
     *
     * @param AlarmBridgeHandler
     */
    private void registerAdemcoAlarmDiscoveryService(EnvisalinkBridgeHandler envisalinkAlarmBridgeHandler) {
        AdemcoDiscoveryService discoveryService = new AdemcoDiscoveryService(envisalinkAlarmBridgeHandler);
        discoveryService.activate();

        discoveryServiceRegistrations.put(envisalinkAlarmBridgeHandler.getThing().getUID(),
                bundleContext.registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<>()));

        logger.debug("registerDSCAlarmDiscoveryService(): Bridge Handler - {}, Class Name - {}, Discovery Service - {}",
                envisalinkAlarmBridgeHandler, DiscoveryService.class.getName(), discoveryService);
    }

    @SuppressWarnings("null")
    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        logger.debug("removeHandler called");
        if (thingHandler instanceof EnvisalinkBridgeHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegistrations
                    .get(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                // remove discovery service, if bridge handler is removed
                AdemcoDiscoveryService service = (AdemcoDiscoveryService) bundleContext
                        .getService(serviceReg.getReference());
                if (service != null) {
                    service.deactivate();
                }
                serviceReg.unregister();
                discoveryServiceRegistrations.remove(thingHandler.getThing().getUID());
            }
        }
    }
}

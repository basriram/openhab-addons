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
package org.openhab.binding.ademco.internal.config;

/**
 * The {@link ZoneThingConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author WeeMin Chan - Initial contribution
 */

public class ZoneThingConfiguration {

    /**
     * The Zone Number. Can be in the range of 1-64. This is a required parameter for a zone.
     */

    public final static String ZONE_NUMBER = "zoneNumber";

    public Integer zoneNumber;
}

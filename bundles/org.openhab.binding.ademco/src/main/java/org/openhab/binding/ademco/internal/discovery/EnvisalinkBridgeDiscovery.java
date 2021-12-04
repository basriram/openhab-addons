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
package org.openhab.binding.ademco.internal.discovery;

import static org.openhab.binding.ademco.internal.AdemcoConstants.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.openhab.binding.ademco.internal.config.EnvisalinkBridgeConfiguration;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for discovering the EyezOn Envisalink 3/2DS Ethernet interface.
 * This is ported from Russell Stephens's Envisalink Bridge Discovery
 *
 * @author Wee-Min Chan - Initial Contribution
 *
 */
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.ademco")
public class EnvisalinkBridgeDiscovery extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(EnvisalinkBridgeDiscovery.class);
    private NetworkAddressService networkAddressService;
    private SubnetInfo subnetInfo = null;
    static final int CONNECT_TIMEOUT_IN_MS = 50;
    static final int ENVISALINK_BRIDGE_PORT = 4025;
    static final int CONNECTION_TIMEOUT = 10;
    static final int SO_TIMEOUT = 15000;
    static int timeout = SO_TIMEOUT;
    static final String ENVISALINK_DISCOVERY_RESPONSE = LOGIN;
    static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<ThingTypeUID>();
    static {
        SUPPORTED_THING_TYPES_UIDS.add(ENVISALINKBRIDGE_THING_TYPE);
    }

    private boolean scanRunning = false;

    /**
     * Constructor.
     */
    public EnvisalinkBridgeDiscovery() {
        super(SUPPORTED_THING_TYPES_UIDS, EnvisalinkBridgeDiscovery.timeout, false);
        logger.debug("EnvisalinkBridgeDiscovery instantiated!");
    }

    /**
     * Returns the amount of time in seconds after which the discovery service automatically
     * stops its forced discovery process.
     *
     * @return the discovery timeout in seconds (>= 0).
     */
    @Override
    public int getScanTimeout() {
        return EnvisalinkBridgeDiscovery.timeout;
    }

    @Reference
    protected void setNetworkAddressService(NetworkAddressService networkAddressService) {
        this.networkAddressService = networkAddressService;
        getSubnetInfo();
    }

    protected void unsetNetworkAddressService(NetworkAddressService networkAddressService) {
        this.networkAddressService = null;
        this.subnetInfo = null;
    }

    /**
     * This method will try to discover the subnet info
     *
     */
    void getSubnetInfo() {
        String ipAddress;
        String broadCastmask;
        SubnetUtils subnetUtils;
        logger.debug("Starting getSubnetInfo.");
        if (networkAddressService != null) {
            ipAddress = networkAddressService.getPrimaryIpv4HostAddress();
            broadCastmask = networkAddressService.getConfiguredBroadcastAddress();

        } else {
            logger.warn(
                    "Subnet info is not filled up. Please fill up network info from Configuration->System->Network Settings.");
            return;
        }

        if (ipAddress != null) {
            Integer cidrLength;
            if (broadCastmask != null) {
                cidrLength = broadCastMaskToCIDRLength(broadCastmask);
                subnetUtils = new SubnetUtils(ipAddress + "/" + cidrLength.toString());
                subnetUtils.setInclusiveHostCount(true);
            } else {
                return;
            }
            // subnetUtils.setInclusiveHostCount(true);
            subnetInfo = subnetUtils.getInfo();

        }
        if (subnetInfo == null) {
            logger.warn("No ip configured");
            return;
        } else {
            EnvisalinkBridgeDiscovery.timeout = (int) Math
                    .round((subnetInfo.getAddressCount() * CONNECT_TIMEOUT_IN_MS) / 1000.0);
            logger.debug("We have {} of IP to scan through", subnetInfo.getAddressCount());
            logger.debug("Host IP: {}", networkAddressService.getPrimaryIpv4HostAddress());
            logger.debug("Broadcast Address {}", networkAddressService.getConfiguredBroadcastAddress());
            logger.debug("Low IP {}", subnetInfo.getLowAddress());
            logger.debug("High IP {}", subnetInfo.getHighAddress());
        }
    }

    /**
     * Easy broadCastMask to CIDR length
     *
     * @param broadCastmask
     * @return CIDR length
     */
    private Integer broadCastMaskToCIDRLength(String broadCastmask) {
        String[] masks = broadCastmask.split("\\.");
        Integer initial = 32;
        if (masks.length == 4) {
            for (Integer i = 0; i < 4; i++) {
                if (masks[3 - i].equals("255")) {
                    initial -= 8;
                }
            }
        }
        return initial;
    }

    /**
     * Method for Bridge Discovery.
     */
    public synchronized void discoverBridge() {
        if (subnetInfo == null) {
            logger.warn(
                    "Subnet info is not filled up. Please fill up network info from Configuration->System->Network Settings.");
            return;
        }
        if (this.scanRunning) {
            logger.warn("Scan is already in progress not starting another");
        } else {
            logger.debug("Starting Scan");
            this.scanRunning = true;
        }
        for (String eachIpAddress : subnetInfo.getAllAddresses()) {
            if (this.scanRunning == false) {
                break;
            }
            try (Socket socket = new Socket()) {
                socket.setReuseAddress(true);
                socket.setReceiveBufferSize(32);
                socket.connect(new InetSocketAddress(eachIpAddress, ENVISALINK_BRIDGE_PORT), CONNECTION_TIMEOUT);
                if (socket.isConnected()) {
                    String message = "";
                    socket.setSoTimeout(SO_TIMEOUT);
                    try (BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        message = input.readLine();
                    } catch (SocketTimeoutException e) {
                        logger.debug("discoverBridge(): No Message Read from Socket at [{}] - {}", eachIpAddress,
                                e.getMessage());
                        continue;
                    } catch (Exception e) {
                        logger.debug("discoverBridge(): Exception Reading from Socket at [{}]! {}", eachIpAddress,
                                e.toString());
                        continue;
                    }

                    if (message.equals(ENVISALINK_DISCOVERY_RESPONSE)) {
                        logger.debug("discoverBridge(): Bridge Found - [{}]! Message - '{}'", eachIpAddress, message);
                        socket.close();
                        this.addEnvisalinkBridge(eachIpAddress);
                    } else {
                        logger.debug("discoverBridge(): No Response from Connection - [{}]! Message - '{}'",
                                eachIpAddress, message);
                    }
                }
            } catch (IllegalArgumentException e) {
                logger.debug("discoverBridge(): Illegal Argument Exception - {}", e.toString());
            } catch (SocketTimeoutException e) {
                logger.trace("discoverBridge(): No Connection on Port 4025! [{}]", eachIpAddress);
            } catch (SocketException e) {
                logger.debug("discoverBridge(): Socket Exception! [{}] - {}", eachIpAddress, e.toString());
            } catch (IOException e) {
                logger.debug("discoverBridge(): IO Exception! [{}] - {}", eachIpAddress, e.toString());
            } catch (Exception e) {
                logger.debug("discoverBridge(): Unknown exception caught: {}", e.toString());
            }
        }
        logger.debug("Ended discoverBridge");
        this.scanRunning = false;
    }

    @Override
    protected void startScan() {
        scheduler.execute(envisalinkBridgeDiscoveryRunnable);
    }

    private Runnable envisalinkBridgeDiscoveryRunnable = () -> {
        this.discoverBridge();
        logger.debug("Ended envisalinkBridgeDiscover");
    };

    /**
     * Method to add an Envisalink Bridge to the Smarthome Inbox.
     *
     * @param ipAddress
     */
    public void addEnvisalinkBridge(String ipAddress) {
        logger.trace("addBridge(): Adding new Envisalink Bridge on {} to Smarthome inbox", ipAddress);

        String bridgeID = ipAddress.replace('.', '_');
        Map<String, Object> properties = new HashMap<>(0);
        properties.put(EnvisalinkBridgeConfiguration.IP_ADDRESS, ipAddress);

        try {
            ThingUID thingUID = new ThingUID(ENVISALINKBRIDGE_THING_TYPE, bridgeID);

            thingDiscovered(DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                    .withLabel("EyezOn Envisalink Bridge - " + ipAddress).build());

            logger.trace("addBridge(): '{}' was added to Smarthome inbox.", thingUID);
        } catch (Exception e) {
            logger.error("addBridge(): Error:", e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.config.discovery.AbstractDiscoveryService#abortScan()
     */
    @Override
    public synchronized void abortScan() {
        this.scanRunning = false;
        super.abortScan();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.config.discovery.AbstractDiscoveryService#stopScan()
     */
    @Override
    protected synchronized void stopScan() {
        logger.debug("Stop Scanning");
        this.scanRunning = false;
        super.stopScan();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.config.discovery.AbstractDiscoveryService#startBackgroundDiscovery()
     */
    @Override
    protected synchronized void startBackgroundDiscovery() {
        this.startScan();
        super.startBackgroundDiscovery();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.config.discovery.AbstractDiscoveryService#stopBackgroundDiscovery()
     */
    @Override
    protected synchronized void stopBackgroundDiscovery() {
        this.stopScan();
        super.stopBackgroundDiscovery();
    }
}

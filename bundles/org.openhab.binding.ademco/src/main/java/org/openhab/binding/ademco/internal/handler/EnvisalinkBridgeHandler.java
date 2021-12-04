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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.ademco.internal.config.EnvisalinkBridgeConfiguration;
import org.openhab.binding.ademco.internal.discovery.AdemcoDiscoveryService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnvisalinkBridgeHandler} class extends BaseBridgeHandler
 * The bridge handler for the EyezOn Envisalink Ethernet interface.
 * This is modeled after Russell Stephens' dscalarm binding.
 * The logic is implemented based off the EnvisalinkTPI-ADEMCO-1-03.pdf specs.
 *
 * @author WeeMin Chan - Initial Contribution
 */
public class EnvisalinkBridgeHandler extends BaseBridgeHandler {

    private Logger logger = LoggerFactory.getLogger(EnvisalinkBridgeHandler.class);
    private EnvisalinkBridgeConfiguration config;

    // Partition stuff
    private PartitionBridgeHandler currentPartitionHandler;
    private short currentPartitionSelected = DEFAULT_PARTITION;
    private Map<Short, PartitionBridgeHandler> configuredPartitionHandler;

    private Boolean connected;

    private Socket tcpSocket = null;
    private PrintWriter tcpOutput = null;
    private BufferedReader tcpInput = null;
    private EnvisalinkState bridgeState = EnvisalinkState.INIT;

    private Integer receivedCommand;
    private long lastSeen;

    private ScheduledFuture<?> pollingJob = null;

    Thread tcpListenerThread;
    private AdemcoDiscoveryService ademcoDiscoveryService;

    /**
     * Constructor
     *
     * @param bridge
     */
    public EnvisalinkBridgeHandler(Bridge bridge) {
        super(bridge);
        configuredPartitionHandler = new HashMap<Short, PartitionBridgeHandler>();
        config = getConfigAs(EnvisalinkBridgeConfiguration.class);
        connected = false;
    }

    /**
     * Method to read from Envisalink bridge.
     *
     * @return String Message from the bridge.
     * @throws IOException Thrown if there's any issues.
     */
    private String read() throws IOException {
        String message = "";
        message = tcpInput.readLine();
        logger.debug("read(): \"{}\"", message);
        lastSeen = System.currentTimeMillis();
        return message;
    }

    /**
     * Method write to the Envisalink bridge.
     *
     * @param writeString String to write to.
     * @throws IOException
     */
    private void write(String writeString) throws IOException {
        tcpOutput.println(writeString);
        if (tcpOutput.checkError()) {
            logger.warn("Bridge closing connection!");
            throw new IOException(BRIDGE_CLOSE);
        }
        logger.debug("write(): \"{}\"", writeString);
    }

    /**
     * Class to implement a runnable task that will listen on the TCP socket.
     *
     * @author WeeMin Chan
     *
     */
    private class tcpListener implements Runnable {
        @Override
        public void run() {
            String messageLine;
            connected = true;
            while (connected) {
                try {
                    if ((messageLine = read()) != null) {
                        parseCommand(messageLine);
                    } else {
                        logger.warn("Got null from read");
                        closeConnection(true);
                    }
                } catch (IOException e) {
                    logger.info("Error while reading from Envisalink");
                    closeConnection(true);
                } catch (Exception e) {
                    logger.warn("Unknown exception occur but we will reconnect.");
                    closeConnection(true);
                }
            }
        }
    }

    /**
     * Method to wait for acknowledgement from EVL Bridge. Needed to synchronize the communication.
     *
     */
    private synchronized void waitForACK() {
        logger.debug("Wait For ACK");
        try {
            wait(config.connectionTimeout);
        } catch (InterruptedException e) {
            logger.warn("Error waiting for ACK");
        }
    }

    /**
     * Method to signal it's done.
     */
    private synchronized void commandACK() {
        logger.debug("Send ACK");
        notify();
    }

    /**
     * Method to check current state matches.
     *
     * @param stateToValidate state to check for
     * @return true if it matches.
     */
    private boolean isCurrentState(EnvisalinkState stateToValidate) {
        return (bridgeState == stateToValidate);
    }

    /**
     * Method to set the state of the bridge.
     *
     * @param newState new state to set to.
     */
    private void nextState(EnvisalinkState newState) {
        bridgeState = newState;
    }

    /**
     * Method to parse the command from the Envisalink Bridge.
     *
     * @param rawDataBytes Data from Bridge
     * @return true if this is a valid command.
     * @throws IOException if there's communication error.
     */
    private boolean parseCommand(String rawDataBytes) throws IOException {
        boolean isValidCommand = false;
        if (rawDataBytes.startsWith("%") && rawDataBytes.endsWith("$")) {
            isValidCommand = true;
            String dataBytes = rawDataBytes.substring(4, rawDataBytes.length() - 1);
            receivedCommand = Integer.parseInt(rawDataBytes.substring(1, 3));
            logger.debug("Command {} received", receivedCommand);
            switch (receivedCommand) {
                case COMMAND_KEYPADUPDATE:
                    parseVirtualKeypadUpdate(dataBytes);
                    break;
                case COMMAND_ZONESATECHANGE:
                    if (this.currentPartitionHandler != null) {
                        this.currentPartitionHandler.parseZoneStateChange(dataBytes);
                    } else {
                        logger.warn("currentPartitionHandler is not configured");
                    }
                    break;
                case COMMAND_PARTITIONSTATECHANGE:
                    parsePartitionStateChange(dataBytes);
                    break;
                case COMMAND_REALTIMECID:
                    parseRealtimeCIDEvent(dataBytes);
                    break;
                case COMMAND_ZONETIMERDUMP:
                    if (this.currentPartitionHandler != null) {
                        this.currentPartitionHandler.parseZoneTimerDump(dataBytes);
                    } else {
                        logger.warn("currentPartitionHandler is not configured");
                    }
                    // Since this only will come from an input command, we'll acknowledge we receive.
                    if (isCurrentState(EnvisalinkState.COMMAND_SEND)) {
                        nextState(EnvisalinkState.LOGIN);
                        commandACK();
                    } else if (isCurrentState(EnvisalinkState.GETPASSWORD)) {
                        logger.debug("ZoneTimerDump Loging received");
                    } else {
                        logger.warn(
                                "We received a ZONETIMERDUMP response from the bridge but we did not send the command.");
                    }
                    break;
                default:
                    isValidCommand = false;
                    logger.warn("Operation not supported");
            }
            // A response from a previous input
        } else if (rawDataBytes.startsWith("^") && rawDataBytes.endsWith("$")) {
            if (isCurrentState(EnvisalinkState.COMMAND_SEND)) {
                // Response to command send
                String command = rawDataBytes.substring(1, 3);
                String status = rawDataBytes.substring(4, 6);
                logger.debug("Command: {}, status: {}", command, status);
                if (!command.equals("02")) {
                    nextState(EnvisalinkState.LOGIN);
                    commandACK();
                }
            } else {
                logger.warn("We received a command response from the bridge but we did not send the command.");
            }
        } else {
            switch (rawDataBytes) {
                case LOGIN:
                    // if (isCurrentState(EnvisalinkState.INIT)) {
                    // } else {
                    // }
                    nextState(EnvisalinkState.GETPASSWORD);
                    write(config.password);
                    break;
                case LOGIN_OK:
                    if (isCurrentState(EnvisalinkState.GETPASSWORD)) {
                        nextState(EnvisalinkState.LOGIN);
                    }
                    commandACK();
                    break;
                case LOGIN_TIMEOUT:
                case LOGIN_FAILED:
                    logger.warn("Login encountered issues");
                    nextState(EnvisalinkState.INIT);
                    commandACK();
                    break;
                default:
                    logger.warn("Unhandled message {}", rawDataBytes);
                    nextState(EnvisalinkState.INIT);
                    commandACK();
            }
        }
        return isValidCommand;
    }

    /**
     * Method to parse CIDEvent command from the bridge.
     *
     * @param data
     */
    private void parseRealtimeCIDEvent(String data) {
        byte Qualifier = Byte.parseByte(data.substring(0, 1));
        short CID = Short.parseShort(data.substring(1, 4));
        byte Partition = Byte.parseByte(data.substring(4, 6));
        short User = Short.parseShort(data.substring(6, 9));
        String output = String.format("Qualifier = %d, CID = %d, Partition = %d, User = %d", Qualifier, CID, Partition,
                User);
        logger.debug(output);
    }

    /**
     * Method to parse PartitionStateChange command from the bridge.
     *
     * @param data
     */
    private void parsePartitionStateChange(String data) {
        logger.debug("parsePartitionStateChange {}", data);
        for (Short currentPartition = 0; currentPartition < PARTITIONSIZE; currentPartition++) {
            int index = currentPartition * 2;
            Short newStatus = (short) Integer.parseInt(data.substring(index, index + 2), 16);
            if (newStatus > 0) {
                PartitionBridgeHandler targetPartitionHandler = this.configuredPartitionHandler
                        .get((short) (currentPartition + 1));
                if (targetPartitionHandler != null) {
                    targetPartitionHandler.updatePartitionStatus(newStatus);
                }
            }
        }
    }

    /**
     * Method to parse Virtual Keypad Update command from the bridge.
     *
     * @param data
     */
    private void parseVirtualKeypadUpdate(String data) {
        logger.debug("got input {}", data);
        Short partitionNumber = Short.parseShort(data.substring(0, 2));
        if (configuredPartitionHandler.containsKey(partitionNumber)) {
            if (this.currentPartitionHandler != null) {
                this.currentPartitionHandler.parseVirtualKeypadUpdate(data);
            }
        } else {
            this.ademcoDiscoveryService.addParitionThing(partitionNumber, this.getThing());
            logger.info("Partition {} read from EVL but unconfigured", partitionNumber);
        }
    }

    /**
     * Start polling with an initial delay
     *
     * @param initialPeriod time to start in minutes
     */
    private void startReconnectTimer() {
        if (pollingJob != null) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                logger.debug("Trying to connect to Envisalink @{}:{}", config.ipAddress, config.port);

                initialize();
            }
        };

        pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, this.config.pollPeriod, this.config.pollPeriod,
                TimeUnit.MINUTES);
    }

    /**
     * Method to check connection of the bridge.
     */
    private void startCheckPulseTimer() {
        if (pollingJob != null) {
            pollingJob.cancel(true);
            pollingJob = null;
        }

        Runnable pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkConnection();
            }
        };
        pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, this.config.pollPeriod, this.config.pollPeriod,
                TimeUnit.MINUTES);
    }

    /**
     * Check if bridge is still connected
     *
     * @return true if it is.
     */
    boolean isConnected() {
        return this.connected;
    }

    @Override
    public void initialize() {
        config = getConfigAs(EnvisalinkBridgeConfiguration.class);
        try {
            tcpSocket = new Socket(config.ipAddress, config.port);
            logger.debug("Socket accepted");
            tcpOutput = new PrintWriter(tcpSocket.getOutputStream(), true);
            tcpInput = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            tcpListenerThread = new Thread(new tcpListener());
            tcpListenerThread.start();
            waitForACK();
            if (isCurrentState(EnvisalinkState.LOGIN)) {
                logger.debug("Connected Succesfully");
                startCheckPulseTimer();
                updateStatus(ThingStatus.ONLINE);
            } else {
                logger.error("Password is wrong! Please check configuration.");
                closeConnection(false);
            }
        } catch (IOException e) {
            logger.warn("Error connecting to Envisalink at {}:{}, please check configuration and try again!",
                    config.ipAddress, config.port);
            closeConnection(true);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.thing.binding.BaseThingHandler#dispose()
     * Close the connection.
     */
    @Override
    public void dispose() {
        if (!this.configuredPartitionHandler.isEmpty()) {
            for (PartitionBridgeHandler eachPartitionHandler : this.configuredPartitionHandler.values()) {
                eachPartitionHandler.dispose();
            }
            this.configuredPartitionHandler.clear();
            this.currentPartitionHandler = null;
        } else {
            logger.warn("No configured paritionhandler!");
        }
        closeConnection(false);
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub
    }

    /**
     * Method to cause Envisalink Bridge to Dump it's internal ZoneTimer.
     *
     * @throws InterruptedException
     * @throws IOException
     */
    public void DumpZoneTimers() throws InterruptedException, IOException {
        sendCommand("^02,$");
    }

    /**
     * Method to send poll command to the bridge.
     *
     * @throws InterruptedException
     * @throws IOException
     */
    public void Poll() throws InterruptedException, IOException {
        sendCommand("^00,$");
    }

    /**
     * Method to close the connection on the EVL bridge.
     *
     * @param reconnect true if this is called due to a fault (unexpected). This will start a reconnect timer. Otherwise
     *            close forever.
     */
    public void closeConnection(boolean reconnect) {
        try {
            if (connected) {
                connected = false;
                if (tcpSocket != null) {
                    logger.debug("closeConnection(): Closing Socket!");
                    tcpSocket.close();
                    tcpSocket = null;
                }
                if (tcpInput != null) {
                    logger.debug("closeConnection(): Closing Output Writer!");
                    tcpInput.close();
                    tcpInput = null;
                }
                if (tcpOutput != null) {
                    logger.debug("closeConnection(): Closing Input Reader!");
                    tcpOutput.close();
                    tcpOutput = null;
                }
                // Just for safety we wait for thread to end;
                this.tcpListenerThread.join(config.connectionTimeout);
                logger.debug("closeConnection(): Closed TCP Connection!");
            }
            if (reconnect) {
                logger.info("Will reconnect in {} minutes.", config.pollPeriod);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE,
                        "Bridge lost connection check logs, attemping to reconnect.");
                this.startReconnectTimer();
            } else {
                if (pollingJob != null) {
                    pollingJob.cancel(true);
                    pollingJob = null;
                }
            }
            nextState(EnvisalinkState.INIT);
        } catch (IOException ioException) {
            logger.error("closeConnection(): Unable to close connection - {}", ioException.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            nextState(EnvisalinkState.INIT);

        } catch (Exception exception) {
            logger.error("closeConnection(): Error closing connection - {}", exception.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            nextState(EnvisalinkState.INIT);

        }
    }

    /**
     * This method is used to check if there's still connection with the server.
     *
     */
    public void checkConnection() {
        if ((System.currentTimeMillis() - this.lastSeen) > config.pollPeriod * 60000) {
            logger.warn("No pulse seen for the last {} minutes", config.pollPeriod);
        }
        try {
            this.Poll();
        } catch (InterruptedException | IOException e) {
            logger.warn("Error communicating with Envisalink");
            closeConnection(true);
        }
    }

    /**
     * Method to send command to the bridge.
     *
     * @param cmd command to send.
     * @throws IOException if errors occur.
     */
    void sendCommand(String cmd) throws IOException {
        boolean isCommand = false;
        Matcher _match = command_input_regex.matcher(cmd);
        if (cmd.matches(non_command_input_regex) || (isCommand = _match.matches())) {
            if (isCommand) {
                nextState(EnvisalinkState.COMMAND_SEND);
            }
            write(cmd);
            waitForACK();
        } else {
            logger.warn("Input {} not format correctly", cmd);
        }
    }

    // /*
    // * (non-Javadoc)
    // *
    // * @see
    // * org.openhab.core.thing.binding.BaseBridgeHandler#childHandlerInitialized(org.openhab.core.
    // * thing.binding.ThingHandler, org.openhab.core.thing.Thing)
    // * Override method to initialize the partition if it's configured.
    // */
    // @Override
    // public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
    // if (PARTITION_THING_TYPE.equals(childThing.getThingTypeUID())) {
    // PartitionBridgeHandler childPartitionHandler = (PartitionBridgeHandler) childHandler;
    // int partitionnumber = childPartitionHandler.getPartitionNumber();
    // logger.debug("***** adding partition {} *****", partitionnumber);
    //
    // if (partitionnumber == this.currentPartitionSelected) {
    // logger.debug("Assigning {} as current partition", partitionnumber);
    // this.currentPartitionHandler = childPartitionHandler;
    // }
    // // Add it to a Map so we can look it up later
    // configuredPartitionHandler.put((short) partitionnumber, childPartitionHandler);
    // } else {
    // logger.warn("Thing not allowed {}", childThing);
    // }
    // super.childHandlerInitialized(childHandler, childThing);
    // }
    //
    // /*
    // * (non-Javadoc)
    // *
    // * @see
    // *
    // org.openhab.core.thing.binding.BaseBridgeHandler#childHandlerDisposed(org.openhab.core.thing.
    // * binding.ThingHandler, org.openhab.core.thing.Thing)
    // */
    // @Override
    // public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
    // if (PARTITION_THING_TYPE.equals(childThing.getThingTypeUID())) {
    // PartitionBridgeHandler childPartitionHandler = (PartitionBridgeHandler) childHandler;
    // short partitionnumber = childPartitionHandler.getPartitionNumber();
    // logger.debug("***** removing partition {} *****", partitionnumber);
    // if (this.configuredPartitionHandler.containsKey(partitionnumber)) {
    // configuredPartitionHandler.remove(partitionnumber, childPartitionHandler);
    // }
    // }
    //
    // super.childHandlerDisposed(childHandler, childThing);
    // }

    /*
     * (non-Javadoc)
     *
     * @see org.openhab.core.thing.binding.BaseThingHandler#handleConfigurationUpdate(java.util.Map)
     */
    @Override
    public void handleConfigurationUpdate(Map<@NonNull String, @NonNull Object> configurationParameters) {
        // ReRead and re-init.
        logger.debug("handleConfigurationUpdate called {}", configurationParameters.toString());
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
        logger.debug("thingUpdated called");
        config = getConfigAs(EnvisalinkBridgeConfiguration.class);
        // TODO Auto-generated method stub
        super.thingUpdated(thing);
    }

    /**
     * Register the Discovery Service.
     *
     * @param ademcoDiscoveryService2
     */
    public void registerDiscoveryService(AdemcoDiscoveryService ademcoDiscoveryService2) {
        if (ademcoDiscoveryService2 == null) {
            throw new IllegalArgumentException("registerDiscoveryService(): Illegal Argument. Not allowed to be Null!");
        } else {
            this.ademcoDiscoveryService = ademcoDiscoveryService2;
            logger.trace("registerDiscoveryService(): Discovery Service Registered!");
        }
    }

    /**
     * Unregister the Discovery Service.
     */
    public void unregisterDiscoveryService() {
        this.ademcoDiscoveryService = null;
        logger.trace("unregisterDiscoveryService(): Discovery Service Unregistered!");
    }

    /**
     * @return the envisalinkBridgeDiscoveryService
     */
    public AdemcoDiscoveryService getEnvisalinkBridgeDiscoveryService() {
        return ademcoDiscoveryService;
    }

    /**
     * Method to add a partitionHandler to the hashmap
     *
     * @param childPartitionHandler
     * @param partitionnumber
     */
    public void addPartitionHandler(PartitionBridgeHandler childPartitionHandler, int partitionnumber) {
        logger.debug("***** adding partition {} *****", partitionnumber);
        if (partitionnumber == this.currentPartitionSelected) {
            logger.debug("Assigning {} as current partition", partitionnumber);
            this.currentPartitionHandler = childPartitionHandler;
        }
        // Add it to a Map so we can look it up later
        configuredPartitionHandler.put((short) partitionnumber, childPartitionHandler);
    }

    /**
     * Method to remove a partitionHandler from the hashmap
     *
     * @param partitionNumber
     */
    public void removePartitionHandler(short partitionNumber) {
        if (this.configuredPartitionHandler.containsKey(partitionNumber)) {
            this.configuredPartitionHandler.remove(partitionNumber);
            if (partitionNumber == this.currentPartitionSelected) {
                this.currentPartitionHandler = null;
            }
        } else {
            logger.warn("Partition {} is not in the set", partitionNumber);
        }
    }
}

// Copyright (c) 2020, Wolfgang Sidler, Switzerland
// All rights reserved
// Based on a proprietary part of the SPE Expert serial protocol. The intellectual property of the initial matter remains with SPE srl.

package ch.fhnw.server.comPort;

import ch.fhnw.server.constants.ERROR_MESSAGES;
import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Class ComPortDriver to communicate with Expert device.
 */
@Service
public class ComPortExtendedDriver implements IComPortExtendedDriver {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    /**
     * The constant NEW_READ_TIMEOUT.
     */
    private static final int NEW_READ_TIMEOUT = 100;
    /**
     * The constant NEW_WRITE_TIMEOUT.
     */
    private static final int NEW_WRITE_TIMEOUT = 100;
    /**
     * The constant PARITY_NONE.
     */
    private static final int PARITY_NONE = 0;
    /**
     * The constant FLOWCONTROL_NONE.
     */
    public static final int FLOWCONTROL_NONE = 0;
    /**
     * The constant NEW_DATA_BITS.
     */
    public static final int NEW_DATA_BITS = 8;
    /**
     * The constant NEW_STOP_BITS.
     */
    public static final int NEW_STOP_BITS = 1;
    /**
     * The constant BAUDRATE.
     */
    public static final int BAUDRATE = 115200;

    private SerialPort serialPort;

    @Value("${serial.comPort}")
    private String comPort;


    /**
     * Configure serialPort and
     * Try to connect to Expert device
     *
     * @return true if setUp is successful
     */
    public boolean setUp() {

        if (comPort == null) {
            log.error(ERROR_MESSAGES.SERIAL_PORT_NOT_SET.getErrorMessage());
            return false;
        }

        try {
            serialPort = SerialPort.getCommPort(comPort);
            serialPort.setComPortParameters(BAUDRATE, NEW_DATA_BITS, NEW_STOP_BITS, PARITY_NONE);
            serialPort.setFlowControl(FLOWCONTROL_NONE);
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, NEW_READ_TIMEOUT, NEW_WRITE_TIMEOUT);

            // Set DTR to 1 and RTS to 0
            serialPort.clearRTS();
            serialPort.setDTR();

        } catch (Exception e) {
            log.error(ERROR_MESSAGES.INITIALIZATION_ERROR.getErrorMessage() + " " + e);
            return false;
        }
        return true;
    }

    /**
     * Write and read bytes from/to Expert
     *
     * @param command      the byte command for Expert device
     * @param responseSize the length of the answer we expect
     * @return the response array from Expert device
     */
    synchronized public byte[] writeReadToSerial(byte command, int responseSize) {
        byte[] request = {0x55, 0x55, 0x55, 0x01, command, command};
        byte[] response = new byte[responseSize];

        try {
            if (serialPort.openPort()) {
                serialPort.writeBytes(request, request.length);
                serialPort.readBytes(response, response.length);
                return response;
            } else {
                log.error(ERROR_MESSAGES.COMPORT_NOT_CONNECTED.getErrorMessage());
                return null;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        } finally {
            serialPort.closePort();
        }
    }

    /**
     * Extended Function to power on Expert
     * function is not Open Source
     */
    // Based on http://www.spetlc.com/images/download/1.3K-FA/User.Manual.EXPERT.1.3K-FA-EN-rev.2.1.Second.Series.pdf, page 79
    public void powerOnExpert() {
        try {
            for (int i = 0; i < 3; i++) {
                // Set DTR to 0 and RTS to 1
                serialPort.clearDTR();
                serialPort.setRTS();

                //Sleep 1 Second
                TimeUnit.SECONDS.sleep(1);

                // Set DTR to 1 and RTS to 0
                serialPort.clearRTS();
                serialPort.setDTR();
            }

        } catch (InterruptedException e) {
            log.warn(e.getMessage());
        }
    }
}

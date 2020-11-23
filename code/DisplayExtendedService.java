// Copyright (c) 2020, Wolfgang Sidler, Switzerland
// All rights reserved
// Based on a proprietary part of the SPE Expert serial protocol. The intellectual property of the initial matter remains with SPE srl.

package ch.fhnw.server.service;

import ch.fhnw.server.comPort.IComPortDriver;
import ch.fhnw.server.constants.ERROR_MESSAGES;
import ch.fhnw.server.response.DisplayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;

/**
 * Class Display service.
 */
@Service
public class DisplayExtendedService implements IDisplayExtendedService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());


    private IComPortDriver comPortDriver;

    private String oldStringDisplay;
    private String newStringDisplay;
    private DisplayResponse displayResponse;
    private HashMap<Byte, String> displayMap;


    /**
     * Instantiates a new Display service.
     *
     */
    public DisplayExtendedService() {
        oldStringDisplay = " ";
        newStringDisplay = " ";
        displayResponse = new DisplayResponse();
        initializeDisplayMap();
    }

    @Override
    public Mono<DisplayResponse> getData() {
        return Mono.just(displayResponse);
    }

    @Override
    public void updateData() {
        byte command = (byte) 0x80;
        byte[] display = comPortDriver.writeReadToSerial(command, 371);

        if (display == null) {
            newStringDisplay = ERROR_MESSAGES.COMPORT_NOT_CONNECTED.getErrorMessage();
            log.error(ERROR_MESSAGES.COMPORT_NOT_CONNECTED.getErrorMessage());
        } else {
            newStringDisplay = decodeDisplay(display);
        }

        if (!oldStringDisplay.equals(newStringDisplay)) {
            oldStringDisplay = newStringDisplay;
            displayResponse = convertToDisplayObject();
            SocketService.sendDisplay(displayResponse);
        }
    }

    // Based on https://github.com/sfera-labs/sfera-driver-speexpert/blob/master/src/main/java/cc/sferalabs/sfera/drivers/speexpert/SpeExpert.java licensed under GNU LESSER GENERAL PUBLIC LICENSE
    private String decodeDisplay(byte[] data) {
        StringBuilder stringBuilder = new StringBuilder();

        int index = 0;
        String prefix = ",,";

        try {

            if (data[0] != (byte) 0xaa || data[1] != (byte) 0xaa || data[2] != (byte) 0xaa) {
                return ERROR_MESSAGES.COMMUNICATION_ERROR.getErrorMessage();
            }

            /**
             *
             * The bit 7 of the led byte is the status of the Alarm led (1=off, 0=on)
             * The bit 6 of the led byte is the status of Tune led (1=off,0=on)
             * The bit 5 of the led byte is the status of the Set led (1=off,0=on)
             * The bit 4 of the led byte is the status of the Op led (1=off,0=on)
             * The bit 3 of the led byte is the status of the Tx led (1=off,0=on)
             * The bit 2 of the led byte is the status of the On led (1=off,0=on)
             * If device is offline every bit is 0
             *
             */
            String ledBitString = String.format("%8s", Integer.toBinaryString(data[8] & 0xFF))
                    .replace(' ', '0');
            stringBuilder.append(ledBitString);
            stringBuilder.append(prefix);

            /**
             *
             * The first 320 bytes of the payload are the display content (8 rows x 40 columns).
             *
             */
            for (int i = 8; i < 329; i++) {
                stringBuilder.append(displayMap.getOrDefault(data[i], " "));

                if ((index % 40) == 0 && index > 0) {
                    stringBuilder.append(prefix);
                }

                index++;
            }

            /**
             *
             * The remaining 40 bytes of the payload are the "attributes" of the 320
             * characters. If the attribute bit is set to 1, the character is printed in reverse mode.
             * There is one attribute byte for each column,the first bit of the
             * attribute byte is related to the character of the first row, the
             * second bit is related to the character on the second row and so on.
             *
             */
            for (int i = 329; i < 369; i++) {
                stringBuilder.append(data[i]);
                stringBuilder.append(";");
            }

            return stringBuilder.toString();

        } catch (Exception e) {
            return ERROR_MESSAGES.DECODING_ERROR.getErrorMessage();
        }
    }

    private DisplayResponse convertToDisplayObject() {

        if (oldStringDisplay.length() <= 1) {
            return displayResponse;
        }

        try {
            String[] s = oldStringDisplay.split(",,");
            if (!"Error".equals(s[0]) && s.length >= 10) {

                char[] led = new char[8];

                for (int i = 0; i < 8; i++) {
                    led[i] = s[0].charAt(i);
                }

                displayResponse.setLed(led);

                char[] line1 = new char[40];
                char[] line2 = new char[40];
                char[] line3 = new char[40];
                char[] line4 = new char[40];
                char[] line5 = new char[40];
                char[] line6 = new char[40];
                char[] line7 = new char[40];
                char[] line8 = new char[40];

                for (int j = 0; j < 40; j++) {
                    line1[j] = s[1].charAt(j);
                    line2[j] = s[2].charAt(j);
                    line3[j] = s[3].charAt(j);
                    line4[j] = s[4].charAt(j);
                    line5[j] = s[5].charAt(j);
                    line6[j] = s[6].charAt(j);
                    line7[j] = s[7].charAt(j);
                    line8[j] = s[8].charAt(j);

                }

                displayResponse.setLine1(line1);
                displayResponse.setLine2(line2);
                displayResponse.setLine3(line3);
                displayResponse.setLine4(line4);
                displayResponse.setLine5(line5);
                displayResponse.setLine6(line6);
                displayResponse.setLine7(line7);
                displayResponse.setLine8(line8);


                displayResponse.setSelected(s[9].split(";"));

            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return displayResponse;
    }

    private void initializeDisplayMap() {
        displayMap = new HashMap<>();
        displayMap.put((byte) 0x00, " ");
        displayMap.put((byte) 0x01, "!");
        displayMap.put((byte) 0x02, "\"");
        displayMap.put((byte) 0x03, "#");
        displayMap.put((byte) 0x04, "$");
        displayMap.put((byte) 0x05, "%");
        displayMap.put((byte) 0x06, "&");
        displayMap.put((byte) 0x07, "\\");
        displayMap.put((byte) 0x08, "(");
        displayMap.put((byte) 0x09, ")");
        displayMap.put((byte) 0x0a, "*");
        displayMap.put((byte) 0x0b, "+");
        displayMap.put((byte) 0x0c, ",");
        displayMap.put((byte) 0x0d, "-");
        displayMap.put((byte) 0x0e, ".");
        displayMap.put((byte) 0x0f, "/");
        displayMap.put((byte) 0x10, "0");
        displayMap.put((byte) 0x11, "1");
        displayMap.put((byte) 0x12, "2");
        displayMap.put((byte) 0x13, "3");
        displayMap.put((byte) 0x14, "4");
        displayMap.put((byte) 0x15, "5");
        displayMap.put((byte) 0x16, "6");
        displayMap.put((byte) 0x17, "7");
        displayMap.put((byte) 0x18, "8");
        displayMap.put((byte) 0x19, "9");
        displayMap.put((byte) 0x1a, ":");
        displayMap.put((byte) 0x1b, ";");
        displayMap.put((byte) 0x1c, "<");
        displayMap.put((byte) 0x1d, "=");
        displayMap.put((byte) 0x1e, ">");
        displayMap.put((byte) 0x1f, "?");
        displayMap.put((byte) 0x20, "@");
        displayMap.put((byte) 0x21, "A");
        displayMap.put((byte) 0x22, "B");
        displayMap.put((byte) 0x23, "C");
        displayMap.put((byte) 0x24, "D");
        displayMap.put((byte) 0x25, "E");
        displayMap.put((byte) 0x26, "F");
        displayMap.put((byte) 0x27, "G");
        displayMap.put((byte) 0x28, "H");
        displayMap.put((byte) 0x29, "I");
        displayMap.put((byte) 0x2a, "J");
        displayMap.put((byte) 0x2b, "K");
        displayMap.put((byte) 0x2c, "L");
        displayMap.put((byte) 0x2d, "M");
        displayMap.put((byte) 0x2e, "N");
        displayMap.put((byte) 0x2f, "O");
        displayMap.put((byte) 0x30, "P");
        displayMap.put((byte) 0x31, "Q");
        displayMap.put((byte) 0x32, "R");
        displayMap.put((byte) 0x33, "S");
        displayMap.put((byte) 0x34, "T");
        displayMap.put((byte) 0x35, "U");
        displayMap.put((byte) 0x36, "V");
        displayMap.put((byte) 0x37, "W");
        displayMap.put((byte) 0x38, "X");
        displayMap.put((byte) 0x39, "Y");
        displayMap.put((byte) 0x3a, "Z");
        displayMap.put((byte) 0x3b, "[");
        displayMap.put((byte) 0x3c, "\\");
        displayMap.put((byte) 0x3d, "]");
        displayMap.put((byte) 0x3e, "^");
        displayMap.put((byte) 0x3f, "_");
        displayMap.put((byte) 0x40, "`");
        displayMap.put((byte) 0x41, "a");
        displayMap.put((byte) 0x42, "b");
        displayMap.put((byte) 0x43, "c");
        displayMap.put((byte) 0x44, "d");
        displayMap.put((byte) 0x45, "e");
        displayMap.put((byte) 0x46, "f");
        displayMap.put((byte) 0x47, "g");
        displayMap.put((byte) 0x48, "h");
        displayMap.put((byte) 0x49, "i");
        displayMap.put((byte) 0x4a, "j");
        displayMap.put((byte) 0x4b, "k");
        displayMap.put((byte) 0x4c, "l");
        displayMap.put((byte) 0x4d, "m");
        displayMap.put((byte) 0x4e, "n");
        displayMap.put((byte) 0x4f, "o");
        displayMap.put((byte) 0x50, "p");
        displayMap.put((byte) 0x51, "q");
        displayMap.put((byte) 0x52, "r");
        displayMap.put((byte) 0x53, "s");
        displayMap.put((byte) 0x54, "t");
        displayMap.put((byte) 0x55, "u");
        displayMap.put((byte) 0x56, "v");
        displayMap.put((byte) 0x57, "w");
        displayMap.put((byte) 0x58, "x");
        displayMap.put((byte) 0x59, "y");
        displayMap.put((byte) 0x5a, "z");
        displayMap.put((byte) 0x5b, "{ ");
        displayMap.put((byte) 0x5c, "|");
        displayMap.put((byte) 0x5d, " ]");
        displayMap.put((byte) 0x5e, "~");
        displayMap.put((byte) 0x5f, "");
        displayMap.put((byte) 0x9d, "←");
        displayMap.put((byte) 0x9e, "→");
        displayMap.put((byte) 0x80, "μ");
        displayMap.put((byte) 0xa7, "∞");
        displayMap.put((byte) 0xa8, "∞");
        displayMap.put((byte) 0xa9, "∞");
        displayMap.put((byte) 0xaa, "°");
        displayMap.put((byte) -82, "✔");
        displayMap.put((byte) -103, "◄");
        displayMap.put((byte) -102, "▲");
        displayMap.put((byte) -101, "▼");
        displayMap.put((byte) -100, "►");
    }

    /**
     * Method to set the comPortDriver
     *
     * @param comPortDriver set the comPortDrive to communicate with expert
     */
    @Override
    public void setComPortDriver(IComPortDriver comPortDriver) {
        this.comPortDriver = comPortDriver;
    }
}

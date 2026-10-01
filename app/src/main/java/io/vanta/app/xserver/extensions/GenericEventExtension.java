package io.vanta.app.xserver.extensions;

import static io.vanta.app.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import io.vanta.app.xconnector.XInputStream;
import io.vanta.app.xconnector.XOutputStream;
import io.vanta.app.xconnector.XStreamLock;
import io.vanta.app.xserver.XClient;
import io.vanta.app.xserver.XServer;
import io.vanta.app.xserver.errors.BadImplementation;
import io.vanta.app.xserver.errors.XRequestError;

import java.io.IOException;

public class GenericEventExtension extends Extension {
    public static final byte MAJOR_VERSION = 1;
    public static final byte MINOR_VERSION = 0;

    public GenericEventExtension(XServer xServer, byte majorOpcode) {
        super(xServer, majorOpcode);
    }

    @Override
    public String getName() {
        return "Generic Event Extension";
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        if (opcode != 0) throw new BadImplementation();
        inputStream.skip(4);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeShort(MAJOR_VERSION);
            outputStream.writeShort(MINOR_VERSION);
            outputStream.writePad(20);
        }
    }
}

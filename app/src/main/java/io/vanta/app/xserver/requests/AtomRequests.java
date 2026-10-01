package io.vanta.app.xserver.requests;

import static io.vanta.app.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import io.vanta.app.xconnector.XInputStream;
import io.vanta.app.xconnector.XOutputStream;
import io.vanta.app.xconnector.XStreamLock;
import io.vanta.app.xserver.Atom;
import io.vanta.app.xserver.XClient;
import io.vanta.app.xserver.errors.BadAtom;
import io.vanta.app.xserver.errors.XRequestError;

import java.io.IOException;

public abstract class AtomRequests {
    public static void internAtom(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        boolean onlyIfExists = client.getRequestData() == 1;
        short length = inputStream.readShort();
        inputStream.skip(2);
        String name = inputStream.readString8(length);
        int id = onlyIfExists ? Atom.getId(name) : Atom.internAtom(name);
        if (id < 0) throw new BadAtom(id);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(id);
            outputStream.writePad(20);
        }
    }

    public static void getAtomName(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int id = inputStream.readInt();
        if (id < 0) throw new BadAtom(id);

        String name = Atom.getName(id);
        int length = name.length();

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt((length + (-length & 3)) / 4);
            outputStream.writeShort((short)length);
            outputStream.writePad(22);
            outputStream.writeString8(name);
        }
    }
}
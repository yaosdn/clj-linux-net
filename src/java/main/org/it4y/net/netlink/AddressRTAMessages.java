package org.it4y.net.netlink;

import org.it4y.jni.libnetlink;

import java.nio.ByteBuffer;

/**
 * Created by luc on 1/2/14.
 */
public class AddressRTAMessages extends RTAMessage {

    public AddressRTAMessages(int pos, ByteBuffer buffer) {
        super(pos, buffer);
    }

    @Override
    public String getRTAName() {
        return libnetlink.linux.if_address.RTA_NAMES[type];
    }
}
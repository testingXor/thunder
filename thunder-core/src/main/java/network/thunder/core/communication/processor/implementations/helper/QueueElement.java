package network.thunder.core.communication.processor.implementations.helper;

import network.thunder.core.communication.objects.lightning.subobjects.ChannelStatus;
import network.thunder.core.database.objects.Channel;

/**
 * Created by matsjerratsch on 07/01/2016.
 */
public interface QueueElement {
    ChannelStatus produceNewChannelStatus(Channel channel);
}
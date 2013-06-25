package de.uniluebeck.itm.spitfire.nCoap.communication;

import de.uniluebeck.itm.spitfire.nCoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.spitfire.nCoap.application.client.TestCoapResponseProcessor;
import de.uniluebeck.itm.spitfire.nCoap.application.endpoint.CoapTestEndpoint;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapMessage;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import java.net.URI;
import java.util.SortedMap;

import static org.junit.Assert.*;


/**
* Tests of CoAP request and response messages including reliability,
* piggy-backed and separate response.
*
* @author Oliver Kleine, Stefan Hüske
*/
public class ClientGetsNoAckForConRequestTest extends AbstractCoapCommunicationTest {

    private static final int TIMEOUT_MILLIS = 2000;
    private static Logger log = Logger.getLogger(ClientGetsNoAckForConRequestTest.class.getName());

    private static long timeRequestSent;
    private static CoapRequest request;

    private static CoapClientApplication client;
    private static TestCoapResponseProcessor responseProcessor;

    private static CoapTestEndpoint testEndpoint;

    @Override
    public void setupLogging() throws Exception {
        Logger.getLogger("de.uniluebeck.itm.spitfire.nCoap.communication.reliability").setLevel(Level.DEBUG);
        Logger.getLogger("de.uniluebeck.itm.spitfire.nCoap.application").setLevel(Level.DEBUG);
    }

    @Override
    public void setupComponents() throws Exception {
        testEndpoint = new CoapTestEndpoint();

        client = new CoapClientApplication();
        responseProcessor = new TestCoapResponseProcessor();
        URI targetUri = new URI("coap://localhost:" + testEndpoint.getPort() + "/testpath");
        request = new CoapRequest(MsgType.CON, Code.GET, targetUri);
    }

    @Override
    public void shutdownComponents() throws Exception {
        client.shutdown();
        testEndpoint.shutdown();
    }



    /**
     * Retransmission intervals (RC = Retransmission Counter):
     * immediately send CON message, set RC = 0
     * wait  2 - 3  sec then send retransmission, set RC = 1
     * wait  4 - 6  sec then send retransmission, set RC = 2
     * wait  8 - 12 sec then send retransmission, set RC = 3
     * wait 16 - 24 sec then send retransmission, set RC = 4
     * wait 32 - 48 sec then fail transmission
     *
     * @throws Exception
     */
    @Override
    public void createTestScenario() throws Exception {

//             client                        testEndpoint     DESCRIPTION
//                  |                             |
//              (1) |----CON-GET----------------->|           Client sends confirmable request
//                  |                             |
//              (2) |----1st RETRANSMISSION------>|           (Client should send four retransmissions)
//                  |                             |
//              (3) |----2nd RETRANSMISSION------>|
//                  |                             |
//              (4) |----3rd RETRANSMISSION------>|
//                  |                             |
//              (5) |----4th RETRANSMISSION------>|
//                  |                             |
//                  |                             |           internal timeout notification to response processor




        //Send request
        client.writeCoapRequest(request, responseProcessor);
        timeRequestSent = System.currentTimeMillis();

        //Maximum time to pass before last retransmission is 48 sec.
        //Wait another 6 sec. to let the last retransmission time out before test methods start
        Thread.sleep(54000);
    }



    @Test
    public void testNumberOfRequests(){
        int expected = 5;
        int actual = testEndpoint.getReceivedMessages().size();
        assertEquals("Endpoint received wrong number of requests!", expected, actual);
    }

    @Test
    public void testAllRequestsAreEqual(){
        SortedMap<Long, CoapMessage> receivedMessages = testEndpoint.getReceivedMessages();
        CoapMessage firstMessage = receivedMessages.get(receivedMessages.firstKey());

        for(CoapMessage message : receivedMessages.values()){
            assertEquals("Received requests did not equal.", firstMessage, message);
        }
    }

    /**
     * Resulting absolute intervals
     * 1st retransmission should be received after  2 - 3  sec
     * 2nd retransmission should be received after  6 - 9  sec
     * 3rd retransmission should be received after 14 - 21 sec
     * 4th retransmission should be received after 30 - 45 sec
     */
    @Test
    public void testRetransmissionsWereReceivedInTime(){
        //Get times of received messages
        SortedMap<Long, CoapMessage> receivedMessages = testEndpoint.getReceivedMessages();
        Object[] receptionTimes = receivedMessages.keySet().toArray();

        long minDelay = 0;
        long maxDelay = 0;
        for(int i = 1; i < receptionTimes.length; i++){
            minDelay += Math.pow(2, i-1) * TIMEOUT_MILLIS;
            maxDelay += Math.pow(2, i-1) * TIMEOUT_MILLIS * 1.5;
            long actualDelay = (Long) receptionTimes[i] - timeRequestSent;

            String message = "Retransmission " + i
                           + " (expected delay between " + minDelay + " and " + maxDelay + "ms,"
                           + " actual delay " + actualDelay + "ms).";

            log.info(message);
            assertTrue(message, minDelay <= actualDelay);
            assertTrue(message, maxDelay >= actualDelay);
        }
    }

   /**
    * Test if the notification was to early. Minimum delay is the sum of minimal timeouts (30000) plus the delay
    * for timeout notification (5000). The maximum delay is the sum of maximum timeouts (45000) plus the delay for timeout
    * notification (5000) plus a tolerance of 2000.
    */
    @Test
    public void testClientReceivesTimeoutNotification(){
        //Test if the client received a timeout notifiaction
        assertFalse("Client did not receive a timeout notification at all.",
                responseProcessor.getRetransmissionTimeoutMessages().isEmpty());

        long minDelay = 35000;
        long maxDelay = 52000;

        long actualDelay = responseProcessor.getRetransmissionTimeoutTime(0) - timeRequestSent;

        String message = "Retransmition timeout notification"
                + " (expected delay between " + minDelay + " and " + maxDelay + "ms,"
                + " actual delay " + actualDelay + "ms).";

        log.info(message);
        assertTrue(message, minDelay <= actualDelay);
        assertTrue(message, maxDelay >= actualDelay);
    }
}


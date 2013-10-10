package org.infinispan.server.test.jgroups.auth;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServers;
import org.infinispan.arquillian.core.WithRunningServer;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for JGroups AUTH protocol. Nodes with correct certificate should be allowed to join the cluster, others
 * should not.
 *
 * TODO: Check replay attack
 * 
 * @author Martin Gencur
 * 
 */
@RunWith(Arquillian.class)
public class AuthProtocolTest {

    @InfinispanResource
    RemoteInfinispanServers servers;

    @ArquillianResource
    ContainerController controller;

    final String JOINING_NODE_FRIEND = "clustered-auth-2";
    final String JOINING_NODE_ALIEN = "clustered-auth-3"; //having different certificate
    final String JOINING_NODE_DUMMY_ALIEN = "clustered-auth-4";//this node just doesn't have AUTH defined at all

    @WithRunningServer("clustered-auth-1")
    @Test
    public void testFriendlyNodeCanJoin() throws Exception {
        try {
            controller.start(JOINING_NODE_FRIEND);
            assertEquals(2, servers.getServer(JOINING_NODE_FRIEND).getCacheManager("clustered").getClusterSize());
        } catch (SecurityException e) {
            fail("The friendly node should have been allowed to join!");
        } finally {
            controller.stop(JOINING_NODE_FRIEND);
        }
    }

    @WithRunningServer("clustered-auth-1")
    @Test
    public void testAlienNodeCannotJoin() throws Exception {
        try {
            controller.start(JOINING_NODE_ALIEN);
            fail("The alien node should not have been allowed to join!");
        } catch (SecurityException e) {
            //OK - we expect the exception
        } finally {
            controller.stop(JOINING_NODE_ALIEN);
        }
    }

    @WithRunningServer("clustered-auth-1")
    @Test
    public void testDummyAlienNodeCannotJoin() throws Exception {
        try {
            controller.start(JOINING_NODE_DUMMY_ALIEN);
            fail("The alien node should not have been allowed to join!");
        } catch (SecurityException e) {
            //OK - we expect the exception
        } finally {
            controller.stop(JOINING_NODE_DUMMY_ALIEN);
        }
    }
}
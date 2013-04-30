/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.infinispan.server.test.configs;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests example configuration file standalone-hotrod-ssl.xml.
 *
 * There are four files for keystores and truststores:
 * keystore_client.jks - trusted by servers with truststore_server.jks
 * keystore_server.jks - trusted by clients with truststore_client.jks
 * truststore_client.jks - clients which use this file trust only certificates in keystore_server.jks
 * truststore_server.jks - servers which use this file trust only certificates in keystore_client.jks
 *
 * @author Tristan Tarrant
 * @author Martin Gencur
 */
@RunWith(Arquillian.class)
public class SslHotRodConfigExampleTest {
   final String DEFAULT_CACHE_MANAGER = "local";
   final String DEFAULT_CACHE = "default";

   @InfinispanResource
   RemoteInfinispanServer server;

   RemoteCacheManager trustedCM;
   RemoteCacheManager spuriousCM;
   RemoteCacheManager trustedCMSpuriousServer;

   String serverConfigPath = System.getProperty("server1.dist") + File.separator + "standalone" + File.separator + "configuration";

   @Before
   public void setUp() {
      trustedCM = configureRCM("keystore_client.jks", "truststore_client.jks");
      //use a wrong client certificate (untrusted by the server)
      spuriousCM = configureRCM("keystore_server.jks", "truststore_client.jks");
      //use a different truststore for the client -> the server will end up as untrusted by the client
      trustedCMSpuriousServer = configureRCM("keystore_client.jks", "truststore_server.jks");
   }

   @Test
   public void testDefaultCache() throws Exception {
      doPutGet(DEFAULT_CACHE, trustedCM);
   }

   @Test(expected = TransportException.class)
   public void testDefaultCacheFailClientAuth() throws Exception {
      doPutGet(DEFAULT_CACHE, spuriousCM);
   }

   @Test(expected = TransportException.class)
   public void testDefaultCacheFailServerAuth() throws Exception {
      doPutGet(DEFAULT_CACHE, trustedCMSpuriousServer);
   }

   private void doPutGet(String cacheName, RemoteCacheManager rcm) {
      RemoteCache<String, String> cache = rcm.getCache(cacheName);
      assertEquals(0, numEntries(cacheName));
      for (int i = 0; i < 10; i++) {
         cache.put("k" + i, "v" + i);
      }
      assertTrue(numEntries(cacheName) <= 10);
      for (int i = 0; i < 10; i++) {
         assertEquals("v" + i, cache.get("k" + i));
      }
   }

   private long numEntries(String cacheName) {
      return server.getCacheManager(DEFAULT_CACHE_MANAGER).getCache(cacheName).getNumberOfEntries();
   }

   private RemoteCacheManager configureRCM(final String keystoreName, final String truststoreName) {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      builder
            .addServer()
            .host(server.getHotrodEndpoint().getInetAddress().getHostName())
            .port(server.getHotrodEndpoint().getPort())
            .ssl()
            .enable()
            .keyStoreFileName(serverConfigPath + File.separator + keystoreName)
            .keyStorePassword("secret".toCharArray())
            .trustStoreFileName(serverConfigPath + File.separator + truststoreName)
            .trustStorePassword("secret".toCharArray());
      return new RemoteCacheManager(builder.build());
   }
}

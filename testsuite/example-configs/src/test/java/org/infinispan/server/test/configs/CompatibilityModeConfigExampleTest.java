/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.infinispan.server.test.configs;

import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.HttpMethod;
import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.apache.commons.httpclient.HttpClient;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test standalone-compatibility-mode.xml example configuration file.
 *
 * //TODO: test also Memcached client
 *
 * @author Martin Gencur
 */
@RunWith(Arquillian.class)
public class CompatibilityModeConfigExampleTest {

   final String DEFAULT_CACHE_MANAGER = "local";
   final String DEFAULT_CACHE = "default";

   @InfinispanResource
   RemoteInfinispanServer server;

   RemoteCache<String, byte[]> hotrodCache;
   HttpClient restClient;
   String restUrl;

   @Before
   public void setUp() {
      hotrodCache = new RemoteCacheManager(new ConfigurationBuilder().addServer()
                                                 .host(server.getHotrodEndpoint().getInetAddress().getHostName())
                                                 .port(server.getHotrodEndpoint().getPort())
                                                 .build()).getCache();
      restClient = new HttpClient();
      restUrl = "http://" + server.getHotrodEndpoint().getInetAddress().getHostName() + ":8080" + server.getRESTEndpoint().getContextPath();
   }

   @Test
   public void testHotRodPutRestGetTest() throws Exception {
      final String key = "1";

      // 1. Put with Hot Rod
      RemoteCache<String, byte[]> remote = getHotRodCache();
      assertEquals(null, remote.withFlags(Flag.FORCE_RETURN_VALUE).put(key, "v1".getBytes()));
      assertArrayEquals("v1".getBytes(), remote.get(key));

      // 2. Get with REST
      HttpMethod get = new GetMethod(getRestUrl() + "/" + key);
      getRestClient().executeMethod(get);
      assertEquals(HttpServletResponse.SC_OK, get.getStatusCode());
      assertEquals("v1".getBytes(), get.getResponseBody());
   }


   @Test
   public void testRestPutHotRodGetTest() throws Exception {
      final String key = "2";

      // 1. Put with REST
      EntityEnclosingMethod put = new PutMethod(getRestUrl() + "/" + key);
      put.setRequestEntity(new ByteArrayRequestEntity(
            "<hey>ho</hey>".getBytes(), "application/octet-stream"));
      HttpClient restClient = getRestClient();
      restClient.executeMethod(put);
      assertEquals(HttpServletResponse.SC_OK, put.getStatusCode());
      assertEquals("", put.getResponseBodyAsString().trim());

      // 2. Get with Hot Rod
      assertEquals("<hey>ho</hey>".getBytes(), getHotRodCache().get(key));
   }

   private RemoteCache<String, byte[]> getHotRodCache() {
      return hotrodCache;
   }

   private HttpClient getRestClient() {
      return restClient;
   }

//   private MemcachedClient getMemcachedClient() {
//      return memcachedClient;
//   }

   private String getRestUrl() {
      return restUrl;
   }

}

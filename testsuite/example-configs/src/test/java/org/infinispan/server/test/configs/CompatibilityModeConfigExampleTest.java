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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.apache.commons.httpclient.HttpClient;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

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
   MemcachedClient memcachedClient;
   String restUrl;

   @Before
   public void setUp() throws Exception {
      hotrodCache = new RemoteCacheManager(new ConfigurationBuilder().addServer()
                                                 .host(server.getHotrodEndpoint().getInetAddress().getHostName())
                                                 .port(server.getHotrodEndpoint().getPort())
                                                 .build()).getCache();
      restClient = new HttpClient();
      restUrl = "http://" + server.getHotrodEndpoint().getInetAddress().getHostName() + ":8080" + server.getRESTEndpoint().getContextPath();
      memcachedClient = new MemcachedClient(server.getMemcachedEndpoint().getInetAddress().getHostName(), server.getMemcachedEndpoint().getPort());
   }

//   @After
//   public void tearDown() throws Exception {
//      memcachedClient.close();
//   }

//      @Test
//   public void testMemcachedPutRestHotRodGetTest() throws Exception {
//      final String key = "1";
//
//      // 1. Put with Memcached
//      Future<Boolean> f = getMemcachedClient().set(key, 0, "v1");
//      assertTrue(f.get(60, TimeUnit.SECONDS));
//
//      // 2. Get with REST
//      HttpMethod get = new GetMethod(getRestUrl() + "/" + key);
//      getRestClient().executeMethod(get);
//      assertEquals(HttpServletResponse.SC_OK, get.getStatusCode());
//      assertEquals("text/plain", get.getResponseHeader("Content-Type").getValue());
//      assertEquals("v1", get.getResponseBodyAsString());
//
//      // 3. Get with Hot Rod
//      assertEquals("v1", getHotRodCache().get(key));
//   }

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

   private MemcachedClient getMemcachedClient() {
      return memcachedClient;
   }

   private String getRestUrl() {
      return restUrl;
   }

   /**
    * A Really simple Memcached client.
    *
    * @author Michal Linhard
    * @author Martin Gencur
    */
   static class MemcachedClient {

      private static final int DEFAULT_TIMEOUT = 10000;
      private static final String DEFAULT_ENCODING = "UTF-8";

      private String encoding;
      private Socket socket;
      private PrintWriter out;
      private InputStream input;

      public MemcachedClient(String host, int port) throws IOException {
         this(DEFAULT_ENCODING, host, port, DEFAULT_TIMEOUT);
      }

      public MemcachedClient(String enc, String host, int port, int timeout) throws IOException {
         encoding = enc;
         socket = new Socket(host, port);
         socket.setSoTimeout(timeout);
         out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), encoding));
         input = socket.getInputStream();
      }

      public String get(String key) throws IOException {
         byte[] data = getBytes(key);
         return (data == null ) ? null : new String(data, encoding);
      }

      public byte[] getBytes(String key) throws IOException {
         writeln("get " + key);
         flush();
         String valueStr = readln();
         if (valueStr.startsWith("VALUE")) {
            String[] value = valueStr.split(" ");
            assertEquals(key, value[1]);
            int size = new Integer(value[3]);
            byte[] ret = read(size);
            assertEquals('\r', read());
            assertEquals('\n', read());
            assertEquals("END", readln());
            return ret;
         } else {
            return null;
         }
      }

      public void set(String key, String value) throws IOException {
         writeln("set " + key + " 0 0 " + value.getBytes(encoding).length);
         writeln(value);
         flush();
         assertEquals("STORED", readln());
      }

      public String getCasId(String aKey) throws IOException {
         writeln("gets " + aKey);
         flush();
         String[] valueline = readln().split(" ");
         assertEquals("VALUE", valueline[0]);
         assertEquals(aKey, valueline[1]);
         read(new Integer(valueline[3]));
         assertEquals("", readln());
         assertEquals("END", readln());
         return valueline[4];
      }

      private byte[] read(int len) throws IOException {
         try {
            byte[] ret = new byte[len];
            input.read(ret, 0, len);
            return ret;
         } catch (SocketTimeoutException ste) {
            return null;
         }
      }

      private byte read() throws IOException {
         try {
            return (byte) input.read();
         } catch (SocketTimeoutException ste) {
            return -1;
         }
      }

      private String readln() throws IOException {
         byte[] buf = new byte[512];
         int maxlen = 512;
         int read = 0;
         buf[read] = read();
         while (buf[read] != '\n') {
            read++;
            if (read == maxlen) {
               maxlen += 512;
               buf = Arrays.copyOf(buf, maxlen);
            }
            buf[read] = read();
         }
         if (read == 0) {
            return "";
         }
         if (buf[read - 1] == '\r') {
            read--;
         }
         buf = Arrays.copyOf(buf, read);
         return new String(buf, encoding);
      }

      private void writeln(String str) {
         out.print(str + "\r\n");
      }

      private void write(byte[] data) throws IOException {
         socket.getOutputStream().write(data);
      }

      private void flush() {
         out.flush();
      }

      private void close() throws IOException {
         socket.close();
      }
   }

}

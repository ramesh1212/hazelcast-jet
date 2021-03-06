package com.hazelcast.jet.impl.deployment;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.util.FilteringClassLoader;
import deployment.AbstractDeploymentTest;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import static java.util.Collections.singletonList;

@Category(QuickTest.class)
@RunWith(HazelcastSerialClassRunner.class)
public class ClientDeploymentTest extends AbstractDeploymentTest {

    @Rule
    public final Timeout timeoutRule = Timeout.seconds(360);

    private Object isolatedNode;
    private JetInstance client;

    @After
    public void tearDown() {
        Jet.shutdownAll();
        shutdownIsolatedNode();
    }


    @Override
    protected JetInstance getJetInstance() {
        return client;
    }

    @Override
    protected void createCluster() {
        Thread thread = Thread.currentThread();
        ClassLoader tccl = thread.getContextClassLoader();
        String host;
        Integer port;
        try {
            FilteringClassLoader cl = new FilteringClassLoader(singletonList("deployment"), "com.hazelcast");
            isolatedNode = createIsolatedNode(thread, cl);
            Class<?> jetInstanceClazz = cl.loadClass("com.hazelcast.jet.JetInstance");
            Method getCluster = jetInstanceClazz.getDeclaredMethod("getCluster");
            Object clusterObj = getCluster.invoke(isolatedNode);
            Class<?> cluster = cl.loadClass("com.hazelcast.core.Cluster");
            Method getLocalMember = cluster.getDeclaredMethod("getLocalMember");
            Object memberObj = getLocalMember.invoke(clusterObj);
            Class<?> member = cl.loadClass("com.hazelcast.core.Member");
            Method getAddress = member.getDeclaredMethod("getAddress");
            Object addressObj = getAddress.invoke(memberObj);
            Class<?> address = cl.loadClass("com.hazelcast.nio.Address");
            Method getHost = address.getDeclaredMethod("getHost");
            Method getPort = address.getDeclaredMethod("getPort");
            host = (String) getHost.invoke(addressObj);
            port = (Integer) getPort.invoke(addressObj);
        } catch (Exception e) {
            throw new RuntimeException("Could not start isolated Hazelcast instance", e);
        } finally {
            thread.setContextClassLoader(tccl);
        }
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getGroupConfig().setName("jet");
        clientConfig.getGroupConfig().setPassword("jet-pass");
        clientConfig.getNetworkConfig().setAddresses(singletonList(host + ":" + port));
        client = Jet.newJetClient(clientConfig);
    }

    protected void shutdownIsolatedNode() {
        if (isolatedNode == null) {
            return;
        }
        try {
            Class<?> instanceClass = isolatedNode.getClass();
            Method method = instanceClass.getMethod("shutdown");
            method.invoke(isolatedNode);
            isolatedNode = null;
        } catch (Exception e) {
            throw new RuntimeException("Could not start shutdown Hazelcast instance", e);
        }
    }
}

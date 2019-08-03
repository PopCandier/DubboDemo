#### 发布服务的过程

我们回到`RegistryProtocol`这个类中

```java
@Override
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        URL registryUrl = getRegistryUrl(originInvoker);
        // url to export locally
        URL providerUrl = getProviderUrl(originInvoker);

        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);
        //export invoker
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);

        // 将服务注册到zk节点上去。
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getRegisteredProviderUrl(providerUrl, registryUrl);
       
        //....
    }
```

简单描述一下就是，Invoker是服务端，服务接口实现类的封装，他将返回AbstractProxyInvoker实例，并且其中的doInvoker方法，进行一些我们例如负载均衡，降级之类的wrapper，之后ProxyFacotry.getInvoker返回，最后存到exprotMap中，当请求来到的时候，通过key找到相应的invoker进行调用，并且返回result

```java
//这个默认实现在 JavassistProxyFactory
@Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        //自己构建了字节码，算是代理，然后加载到jvm中
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }
```

```java
public interface Invoker<T> extends Node {

    /**
     * get service interface.
     *
     * @return service interface.
     */
    Class<T> getInterface();

    /**
     * invoke.
     *
     * @param invocation
     * @return result
     * @throws RpcException
     */
    Result invoke(Invocation invocation) throws RpcException;

}
```

### 对于服务端的一个总结

服务端的流程只是一个简单的解析配置，到暴露服务，和注册服务的过程。

其中涉及到了比较多的自适应扩展点的应用。

从ServiceBean与ServiceConfig开始，ServiceConfig的export方法是所以的流程的入口。

export中，大体上分成两个流程，目前说道流程就是暴露服务和注册服务

暴露服务一开始并不知道具体需要暴露的服务是什么，可能是dubbo也可能是consul

同理，注册服务一开始也不知道具体需要注册的是什么服务，可能是redis也可能是zookeeper

而从解析到发布服务贯穿所有中心轴的就是`URL的驱动`

暴露服务：protocol:ip:port:params

注册服务:   register:ip:port:params

随着对ServiceConfig和RegisterConfig这个由dubbo集成到Spring的类，也就是对dubbo的配置进行解析。

将会知道，我们配置的是dubbo协议和zookeeper的注册中心。

但是实际到上框架对自适应扩展点的DubboProtcol和ZookeeperRegisterFacotry进行了不同程度的封装。

前者有过滤器的warpper，监听器的warraper等。通过依赖注入extionload的inject的方法包装。构造器。

除了对已经配置好的dubbo协议的封装，由于请求过来，需要返回响应结果，所以DubboProtocol还存在一个DubboExport的对象，这个对象将Invoker和对应的作为key的接口全路径放进了exportMap中，

Invoker中通过默认的javasisitProxyFacotry也就是通过ProxyFacotry.getInvoker方法，当然这也是通过了spi机制自动适配的找到的，使用字节码的方式将服务器接口的实现类整合成了一个Wrapper，然后塞进了AbstartProxyFactory中，不过这里面也存在的多层的warper，这样当有请求来的时候，必然也会带着接口名，参数和方法名。然后通过key找到Invoker，由于之前字节码方式整合了实现类，这样通过调用返回结果给调用方。



#### 客户端的解释

这个，从ReferenceConfig开始说起。

```java
@Reference
ISayHelloService isayHelloService;
```

* 生成代理对象（帮我们实现网络通信细节）
* 建立通信连接 netty
* 从zookeeper 获得目标地址 订阅节点变化
* 负载均衡
* 容错
* mock
* 序列化...

#### 大致分为两个步骤

* 启动服务阶段
  * 构建通信连接（长连接）| 获得一个远程代理对象
* 运行阶段，触发远程调用，invoker->doinvoker->trasporter --...-->返回结果



获得代理对象步骤

```java
//ReferenceConfig
public synchronized T get() {
        checkAndUpdateSubConfigs();

        if (destroyed) {
            throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
        }
        if (ref == null) {
            init();//进入
        }
        return ref;
    }
```

```java
private void init() {
        //....
//以上代码都是为了构建url
        String hostToRegistry = ConfigUtils.getSystemProperty(DUBBO_IP_TO_REGISTRY);
        if (StringUtils.isEmpty(hostToRegistry)) {
            hostToRegistry = NetUtils.getLocalHost();
        } else if (isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        map.put(REGISTER_IP_KEY, hostToRegistry);

        ref = createProxy(map);//由于之前说了，reference其实就是个创建与服务器通信代理的过程，这个地方就是入口。

        String serviceKey = URL.buildKey(interfaceName, group, version);
        ApplicationModel.initConsumerModel(serviceKey, buildConsumerModel(serviceKey, attributes));
        initialized = true;
    }
```



```java
@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
private T createProxy(Map<String, String> map) {
    if (shouldJvmRefer(map)) {//如果是injvm也就是没有远程，使用本地，这里可以不看，因为我们是远程
        URL url = new URL(LOCAL_PROTOCOL, LOCALHOST_VALUE, 0, interfaceClass.getName()).addParameters(map);
        invoker = REF_PROTOCOL.refer(interfaceClass, url);
        if (logger.isInfoEnabled()) {
            logger.info("Using injvm service " + interfaceClass.getName());
        }
    } else {
        urls.clear(); // reference retry init will add url to urls, lead to OOM
        if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
            //如果没有配置注册中心，这里是基于点对点的配置，并放到urls中去保存
            String[] us = SEMICOLON_SPLIT_PATTERN.split(url);
            if (us != null && us.length > 0) {
                for (String u : us) {
                    URL url = URL.valueOf(u);
                    if (StringUtils.isEmpty(url.getPath())) {
                        url = url.setPath(interfaceName);
                    }
                    if (REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                        urls.add(url.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
                    } else {
                        urls.add(ClusterUtils.mergeUrl(url, map));
                    }
                }
            }
        } else { // assemble URL from register center's configuration
            // if protocols not injvm checkRegistry
            //存在注册中心的情况
            if (!LOCAL_PROTOCOL.equalsIgnoreCase(getProtocol())){
                checkRegistry();
                //register://ip:port 构建多个这样的，当然服务端我们说过，可以构建多个，所以这里也是个list
                List<URL> us = loadRegistries(false);
                if (CollectionUtils.isNotEmpty(us)) {
                    for (URL u : us) {
                        URL monitorUrl = loadMonitor(u);
                        if (monitorUrl != null) {
                            map.put(MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                        }
                        urls.add(u.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
                    }
                }
                if (urls.isEmpty()) {
                    throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                }
            }
        }

        if (urls.size() == 1) {
            //同时我们配置了一个注册中心，所以会进入这个方法，这很明显是一个自适应扩展点，和export一样，因为之前的register：//所以这里的protocol是一个registerProtocol，现在我们找到这个的实现。
            /*
            当然按照以前服务端的知识点，这是一个被Qos(listener(filter(registerprotocl)))
            包装的类，虽然最后还是调用了registerProtocol
            */
            invoker = REF_PROTOCOL.refer(interfaceClass, urls.get(0));
        } else {
            //....
}
```

`RegisterProtocol`

```java
public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
    //这里的register:// 就被替换成了 zookeeper://了
        url = URLBuilder.from(url)
                .setProtocol(url.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY))
                .removeParameter(REGISTRY_KEY)
                .build();
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        // group="a,b" or group="*"
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));
        String group = qs.get(GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((COMMA_SPLIT_PATTERN.split(group)).length > 1 || "*".equals(group)) {
                return doRefer(getMergeableCluster(), registry, type, url);
            }
        }
        return doRefer(cluster, registry, type, url);//进入
    }
```

```java
private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
    /*
    Directory是个很重要的类，他就是传说中用来缓存服务端的服务地址的类，并且提供监听
    当服务端服务节点发生变化的时候，它将会被感知
    */
    
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());
        URL subscribeUrl = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (!ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(REGISTER_KEY, true)) {
            directory.setRegisteredConsumerUrl(getRegisteredConsumerUrl(subscribeUrl, url));
            registry.register(directory.getRegisteredConsumerUrl());
        }
        directory.buildRouterChain(subscribeUrl);
        directory.subscribe(subscribeUrl.addParameter(CATEGORY_KEY,
                PROVIDERS_CATEGORY + "," + CONFIGURATORS_CATEGORY + "," + ROUTERS_CATEGORY));
    
    
    
    	/*
    	以上 也就是直接去连接注册中心，并找到所有服务节点，缓存到本地的Directory对象中。
    	*/
    
		//这里是cluster的扩展点，我们可以看一下有哪些实现
        Invoker invoker = cluster.join(directory);
    // 服务端注册消费者
        ProviderConsumerRegTable.registerConsumer(invoker, url, subscribeUrl, directory);
        return invoker;
    }
```

![1564823442116](https://github.com/PopCandier/DubboDemo/blob/master/img/1564823442116.png)

所以，这个invoker返回的是应该是被

```java
invoker=MockClusterWrapper(FailoverCluster(direcotry)) //包装的代理类
```

到此，代理对象完成，也就意味着我们增加了`@Reference`注解的对象，最终会变成这个样子。

不过，现在还没有说两个问题，那就是代理对象中的

* 从zk上获得provider url
* 基于url中的ip:port建立连接

这我们就需要讲一下还没讲的代码

```java
// doRefer 的另一个部分
/*
	构建了一个consumer：//ip:port 保存到zk上
	
	provider / consumers /configuration / routers
	
	1. 连接到注册中心
	2. 从注册中心拿到地址 providerUrl
	3. 基于provider 地址建立通信
*/
RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);//register 连接 zk的api
        directory.setProtocol(protocol);//protocol dubboprotocl 想起之前服务端的invoker去建立通信。这里先设置进入，这里面一定会有相应处理
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());
        URL subscribeUrl = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (!ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(REGISTER_KEY, true)) {
            directory.setRegisteredConsumerUrl(getRegisteredConsumerUrl(subscribeUrl, url));
            registry.register(directory.getRegisteredConsumerUrl());
        }
        directory.buildRouterChain(subscribeUrl);
		

		// 订阅，进入。
        directory.subscribe(subscribeUrl.addParameter(CATEGORY_KEY,
                PROVIDERS_CATEGORY + "," + CONFIGURATORS_CATEGORY + "," + ROUTERS_CATEGORY));


    
```

![1564824636203](https://github.com/PopCandier/DubboDemo/blob/master/img/1564824636203.png)

static不用注册中心的情况，register从目标服务获得地址。类似`List<String> urls`

注册中心还有个`NotifyListener`的监听，用于监听注册中心的变化



```java
public void subscribe(URL url) {
        setConsumerUrl(url);
        CONSUMER_CONFIGURATION_LISTENER.addNotifyListener(this);
        serviceConfigurationListener = new ReferenceConfigurationListener(this, url);
        //FailBackReigster  失败重试 因为是一个ZookeeperRegister 失败重试策略
    // 并且这个this 是一个 RegistryDirectory 并且这个参数还是一个listener也就是一个监听的参数
        registry.subscribe(url, this);
    }
```

```java
public void subscribe(URL url, NotifyListener listener) {
        super.subscribe(url, listener);
        removeFailedSubscribed(url, listener);
        try {
            // Sending a subscription request to the server side
            doSubscribe(url, listener);//进入
        } catch (Exception e) {
            Throwable t = e;

            List<URL> urls = getCacheUrls(url);
            if (CollectionUtils.isNotEmpty(urls)) {
                notify(url, listener, urls);
                logger.error("Failed to subscribe " + url + ", Using cached list: " + urls + " from cache file: " + getUrl().getParameter(FILE_KEY, System.getProperty("user.home") + "/dubbo-registry-" + url.getHost() + ".cache") + ", cause: " + t.getMessage(), t);
            } else {
                // If the startup detect
```

选择`ZookpeerRegister`

```java
public void doSubscribe(final URL url, final NotifyListener listener) {
        try {
            //任何服务都匹配 当然这里不走，走下面else
            if (ANY_VALUE.equals(url.getServiceInterface())) {
                String root = toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                if (listeners == null) {
                    zkListeners.putIfAbsent(url, new ConcurrentHashMap<>());
                    listeners = zkListeners.get(url);
                }
                ChildListener zkListener = listeners.get(listener);
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, (parentPath, currentChilds) -> {
                        for (String child : currentChilds) {
                            child = URL.decode(child);
                            if (!anyServices.contains(child)) {
                                anyServices.add(child);
                                subscribe(url.setPath(child).addParameters(INTERFACE_KEY, child,
                                        Constants.CHECK_KEY, String.valueOf(false)), listener);
                            }
                        }
                    });
                    zkListener = listeners.get(listener);
                }
                zkClient.create(root, false);
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (CollectionUtils.isNotEmpty(services)) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            } else {
                List<URL> urls = new ArrayList<>();
                /*
                这里的  Categories 
                表示zk下面的各种节点
                configurator consumer router
                并且监听他们各种的变化
                */
                for (String path : toCategoriesPath(url)) {
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                    if (listeners == null) {//没有就初始化
                        zkListeners.putIfAbsent(url, new ConcurrentHashMap<>());
                        listeners = zkListeners.get(url);
                    }
                    //对其子类接着进行监听。
                    ChildListener zkListener = listeners.get(listener);
                    if (zkListener == null) {
                        listeners.putIfAbsent(listener, (parentPath, currentChilds) -> ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds)));
                        zkListener = listeners.get(listener);
                    }
                    zkClient.create(path, false);
                    //字节点进行监听 providerUrl的监听
                    List<String> children = zkClient.addChildListener(path, zkListener);
                    if (children != null) {
                        urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                //通知
                notify(url, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
```

```java
protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        //...
        Map<String, List<URL>> categoryNotified = notified.computeIfAbsent(url, u -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            List<URL> categoryList = entry.getValue();
            categoryNotified.put(category, categoryList);
            //通知
            //这里的listner RegisterFacotry
            listener.notify(categoryList);
            //注册中心挂了，这里是缓存地址。保存在文件中。
            saveProperties(url);
        }
    }
```

```java
public synchronized void notify(List<URL> urls) {
        //....
        // providers
        List<URL> providerURLs = categoryUrls.getOrDefault(PROVIDERS_CATEGORY, Collections.emptyList());
    //刷新覆盖Invoker 
    //providerURLS 就是dubbo://ip:port 的集合
        refreshOverrideAndInvoker(providerURLs);//进去
    }
```

```java
 private void refreshOverrideAndInvoker(List<URL> urls) {
        // mock zookeeper://xxx?mock=return null
        overrideDirectoryUrl();//覆盖
     	//这个方法很重要
        refreshInvoker(urls);//刷新
    }
```

```java
private void refreshInvoker(List<URL> invokerUrls) {
        Assert.notNull(invokerUrls, "invokerUrls should not be null");

    //....
            if (invokerUrls.isEmpty()) {
                return;
            }
    // 把invokerUrls 转化为 invoker 也是真正通信对象
            Map<String, Invoker<T>> newUrlInvokerMap = toInvokers(invokerUrls);// Translate url list to Invoker map

          //...
    
    routerChain.setInvokers(newInvokers);
            this.invokers = multiGroup ? toMergeInvokerList(newInvokers) : newInvokers;
            this.urlInvokerMap = newUrlInvokerMap;//并且保存在这里。invoker

            try {
                destroyUnusedInvokers(oldUrlInvokerMap, newUrlInvokerMap); // Close the unused Invoker
            } catch (Exception e) {
                logger.warn("destroyUnusedInvokers error. ", e);
            }
    }
```

在`RegistryDirectory`的声明

![1564826583870](https://github.com/PopCandier/DubboDemo/blob/master/img/1564826583870.png)

是不是很像是服务端的DubboProtocol的exportMap，基本是一样的。

```java
private Map<String, Invoker<T>> toInvokers(List<URL> urls) {
        //...
            if (invoker == null) { // Not in the cache, refer again
                try {
                    boolean enabled = true;
                    if (url.hasParameter(DISABLED_KEY)) {
                        enabled = !url.getParameter(DISABLED_KEY, false);
                    } else {
                        enabled = url.getParameter(ENABLED_KEY, true);
                    }
                    if (enabled) {
                        //构建invoker的方法，是不是感觉也很熟悉
                        //也是真正意义上建立通信连接的方法
                        // 这个地方应该是调用dubbo 除去包装不管。
                        invoker = new InvokerDelegate<>(protocol.refer(serviceType, url), url, providerUrl);
                    }
                } catch (Throwable t) {
                    logger.error("Failed to refer invoker for interface:" + serviceType + ",url:(" + url + ")" + t.getMessage(), t);
                }
                if (invoker != null) { // Put new invoker in cache
                    newUrlInvokerMap.put(key, invoker);
                }
            } else {
                newUrlInvokerMap.put(key, invoker);
            }
        }
        keys.clear();
        return newUrlInvokerMap;
    }
```

但是dubbo里面没有这个refer应该是在父类里面`AbstractProtocol`

```java
@Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        return new AsyncToSyncInvoker<>(protocolBindingRefer(type, url));
    }

    protected abstract <T> Invoker<T> protocolBindingRefer(Class<T> type, URL url) throws RpcException;
```

其实又去调用了子类的`DubboProtocol`的方法

```java
@Override
    public <T> Invoker<T> protocolBindingRefer(Class<T> serviceType, URL url) throws RpcException {
        optimizeSerialization(url);

        // create rpc invoker.
        // getClients就是建立通信。有一些共享连接的一些处理，当然可以不处理，主要是可以有多个连接或者一个连接。
        DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
        invokers.add(invoker);

        return invoker;
    }
```

构建的通信会放到一个数组里面，因为一个客户端可以和多个服务器进行连接。

```java
//字段申明
 private final ExchangeClient[] clients;// 《---

// 方法
public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients, Set<Invoker<?>> invokers) {
        super(serviceType, url, new String[]{INTERFACE_KEY, GROUP_KEY, TOKEN_KEY, TIMEOUT_KEY});
        this.clients = clients;//《---
        // get version.
        this.version = url.getParameter(VERSION_KEY, "0.0.0");
        this.invokers = invokers;
    }
```

回忆起之前服务端的调用，是有一个doInvoker方法，会有服务端的AbstartProxyFactory返回的Invoker方法，所以这个客户端也应该有这个doInvoker方法。

```java
 protected Result doInvoke(final Invocation invocation) throws Throwable {
        RpcInvocation inv = (RpcInvocation) invocation;
        final String methodName = RpcUtils.getMethodName(invocation);
        inv.setAttachment(PATH_KEY, getUrl().getPath());
        inv.setAttachment(VERSION_KEY, version);

        ExchangeClient currentClient;
        if (clients.length == 1) {//拿到连接，进行连通。
            currentClient = clients[0];
        } else {
            currentClient = clients[index.getAndIncrement() % clients.length];
        }
       //....
    }
```

代理对象也就完成。
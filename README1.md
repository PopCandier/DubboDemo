#### Dubbo 服务发布和注册

思考，dubbo做到了最基本的功能是什么

* 开放20880端口
* 发布了一个地址 dubbo://192.168.10.40:20880/interface?..(zookeeper)

如果我们要达到这样的目的

* 解析配置文件
* 使用netty进行服务的暴露 20880端口
* 序列化和反序列化
* 把地址注册到注册中心去。

### 如何解析spring的自定义配置文件

传统意义上，spring中有<bean>这样的标签，但是如果我们希望得到

自定义的标签应该如何做到，例如<Pop>这样的标签

* `NamespaceHandlerSupport`
* `BeanDefinitionParser`

```java
public class DubboNamespaceHandler extends NamespaceHandlerSupport {

    static {
        Version.checkDuplicate(DubboNamespaceHandler.class);
    }

    @Override
    public void init() {
        registerBeanDefinitionParser("application", new DubboBeanDefinitionParser(ApplicationConfig.class, true));
        registerBeanDefinitionParser("module", new DubboBeanDefinitionParser(ModuleConfig.class, true));
        registerBeanDefinitionParser("registry", new DubboBeanDefinitionParser(RegistryConfig.class, true));
        registerBeanDefinitionParser("config-center", new DubboBeanDefinitionParser(ConfigCenterBean.class, true));
        registerBeanDefinitionParser("metadata-report", new DubboBeanDefinitionParser(MetadataReportConfig.class, true));
        registerBeanDefinitionParser("monitor", new DubboBeanDefinitionParser(MonitorConfig.class, true));
        registerBeanDefinitionParser("metrics", new DubboBeanDefinitionParser(MetricsConfig.class, true));
        registerBeanDefinitionParser("provider", new DubboBeanDefinitionParser(ProviderConfig.class, true));
        registerBeanDefinitionParser("consumer", new DubboBeanDefinitionParser(ConsumerConfig.class, true));
        registerBeanDefinitionParser("protocol", new DubboBeanDefinitionParser(ProtocolConfig.class, true));
        registerBeanDefinitionParser("service", new DubboBeanDefinitionParser(ServiceBean.class, true));
        registerBeanDefinitionParser("reference", new DubboBeanDefinitionParser(ReferenceBean.class, false));
        registerBeanDefinitionParser("annotation", new AnnotationBeanDefinitionParser());
    }

}//看起来很熟悉
```

解析配置。

dubbo依靠spring的生态，将自己的配置信息生成bean，并托管给了spring，从上面可以看到的是，通过DubboBeanDefintionParser的类将xml中的配置，全部解析成了bean。

Spring会默认加载jar包下面的META-INF/spring.handlers，寻找对应的NamespaceHandler。

Dubbo-config模块下的config-spring存在的这配置。



以上的配置，都存在着将不同的配置转化成了spring中的bean对象

application 对应的 ApplicationConfig 

registry 对应的 RegistryConfig

都象征了 dubbo:application 这样的标签和dubbo:registry这样的表现的解析

而最特殊的莫过于service和reference的解析了，其它人都是config结尾，唯独他们两个是Bean结尾。其实我们可以大概猜到，因为我们之前用过注解的方式与springboot整合，所以服务端被标记了service的服务，和客户端被标记了reference的服务，都会被进行不同程度的代理，并且还要彼此进行通信，所以他们的构成也是这些配置中最为复杂的。

ServiceBean 代表了服务的发布和注册，Reference则代表了服务的请求和消费。



#### ServiceBean 的实现

建议去看一下ServiceBean里的内容，和Spring有关的扩展。

```java
public class ServiceBean<T> extends ServiceConfig<T> implements InitializingBean, DisposableBean,
        ApplicationContextAware, ApplicationListener<ContextRefreshedEvent>, BeanNameAware,
        ApplicationEventPublisherAware {

```

接下来，和大家讲一下这些借口的含义

```java
public interface InitializingBean {
    //当bean初始化的时候，被调用
    void afterPropertiesSet() throws Exception;
}

public interface DisposableBean {
    //当bean销毁时，被调用。
    void destroy() throws Exception;
}

public interface ApplicationContextAware extends Aware {
    //spring容器初始化的时候，获得容器。
    void setApplicationContext(ApplicationContext var1) throws BeansException;
}

public interface BeanNameAware extends Aware {
    //自己初始化的时候，会获得自己本身的id
    void setBeanName(String var1);
}

public interface ApplicationEventPublisherAware extends Aware {
    //事件发送器
    void setApplicationEventPublisher(ApplicationEventPublisher var1);
	/*
	如果想要发布bean到这个事件中去，那么必须继承ApplicationEvent才可以
	被事件所接纳。
	*/
}

public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {
    //监听
    void onApplicationEvent(E var1);
}
/*
  ApplicationListener 监听
  ApplicationEventPublisherAware 事件发送器
  ApplicationEvent 事件的本身
*/
/*******************************************/
private void publishExportEvent() {
        ServiceBeanExportedEvent exportEvent = new ServiceBeanExportedEvent(this);
        applicationEventPublisher.publishEvent(exportEvent);
    }

 //.... serviceBean中的源码

    /**
     * @param applicationEventPublisher
     * @since 2.6.5
     */
    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

// ServiceBeanExportedEvent 这个类的具体细节 实现了applicationEvent
public class ServiceBeanExportedEvent extends ApplicationEvent {

    /**
     * Create a new ApplicationEvent.
     *
     * @param serviceBean {@link ServiceBean} bean
     */
    public ServiceBeanExportedEvent(ServiceBean serviceBean) {
        super(serviceBean);
    }

    /**
     * Get {@link ServiceBean} instance
     *
     * @return non-null
     */
    public ServiceBean getServiceBean() {
        return (ServiceBean) super.getSource();
    }
}

```

在这个类中，serviceBean实现了许多spring提供的借口，为了他自己的dubbo更好的整合到spring的生态中去，虽然上面提到了，但是之类还是需要重复一遍，serviceBean中最关键的是几个监听的方法

* ApplicationEvent 表示事件本身
* ApplicationEventPublisherAware 事件发送器，需要实现该接口
* ApplicationListener 事件监听

由于本身ServiceBean实现了ApplicationEvent接口，而且也是事件发送器，意味着他所注册事件将会被spring感知，而他自己也监听了spring的ContextRefreshedEvent，也就是上下文刷新的方法，意味着，spring启动的时候，将会发生什么事情。

他本身有一个ServiceBeanEvent的方法，实现了ApplicationEvent接口，并且ServiceBean并将这个事件加入了ApplicationEventPublisherAware 的实现的方法中。这样，如果你希望实现事件的异步监听的话，首先需要实现ApplicationEventPublisherAware 接口，然后呢ApplicationEventPublisherAware 只允许你接受发布ApplicationEvent实现类的时间，所以ServiceBeanExportedEvent便是dubbo自己定义的事件，你可以在实现了ApliationEventList中指定你想要监听的类型，就像是选择了监听ContextRefershedEvent一样的。

```java
private void publishExportEvent() {
        ServiceBeanExportedEvent exportEvent = new ServiceBeanExportedEvent(this);
        applicationEventPublisher.publishEvent(exportEvent);
    }

public class ServiceBeanExportedEvent extends ApplicationEvent {

    /**
     * Create a new ApplicationEvent.
     *
     * @param serviceBean {@link ServiceBean} bean
     */
    public ServiceBeanExportedEvent(ServiceBean serviceBean) {
        super(serviceBean);
    }

    /**
     * Get {@link ServiceBean} instance
     *
     * @return non-null
     */
    public ServiceBean getServiceBean() {
        return (ServiceBean) super.getSource();
    }
}
```

当然，回到最重要的地方，就是ApplicationEventListener方法了，这个接口的onApplicationEvent方法，才是暴露服务的关键。

```java
@Override// 这个ContextRefreshedEvent 事件是spring上下文被刷新或者加载的时候刷新
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!isExported() && !isUnexported()) {
            if (logger.isInfoEnabled()) {
                logger.info("The service ready on spring started. service: " + getInterface());
            }
            export();//暴露，发布 dubbo服务开启的入口
        }
    }
```

当spring启动的时候，这个方法将会被调用。

```java
@Override
    public void export() {
        super.export();//进入
        // Publish ServiceBeanExportedEvent
        publishExportEvent();
    }
//然后，这个方法就进入了ServiceConfig中
 public synchronized void export() {
        checkAndUpdateSubConfigs();//检查更新配置
	
     	/*
     	判断是否要发布，这个的配置在
     	@Service(export=false)的情况下不会发布，因为
     	可能这个服务还没有开发好，所以暂时选择不发布
     	
     	*/
        if (!shouldExport()) {
            return;
        }
		/*
		@Service(export = false,delay = 9999)
		是否延迟发布
		因为可能spring容器还没加载好的时候，就发布可能会有问题
		所以就会要求延迟加载了。
		*/
        if (shouldDelay()) {
            delayExportExecutor.schedule(this::doExport, getDelay(), TimeUnit.MILLISECONDS);
        } else {
            doExport();//进入
        }
    }

protected synchronized void doExport() {
        if (unexported) {
            throw new IllegalStateException("The service " + interfaceClass.getName() + " has already unexported!");
        }
        if (exported) {//检查服务是否发布过了
            return;
        }
        exported = true;

        if (StringUtils.isEmpty(path)) {
            path = interfaceName;
        }
        doExportUrls();//进入
    }

```

其实上述的多个步骤，仍然是在解析service配置项。

```java

private void doExportUrls() {
    /*
    第一部将会加载注册中心的注册地址，因为注册中心可以配置多个，所以这里是list
    而且到了这一步，url地址应该为
    registry://192.168.10.40:2181
    */
    
        List<URL> registryURLs = loadRegistries(true);
        for (ProtocolConfig protocolConfig : protocols) {
           //....
    }
```

值得说明的是dubbo的源码中，`Url驱动`是dubbo源码的核心，通过不断改变url的头，还有后面的参数和值用于给后续的处理带来信息，例如这一步的loadRegistries方法。

```java
protected List<URL> loadRegistries(boolean provider) {
        // check && override if necessary
        List<URL> registryList = new ArrayList<URL>();
        if (CollectionUtils.isNotEmpty(registries)) {
            //RegistryConfig这个很明显，这个从配置文件中<dubbo:register address="xxx">
            /*
            配置项目解析出来的集合，将会被在这里统一装配
            */
            for (RegistryConfig config : registries) {
                String address = config.getAddress();
                if (StringUtils.isEmpty(address)) {//如果没有地址，也就是address不存在什么值的时候，将会默认给出 0.0.0.0的值
                    address = ANYHOST_VALUE;
                }
                if (!RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(address)) {//如果是合法的值，将会走这里
                    Map<String, String> map = new HashMap<String, String>();
                    appendParameters(map, application);
                    appendParameters(map, config);
                    map.put(PATH_KEY, RegistryService.class.getName());
                    appendRuntimeParameters(map);
                    if (!map.containsKey(PROTOCOL_KEY)) {
                        map.put(PROTOCOL_KEY, DUBBO_PROTOCOL);
                    }
                    List<URL> urls = UrlUtils.parseURLs(address, map);
					//这上面都是在拼装url
                    
                    for (URL url : urls) {
                        
                        //.......
                        //被这样标记出来的代码，会在dubbo源码中出现很多次，因为他表示不断被替换的头，而这个地方，则是将协议头，替换成register，也就是register：//
                        url = URLBuilder.from(url)
                                .addParameter(REGISTRY_KEY, url.getProtocol())
                                .setProtocol(REGISTRY_PROTOCOL)
                                .build();
                        //...........
                        
                        if ((provider && url.getParameter(REGISTER_KEY, true))
                                || (!provider && url.getParameter(SUBSCRIBE_KEY, true))) {
                            registryList.add(url);
                        }
                    }
                }
            }
        }
        return registryList;
    }
```

这上面，就组装好了RegisterConfig也就是注册中心配置，如果你配置了多个注册中心，那么也就意味着会循环多次，并且替换响应的协议头放到registryList中，返回回去。让我们回到上一层代码。

```java
private void doExportUrls() {
        List<URL> registryURLs = loadRegistries(true);
    		//所以这里的协议名称大概是这样的
    //registry://ip:port/org.apache.dubbo.registry.RegistryService（我们注册的服务）
        for (ProtocolConfig protocolConfig : protocols) {
            //这里也很明显，是协议的组装，协议涉及了接口的路径，group组别，版本号等
            String pathKey = URL.buildKey(getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), group, version);
            ProviderModel providerModel = new ProviderModel(pathKey, ref, interfaceClass);
            ApplicationModel.initProviderModel(pathKey, providerModel);
            //以上也就是dubbo:protocol的初始化了。
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);//进入
            //而这里出来后的协议，大概就是
            //dubbo://192.168.13.1:20881/com.gupaoedu.dubbo.practice.ISayHelloService/后面还有一大堆参数
            //同时，不同的协议也是会在同一个注册中心注册，和之前dubbo和webservice都注册了是一个道理
        }
    }
```

`doExportUrlsFor1Protocol`的内容比较多，但是我们也不需要看，其实也主要是关于protocol的解析，组装成url

```java
private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
        String name = protocolConfig.getName();//得到<dubbo:protocol name=“xxx”>的内容。
        if (StringUtils.isEmpty(name)) {//如果没有，那么默认赋予协议protocol为dubbo
            name = DUBBO;
        }
		//又是乱七八糟的组装过程。
        Map<String, String> map = new HashMap<String, String>();
        map.put(SIDE_KEY, PROVIDER_SIDE);

        appendRuntimeParameters(map);
        appendParameters(map, metrics);
        appendParameters(map, application);
        appendParameters(map, module);
        // remove 'default.' prefix for configs from ProviderConfig
        // appendParameters(map, provider, Constants.DEFAULT_KEY);
        appendParameters(map, provider);
        appendParameters(map, protocolConfig);
        appendParameters(map, this);
        if (CollectionUtils.isNotEmpty(methods)) {
            for (MethodConfig method : methods) {
                /*
                <dubbo:service>
                	<dubbo:method></dubbo:method>
                	类似这样的解析，因为service标签可以配置在方法层面上，
                </dubbo:service>
                
                */
               //...........
            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else {
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        if (!ConfigUtils.isEmpty(token)) {
            if (ConfigUtils.isDefault(token)) {
                map.put(TOKEN_KEY, UUID.randomUUID().toString());
            } else {
                map.put(TOKEN_KEY, token);
            }
        }
        // export service
            //找到 主机 和 ip地址
        String host = this.findConfigedHosts(protocolConfig, registryURLs, map);
        Integer port = this.findConfigedPorts(protocolConfig, name, map);
        URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);

            //动态配置修改 例如 dubbo-admin 之类的动态修改
        if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .hasExtension(url.getProtocol())) {
            url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                    .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
        }

            //url已经组装好了，接下来就是服务发布了。
            /*
            SCOPE_KEY scope 选择服务发布的范围（local/remote）
            local的话，意味着同一个jvm里面调用的话，没必要走远程通过 injvm:ip:port
            remote ： dubbo:ip:port 远程调用。
            如果是配置registry的情况下，dubbo默认发布两种injvm和dubbo
            */
        String scope = url.getParameter(SCOPE_KEY);
        // don't export when none is configured
        if (!SCOPE_NONE.equalsIgnoreCase(scope)) {

            // export to local if the config is not remote (export to remote only when config is remote)
            if (!SCOPE_REMOTE.equalsIgnoreCase(scope)) {//走本地的 local
                exportLocal(url);
            }
            // export to remote if the config is not local (export to local only when config is local)
            if (!SCOPE_LOCAL.equalsIgnoreCase(scope)) {//走远程的 remote
                if (!isOnlyInJvm() && logger.isInfoEnabled()) {
                    logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                }
                if (CollectionUtils.isNotEmpty(registryURLs)) {
                    //这个时候，registryUrl还是 registry://ip:port.. 这样的东西
                    for (URL registryURL : registryURLs) {
                        //if protocol is only injvm ,not register
                        if (LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
                            continue;
                        }
                        url = url.addParameterIfAbsent(DYNAMIC_KEY, registryURL.getParameter(DYNAMIC_KEY));
                        URL monitorUrl = loadMonitor(registryURL);
                        if (monitorUrl != null) {
                            url = url.addParameterAndEncoded(MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                        }

                        // For providers, this is used to enable custom proxy to generate invoker
                        String proxy = url.getParameter(PROXY_KEY);
                        if (StringUtils.isNotEmpty(proxy)) {
                            registryURL = registryURL.addParameter(PROXY_KEY, proxy);
                        }
						
                        //这里的关键地方，Invoker的地方。请记住这里
                        
                        Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
                        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
						//wrapperInvoker 仍然是 registry://ip:port
                        Exporter<?> exporter = protocol.export(wrapperInvoker);
                        exporters.add(exporter);
                   //.... 这里就要单独领出来。
            }
        }
        this.urls.add(url);
    }
```

领出来的部分，SPI扩展点的应用。

```java
/*
	这个地方的protocol应该是什么，我们回到扩展点的实现逻辑
*/

Exporter<?> exporter = protocol.export(wrapperInvoker);
                        exporters.add(exporter);
//当我们查看这个protocol的声明地方的时候，就会发现。
private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
//没错，这个protocol是一个自适应扩展点。按道理来说如果我们没有配置其它的东西的话，按照之前只是，这应该是一个register，也就是RegisterProtocol，且export是被@Adaptive注解过的，意味着他是被代理过的。也就是我们熟悉的Protocol$Adaptive这样的代理类。
```

所以我从dubbo-config里面找一下这个的实现类是什么。被代理的类Protocol$Adaptive中其实也就是再次调用了静态扩展点，我们可以看到这里其实也就是调用了key:value的value的实现类，所以我们来到这里的

![1564676582672](https://github.com/PopCandier/DubboDemo/blob/master/img/1564676582672.png)

然后我们找到了这个的关于export的实现类。接下来在这里面实现服务的`发布和注册`。

```java
@Override
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        //现在这个值出来，就可能变成了zookeeper，因为我们配置了zookepper，可以点进去看看。
        //registryUrl -> zookeeper://ip:port
        URL registryUrl = getRegistryUrl(originInvoker);
        //而这个就应该替换成了dubbo了。
        //providerUrl -> dubbo://ip:port
        // url to export locally
        URL providerUrl = getProviderUrl(originInvoker);

        // Subscribe the override data
        // FIXME When the provider subscribes, it will affect the scene : a certain JVM exposes the service and call
        //  the same service. Because the subscribed is cached key with the name of the service, it causes the
        //  subscription information to cover.
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);
        //export invoker 本质上是启动一个netty服务
        //我们进入这个方法中
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);

        // url to registry 将 dubbo:// 将url注册到zk上。
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getRegisteredProviderUrl(providerUrl, registryUrl);
      //....
        return new DestroyableExporter<>(exporter);
    }
```

进入`doLocalExport`方法中

```java
 //这里的关键地方，Invoker的地方。请记住这里
                        
                        Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
                        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
						//wrapperInvoker 仍然是 registry://ip:port
                        Exporter<?> exporter = protocol.export(wrapperInvoker);
                        exporters.add(exporter);
```

再次提出来的Invoker之前的模块。

```java
  private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker, URL providerUrl) {
        String key = getCacheKey(originInvoker);
		// 这里bounds 是一个 chm
      /*
       这里的originInvoker 其实是 wrapperInvoker，单是这不仅仅是单纯的Invoker，他被包装过
       所以在这之前，这个Invoker应该已经变成了。
       DelegateProviderMetaDataInvoker(originInvoker)
       而到了下一步，这个包装又进行了一次升级。
      InvokerDelegate（DelegateProviderMetaDataInvoker(originInvoker)）
      */
        return (ExporterChangeableWrapper<T>) bounds.computeIfAbsent(key, s -> {
            Invoker<?> invokerDelegate = new InvokerDelegate<>(originInvoker, providerUrl);
            //这里之前说过了，应该已经替换成立DubboProtocol.export 本质上也是暴露一个20880的端口
            //这个时候，protocol还是Protocol@Adaptor
            return new ExporterChangeableWrapper<>((Exporter<T>) protocol.export(invokerDelegate), originInvoker);
        });
    }
```

所以我们回到这里。

![1564678152181](https://github.com/PopCandier/DubboDemo/blob/master/img/1564678152181.png)

**但是！**情况和我们想象的有点不一样，那就是这个`protocol`并没有初始化

```java
private final ConcurrentMap<String, ExporterChangeableWrapper<?>> bounds = new ConcurrentHashMap<>();
    private Cluster cluster;
    private Protocol protocol;// 《-- 没有初始化
    private RegistryFactory registryFactory;
    private ProxyFactory proxyFactory;
```

但是我们通过查找，发现了一个这个方法。

```java
 public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }
```

通过set的依赖注入，完成了初始化。也就是inject方法，**set方法设置依赖扩展点**，他会注入默认的扩展点，例如在`Protocol`中，他的默认扩展点是dubbo，也就意味着DubboProtocol将会被注入。

```java
@SPI("dubbo")
public interface Protocol {
```

在之前扩展点的时候，会有一个穿插在中间的jnjectXX()方法。那么问题来了，这个protocol到底是个什么呢？其实这个protocol并不是单纯的DubboProtocol，而是做了多层包装。

![https://github.com/PopCandier/DubboDemo/blob/master/img/1564679003010.png)

所以实际上，protocol应该是这个样子的。

```java
QosProtocolWrapper(ProtocolListenerWrapper(ProtocolFilterWrapper(DubboProtocol)))
```

那么意味着

```java
  private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker, URL providerUrl) {
        String key = getCacheKey(originInvoker);
        return (ExporterChangeableWrapper<T>) bounds.computeIfAbsent(key, s -> {
            Invoker<?> invokerDelegate = new InvokerDelegate<>(originInvoker, providerUrl);
            //QosProtocolWrapper(ProtocolListenerWrapper(ProtocolFilterWrapper(DubboProtocol)))
            //此时的在Protocol$Adapter的export通过静态扩展点返回的时候，中的调用方法者，已经变成了上面的三层包装了。
            return new ExporterChangeableWrapper<>((Exporter<T>) protocol.export(invokerDelegate), originInvoker);
        });
    }
```

回头来，我们发现在`org.apache.dubbo.rpc.Protocol`结尾有wrapper的扩展点。我们来到`ExtenionLoader`的类中，找到loadResource方法。

```java
private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, java.net.URL resourceURL) {
        try {
          //....
                            if (line.length() > 0) {
                                //进入这个方法。
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }
```

`loadClass方法`

```java
private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name) throws NoSuchMethodException {
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            cacheAdaptiveClass(clazz);
        } else if (isWrapperClass(clazz)) {//请注意这个方法，我们进入
            cacheWrapperClass(clazz);
        } else {
            clazz.getConstructor();
            if (StringUtils.isEmpty(name)) {
                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }

            String[] names = NAME_SEPARATOR.split(name);
            if (ArrayUtils.isNotEmpty(names)) {
                cacheActivateClass(clazz, names[0]);
                for (String n : names) {
                    cacheName(clazz, n);
                    saveInExtensionClass(extensionClasses, clazz, name);
                }
            }
        }
    }
```

会识别扩展点是否是一个wrapper，然后加入缓存中

```java
 private boolean isWrapperClass(Class<?> clazz) {
        try {//如果当前的包装类的构造函数，是有一个传进的参数class的话，那么就是
            /*
            如果这个时候 clazz 是 Protocol的话
            就意味着，他会找到响应的含 Protocol参数的构造函数的wrapper
            同时，回到之前Protocol的话题上，其实每个包装类都会有一个自己独有的含class的构造器。
            而对于此时的Protocol而言，这个参数就是Protocol.class
            */
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }
```

我们以这个作为`ProtocolFilterWrapper`，作为例子。

```java
public class ProtocolFilterWrapper implements Protocol {

    private final Protocol protocol;

    public ProtocolFilterWrapper(Protocol protocol) {//而这个时候，应该是DubboProtocol
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }
    //....
```

那么第二个问题，**在哪里用到的呢，什么时候发生的注入？**

```java
private T createExtension(String name) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            injectExtension(instance);
            //这个地方，就是使用的时候。
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (CollectionUtils.isNotEmpty(wrapperClasses)) {
                for (Class<?> wrapperClass : wrapperClasses) {
                    instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));//开始注入，并且实例化。
                }
            }
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }
```

回到包装的话题，dubbo为什么要给他们用上包装呢，很明显是为了增强，且包装的顺序，我们可以知道了。

* QosProtocolWrapper  

  * 类似一个监听服务的，会开启一个22222五个2的端口监听

* ProtocolListenerWrapper

  * 这个，没有实现

* ProtocolFilterWrapper

  * 增加过滤器功能，他有一个责任链，将所有过滤组成成功后返回给dubboProtocol

  * ![1564681092594]https://github.com/PopCandier/DubboDemo/blob/master/img/1564681092594.png)

  * 调用相对的exprot方法

  * ```java
    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        if (REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
            return protocol.export(invoker);
        }
        //进入buildInvokerChain方法
        return protocol.export(buildInvokerChain(invoker, SERVICE_FILTER_KEY, CommonConstants.PROVIDER));
    }
    ```

  * ```java
    private static <T> Invoker<T> buildInvokerChain(final Invoker<T> invoker, String key, String group) {
            Invoker<T> last = invoker;
            List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), key, group);
    
            if (!filters.isEmpty()) {
                for (int i = filters.size() - 1; i >= 0; i--) {
                    final Filter filter = filters.get(i);//得到过滤器
                    final Invoker<T> next = last;//存储自己
                    last = new Invoker<T>() {
    
                        @Override
                        public Class<T> getInterface() {
                            return invoker.getInterface();
                        }
    
                        @Override
                        public URL getUrl() {
                            return invoker.getUrl();
                        }
    
                        @Override
                        public boolean isAvailable() {
                            return invoker.isAvailable();
                        }
    
                        @Override
                        public Result invoke(Invocation invocation) throws RpcException {
                            Result asyncResult;
                            try {
                                	//再将自己传给下一个
                                asyncResult = filter.invoke(next, invocation);
                            } catch (Exception e) {
                                // onError callback
                                if (filter instanceof ListenableFilter) {
                                    Filter.Listener listener = ((ListenableFilter) filter).listener();
                                    if (listener != null) {
                                        listener.onError(e, invoker, invocation);
                                    }
                                }
                                throw e;
                            }
                            return asyncResult;
                        }
    
                        @Override
                        public void destroy() {
                            invoker.destroy();
                        }
    
                        @Override
                        public String toString() {
                            return invoker.toString();
                        }
                    };
                }
            }
    
            return new CallbackRegistrationInvoker<>(last, filters);
        }
    ```

    

* DubboProtocol

```java
QosProtocolWrapper(ProtocolListenerWrapper(ProtocolFilterWrapper(DubboProtocol)))
```

所以，最后我们还是会来到DubboProtocol的export方法。

```java
 @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        URL url = invoker.getUrl();

        // export service.
        String key = serviceKey(url);
        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
        exporterMap.put(key, exporter);

        //export an stub service for dispatching event
        Boolean isStubSupportEvent = url.getParameter(STUB_EVENT_KEY, DEFAULT_STUB_EVENT);
        Boolean isCallbackservice = url.getParameter(IS_CALLBACK_SERVICE, false);
        if (isStubSupportEvent && !isCallbackservice) {
            String stubServiceMethods = url.getParameter(STUB_EVENT_METHODS_KEY);
            if (stubServiceMethods == null || stubServiceMethods.length() == 0) {
                if (logger.isWarnEnabled()) {
                    logger.warn(new IllegalStateException("consumer [" + url.getParameter(INTERFACE_KEY) +
                            "], has set stubproxy support event ,but no stub methods founded."));
                }

            } else {
                stubServiceMethodsMap.put(url.getServiceKey(), stubServiceMethods);
            }
        }

        openServer(url);// 开启一个服务，暴露 20880 端口
        optimizeSerialization(url); // 优化序列化方式。

        return exporter;
    }
```

这之后差不多就是netty的内容了，创建处理链，也就是所谓了handler之类的，涉及的dubbo的处理对象

是Transporter还有Exchanger，还有部分扩展点(extension)的穿插。

#### 2019/7/30的总结

服务器发布做了哪些事情

* 基于Spring进行解析配置文件存储到了config中
* 在这过程中含有大量的逻辑判断，主要是为了保证配置信息的安全性
* 由于dubbo是靠url驱动，所以在这一步会组装url
  * registry:// ->zookeeper:// -> dubbo:// -> injvm
* 构建一个Invoker（动态代理）
  * 用于发起远程调用的封装，dubbo在这其中做了很多层包装
* RegistryProtocl.export
* 拥有很多的wrapper，去增强上面的类，用于filter/qos(质量检测)/listener
* DubboProtocol.export 发布服务
* 将invoker保存到集合中，并开启一个nettyserver 去监听
* 扩展点的依赖注入->injectXxx。

一切的开始，ServiceBean.onApplicationEvent(){

​	onApplicationEvent(){

​		export....

​	}

}

在这过程中 扩展点的应用非常多，

* AdaptiveExtention
* Extension


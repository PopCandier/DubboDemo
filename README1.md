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

以上其实都是dubbo解析配置文件所设置的过程

```java
@Override// 这个ContextRefreshedEvent 事件是spring上下文被刷新或者加载的时候刷新
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!isExported() && !isUnexported()) {
            if (logger.isInfoEnabled()) {
                logger.info("The service ready on spring started. service: " + getInterface());
            }
            export();//导出，发布 dubbo服务开启的入口
        }
    }
```

进入该方法。

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
//.....

private void doExportUrls() {
    //从配置中心获取注册的url
        List<URL> registryURLs = loadRegistries(true);
        for (ProtocolConfig protocolConfig : protocols) {
            String pathKey = URL.buildKey(getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), group, version);
            ProviderModel providerModel = new ProviderModel(pathKey, ref, interfaceClass);
            ApplicationModel.initProviderModel(pathKey, providerModel);
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }
```

等待补充..
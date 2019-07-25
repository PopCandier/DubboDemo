# DubboDemo

### 初步认识Dubbo

主要用于解决服务通信。服务注册和发现，负载均衡

Dubbo 的官网  http://dubbo.apache.org

### 分析Dubbo服务治理技术

动态代理，和序列化与反序列化的使用

```xml
<dependency>
      <groupId>org.apache.dubbo</groupId>
      <artifactId>dubbo</artifactId>
      <version>2.7.2</version>
 </dependency>
```

Dubbo 多协议，多注册中心的支持，Dubbo也是一个服务治理的框架。

app将会基于Dubbo发布服务，而其他应用将会基于Dubbo去调用服务



Dubbo服务启动后生成地址信息

```
dubbo://169.254.xx.20:20880/com.pop.dubbo.IPayService?anyhost=true&application=pay-service&bean.name=com.pop.dubbo.IPayService&bind.ip=169.254.69.20&bind.port=20880&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=com.pop.dubbo.IPayService&methods=pay&pid=11468&register=true&release=2.7.2&side=provider&timestamp=1563804782677, dubbo version: 2.7.2, current host: 169.254.xx.20
```

但是，我们无法通过网页直接调用，因为网页是基于http协议，所以本质来说，我们在客户端调用的时候，还是很需要使用dubbo来解析



作为服务端而言，也就是被调用api而言，dubbo的配置，他是暴露服务

```xml
<!--通过什么协议，这里有dubbo和webService等多种可以选择-->
    <dubbo:protocol name="dubbo" port="20880" />

    <!--
        服务的接口
    -->
    <dubbo:service interface="com.pop.dubbo.IPayService" ref="payService"/>

    <!--具体实现-->
    <bean id="payService" class="com.pop.dubbo.PayServiceImpl"/>
```

客户端而言，也可以是其他的服务，然后去调用服务

```xml
<dubbo:reference interface="com.pop.dubbo.IPayService" id="payService"
    url="dubbo://169.254.69.20:20880/com.pop.dubbo.IPayService"/>
```



##### 引入注册中心

dubbo中我们使用zk作为例子，我们可以看到在客户端而言，如果我们需要去调用多个服务，这个url的成本维护很大，我们不可能每次都写

`url="dubbo://169.254.69.20:20880/com.pop.dubbo.IPayService"`

这个的东西，所以，我们可以使用zk，来使用来完成注册中心，这样我们就可以不用写，那么记录那么复杂的url了。当我们的服务器启动的时候，就会自动往zk去注册临时节点。

![1563807083324](https://github.com/PopCandier/DubboDemo/blob/master/img/1563807083324.png)

```xml
<dubbo:registry address="zookeeper://192.168.255.102:2182"/>
```

同时也意味着，我们作为调用方，也就是服务端也只需要写上和服务端一样的话，这样就可以自己去zk服务器上去发现所需要的服务，然后获取ip地址，利用dubbo协议去获得结果。

##### 多个注册中心

当然，如果你还希望使用多个注册中心，dubbo也可以配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:dubbo="http://dubbo.apache.org/schema/dubbo"
       xsi:schemaLocation="http://www.springframework.org/schema/beans        http://www.springframework.org/schema/beans/spring-beans-4.3.xsd        http://dubbo.apache.org/schema/dubbo        http://dubbo.apache.org/schema/dubbo/dubbo.xsd">

    <!--
        发布的服务应用名称，最好名字唯一
    -->
    <dubbo:application name="pay-service"/>

    <!--注册中心，dubbo支持多个注册中心实现，包括zk，还有其他等
        会需要curator的包 导入两个
    -->
    <dubbo:registry address="zookeeper://192.168.255.102:2182" id="rg1"/>
    <!--倘若有多个注册中心-->
    <dubbo:registry address="zookeeper://192.168.255.102:2183" id="rg2"/>

    <!--通过什么协议，这里有dubbo和webService等多种可以选择-->
    <dubbo:protocol name="dubbo" port="20880" />

    <!--
        服务的接口
    -->
    <dubbo:service registry="rg1" interface="com.pop.dubbo.IPayService" ref="payService" />
    <dubbo:service registry="rg2" interface="com.pop.dubbo.IQueryService" ref="queryService"/>

    <!--具体实现-->
    <bean id="payService" class="com.pop.dubbo.PayServiceImpl"/>
    <bean id="queryService" class="com.pop.dubbo.IQueryService"/>
</beans>
```

dubbo也支持多个注册中心，也支持多协议。可以直接改

```xml
 <dubbo:registry address="zookeeper://192.168.255.102:2183" id="rg2"/>
 <dubbo:registry address="redis://192.168.255.102:2183" id="rg2"/>
```

* 可以支持各个注册中心
* 支持各种协议

Dubbo不止是一个服务治理的框架，个人认为更是一种生态，一个平台

每个协议都有自己的好的地方，则意味着使用dubbo将可以很好的兼容这些使用了这些协议的项目，而且Dubbo还支持同一个服务发成不同的协议。

如果你想要发布多个协议，那么你只需要导入响应依赖的包即可。

```xml
<!-- webService 依赖-->
<dependency>
  <groupId>org.apache.cxf</groupId>
  <artifactId>cxf-rt-frontend-simple</artifactId>
  <version>3.3.2</version>
</dependency>
<dependency>
  <groupId>org.apache.cxf</groupId>
  <artifactId>cxf-rt-transports-http</artifactId>
  <version>3.3.2</version>
</dependency>
<!--由于是webService是http协议，所以需要web容器解析-->
<dependency>
  <groupId>org.eclipse.jetty</groupId>
  <artifactId>jetty-server</artifactId>
  <version>9.4.18.v20190429</version>
</dependency>
<dependency>
  <groupId>org.eclipse.jetty</groupId>
  <artifactId>jetty-servlet</artifactId>
  <version>9.4.18.v20190429</version>
</dependency>
```

接着，我们只需要添加响应的协议配置集合，如果你还希望对某些暴露的接口使用什么协议，也可以指定

```xml
 <dubbo:protocol name="webservice" port="8080" server="jetty" />
  <dubbo:service registry="rg1" interface="com.pop.dubbo.IPayService" ref="payService"  protocol="webservice"/>
```

因为这个项目，只放了一个服务，所以可以看到区别

![1563809851529](https://github.com/PopCandier/DubboDemo/blob/master/img/1563809851529.png)

同时，由于是webService，所以我们在地址上输入

http://localhost:8080/com.pop.dubbo.IPayService?wsdl  可以获得以下信息。

![1563809998684](https://github.com/PopCandier/DubboDemo/blob/master/img/1563809998684.png)

最后就是，之前说过的同一个端口的多协议，其实也可以用，不过这种做法兼容比较多。

```xml
<dubbo:service registry="rg1" interface="com.pop.dubbo.IPayService" ref="payService" protocol="webservice,dubbo"/>
```

![1563810327746](https://github.com/PopCandier/DubboDemo/blob/master/img/1563810327746.png)

如果你想要知道更多的rpc协议，可以去github上找到dubbo项目的rpc包下查看具体支持

### Dubbo 服务治理的体现

springboot+dubbo

原先的dubbo项目，同样被整合到一个项目中去。

这一次，我们介绍一下常见的Dubbo的常见配置

开箱即用，准备stater包

```xml
<dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-spring-boot-starter</artifactId>
            <version>2.7.1</version>
        </dependency>
```

此外，我还需要dubbo本身的jar包，starter只是提供自动装配

```xml
 <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo</artifactId>
            <version>2.7.2</version>
        </dependency>
        <!--配置zk-->
        <dependency>
            <groupId>org.apache.curator</groupId>
            <artifactId>curator-framework</artifactId>
            <version>4.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.apache.curator</groupId>
            <artifactId>curator-recipes</artifactId>
            <version>4.0.0</version>
        </dependency>
```

在新建立的项目中，我们不需要任何的spring依赖，接着我们实现springboot模式下的服务

```java
import org.apache.dubbo.config.annotation.Service;

/**
 * @author Pop
 * @date 2019/7/25 0:05
 */
@Service //注意这里的有所不同，这是dubbo中的注解，用于标记一个服务
public class SayHelloServiceImplSpringBoot implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        return "Hello Dubbo :"+call;
    }
}
```

然后，我们还需要再配置些dubbo的参数

```properties
#dubbo.protocol.name = dubbo
#dubbo.protocol.prot = 20880
# 写@Service的路径，不写就无法自动注册到zookeeper节点。
dubbo.scan.base-packages=com.pop.springboot.dubbo.springbootdubbo
dubbo.application.name=springboot-dubbo
dubbo.registry.address=zookeeper://192.168.255.102:2182

#集群节点的写法
dubbo.registry.address=zookeeper://192.168.50.132:2181？backup=192.168.50.133:2181,192.168.50.134:2181
```

接着我们启动springboot，这个就完成了服务的发布，然后再节点上就可以看到了

![1563985964121](https://github.com/PopCandier/DubboDemo/blob/master/img/1563985964121.png)

接着我们创建客户端的spring-boot工程，dubbo-client工程

添加我们自定义的依赖还有zk的客户端jar包和dubbojar包。

```java
/**
 * @author Pop
 * @date 2019/7/25 22:20
 */
@RestController
public class DubboController {

    /**
     * 这里的reference注解是dubbo的注解
     * 用于注入服务
     *
     * 也是基于dubbo注册中心zk获取，所以还需要到propertis配置
     */
    @Reference
    ISayHelloService sayHelloService;

    @GetMapping("/sayhello")
    public String sayHello(){
        return sayHelloService.sayHello("Pop");
    }
}
```

配置

```properties
dubbo.scan.base-packages=com.pop.dubbo.dubboclient
dubbo.application.name=springboot-dubbo-client
dubbo.registry.address=zookeeper://192.168.255.102:2182
```

接下来，我们来讲解一下Dubbo中的服务治理，dubbo可以扩展性已经非常好，我们在之前已经见过了，dubbo已经将负载均衡，熔断，降级做了很好的封装，我们只要调用就好了。

我们将来讲解一下dubbo的负载均衡算法，随机算法，但是这里的随机并不是真正意义上的随机，而是权重(weight)随机

#### 负载均衡

----



```java
@Service(loadbalance = "random")//随机算法，请注意这是在服务端添加的，由dubbo做
//分发
public class SayHelloServiceImplSpringBoot implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        //为了好演示，这边打印出来
        System.out.println("请求发来了："+call);
        return "Hello Dubbo :"+call;
    }
}
```

为了测试负载均衡，我们需要多启动一些服务，来模拟请求的分发。

![1564065633756](https://github.com/PopCandier/DubboDemo/blob/master/img/1564065633756.png)

因为是启动两个服务，来模拟集群，所以我们需要拆分一下端口，避免冲突，因为一个dubbo端口是20880，现在改成20881

![1564065840445](https://github.com/PopCandier/DubboDemo/blob/master/img/1564065840445.png)

这里也可以清楚的看出，出现了两个协议，说明两个dubbo服务都注册成功。

![1564066482924](https://github.com/PopCandier/DubboDemo/blob/master/img/1564066482924.png)

![1564066504635](https://github.com/PopCandier/DubboDemo/blob/master/img/1564066504635.png)

可以将，相对来说是比较均衡了，这一种权重随机，例如A占3，B占有5，C占有2，接着dubbo会生成一定的随机值，落到这三个不同的区间内，并从本地缓存得到地址，dubbo会请求目的的地址。

```java
@Service(loadbalance = "random",weight = 6)
public class SayHelloServiceImplSpringBoot implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        //为了好演示，这边打印出来
        System.out.println("请求发来了："+call);
        return "Hello Dubbo :"+call;
    }
}
/*
意味着这个服务的权重占有60%，也有60%的概率落到这里。
*/
```

* 最小活跃
  * 如果某一台机器性能很高，那么意味着他的消化能力越高，数据堆积能力越少，他的活跃度越低，负载率越高，权重越高，也就是吞吐量。
* 权重轮询
  * 权重则 A B C
* 一致性hash
  * hash环

更多的负载均衡算法，可以在最开头的官网查看。

当然，不光是可以在服务端可以配置负载均衡，在客户端也可以配置，虽然没人会这么做，但是如果真的有这样的写法，优先使用客户端的。

```java
@RestController
public class DubboController {

    /**
     * 这里的reference注解是dubbo的注解
     * 用于注入服务
     *
     * 也是基于dubbo注册中心获取，所以还需要到propertis配置
     */
    @Reference(loadbalance = "random")
    ISayHelloService sayHelloService;

    @GetMapping("/sayhello")
    public String sayHello(){
        return sayHelloService.sayHello("Pop");
    }


}
```

都会带到注册中心的value值，并且由dubbo解析。在此之上，dubbo还支持`方法级别`的配置，这是有限度最高的，如果你配置了，会优先应用这个，初次之外，如果你的客户端和服务端都配置了相同的配置，依旧优先听客户端的，`一切以客户为主`。

#### 容错策略

----

远程通信，有很多不确定性，也就是未知性，可能成功或者失败。

也就是容忍错误的能力，当你出错后，会提供响应方案。

Dubbo提供了6钟容错概率。

我们可能有以下种需求。

* 重试
  * 再试试也许会成功
* 不希望重试
  * 快速失败
* 失败后
  * 可以记录一个日志。

Dubbo中的重试。`failover`默认的情况，三次，但是retries=2加上自己等于三次

快速失败。`failfast`

失败后，记录日志，`failback`

失败安全，出错以后，直接忽略，`failsafe`

广播出去，并行调用多个服务，有一个成功也算成功，`forking`

....

配置直接忽略错误的，容错策略。

```java
@Service(loadbalance = "random",weight = 6,cluster = "failsafe")
public class SayHelloServiceImplSpringBoot implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        //为了好演示，这边打印出来
        System.out.println("请求发来了："+call);
        return "Hello Dubbo :"+call;
    }
}
```

#### 服务降级

----

* 异常降级
  * 当你`非关键`的功能模块，出现了异常，访问错误的情况，你可以选择一个`保底`的方法返回，也可以是个静态页面，当然你也可以直接通错错误。
* 限流降级
  * 当的服务能够处理的量超过某一个阈值的时候，将会采取什么策略，例如线程池，当线程数达到一定阈值的时候，可以选择拒绝策略，也就是我们常见的`服务器繁忙，请稍候再试`
* 熔断降级
  * 例如10s之内，超过50%的请求响应时间达到了5s，这可能会触发我们设置好的熔断降级。

Dubbo中的`Mock`机制

请注意，这里的`Mock`应该是写在客户端这边，也就是请求方这边，意思就是如果你请求失败，你有个预备方案可以供自己使用。

首先我们创建一个类，这个类**必须**实现我们需要请求的借口，这样才能完成兜底的效果。

```java
/**
 * @author Pop
 * @date 2019/7/25 23:38
 */
public class SayHelloServiceMock implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        return " 服务器发生异常，返回兜底数据。";
    }
}
```

然后在Controller中的请求借口增加配置。

```java
@RestController
public class DubboController {
   //请注意，这里要求写降级策略的全路径
    @Reference(loadbalance = "random",mock ="com.pop.dubbo.dubboclient.SayHelloServiceMock")
    ISayHelloService sayHelloService;

    @GetMapping("/sayhello")
    public String sayHello() {
        return sayHelloService.sayHello("Pop");
    }
}
```

这里，为了模拟请求失败，我们添加上额外的设置。

```java
@RestController
public class DubboController {
    /**
     * 这里的reference注解是dubbo的注解
     * 用于注入服务
     *
     * 也是基于dubbo注册中心获取，所以还需要到propertis配置
     *
     * timeout 表示，这个请求要在 1 毫秒内请求完成，如果网络出现阻塞或者波动是很容易失败的
     * cluster 容错策略 如果你请求失败了，那希望你快点失败算了，因为他的默认是failover 重试，我们不希望他重试
     */
    @Reference(loadbalance = "random",timeout = 1,cluster = "failfast",mock ="com.pop.dubbo.dubboclient.SayHelloServiceMock")
    ISayHelloService sayHelloService;

    @GetMapping("/sayhello")
    public String sayHello() {
        return sayHelloService.sayHello("Pop");
    }
}
```

为了让他请求，服务器返回一定超过1毫秒，我们强制在服务器设置睡1秒

```java
@Service(loadbalance = "random",cluster = "failsafe")
public class SayHelloServiceImplSpringBoot implements ISayHelloService {
    @Override
    public String sayHello(String call) {
        //为了好演示，这边打印出来
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("请求发来了："+call);
        return "Hello Dubbo :"+call;
    }
}
```

然后我们启动服务，他告诉我们出现了问题，

![1564069989159](https://github.com/PopCandier/DubboDemo/blob/master/img/1564069989159.png)

#### 补充几点

----

有一个这种情况，就是如果我的目标服务没有启动，我发起了请求，很明显会报错，所以dubbo中，我们可以为这种情况增加配置第一个是在配置文件中配置。

```properties
dubbo.registry.check=false
```

**请注意**，这里的false并不是代表不检查，而是表示，当发现注册中心服务不可用的时候，会在等会发起**重试**。

如果你配置在接口层面。

```java
@Reference(loadbalance = "random",timeout = 1,
            cluster = "failfast",mock ="com.pop.dubbo.dubboclient.SayHelloServiceMock",
        check = false)
    ISayHelloService sayHelloService;
```

这里的false意味着，不检查，这个服务不可用是可以被允许的，后面会再次重新连接。

此外，还有一个关于主机绑定的问题。有的时候你可能会发现，你发布的dubbo地址不是你想要的，甚至是个很奇怪的域名，所以你可以自己指定。

```
dubbo.protocol.host=192.168.255.99
```

但是要注意的是，默认Dubbo会取你本地ip地址。随便设置可能会导致启动报错。请按照需求设置你的ip地址。


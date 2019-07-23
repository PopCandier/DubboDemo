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

![1563807083324](https://github.com/PopCandier/DubboDemo/tree/master/img/1563807083324.png)

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

![1563809851529](https://github.com/PopCandier/DubboDemo/tree/master/img/1563809851529.png)

同时，由于是webService，所以我们在地址上输入

http://localhost:8080/com.pop.dubbo.IPayService?wsdl  可以获得以下信息。

![1563809998684](https://github.com/PopCandier/DubboDemo/tree/master/img/1563809998684.png)

最后就是，之前说过的同一个端口的多协议，其实也可以用，不过这种做法兼容比较多。

```xml
<dubbo:service registry="rg1" interface="com.pop.dubbo.IPayService" ref="payService" protocol="webservice,dubbo"/>
```

![1563810327746](https://github.com/PopCandier/DubboDemo/tree/master/img/1563810327746.png)

如果你想要知道更多的rpc协议，可以去github上找到dubbo项目的rpc包下查看具体支持

### Dubbo 源码之内核



### 分析Dubbo 源码之服务发布和注册



### 分析Dubbo源码


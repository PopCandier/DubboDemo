package com.pop.dubbo;

import org.apache.dubbo.container.Main;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException {
        /**
         * 因为Dubbo其实是spring的一种扩展
         */
        ClassPathXmlApplicationContext context =
                new ClassPathXmlApplicationContext(
                        new String[]{"META-INF/spring/application.xml"}
                );
        context.start();

//        Main.main(args);// dubbo提供的一个启动类，其实和上面一样的

        System.in.read();
    }
}

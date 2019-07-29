package com.pop.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.compiler.Compiler;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Protocol;

import java.util.List;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {

//        System.out.println( "Hello World!" );
//        Protocol protocol=ExtensionLoader.getExtensionLoader(Protocol.class).getExtension("popProtocol");
//        System.out.println(protocol.getDefaultPort());

       // Compiler compiler = ExtensionLoader.getExtensionLoader(Compiler.class).getAdaptiveExtension();
        //System.out.println(compiler);//org.apache.dubbo.common.compiler.support.AdaptiveCompiler@50134894

        URL url = new URL("","",0);
        url=url.addParameter("cache","cache");
        List<Filter> list=ExtensionLoader.getExtensionLoader(Filter.class)
                .getActivateExtension(url,"cache");

        System.out.println(list.size());

    }

}
